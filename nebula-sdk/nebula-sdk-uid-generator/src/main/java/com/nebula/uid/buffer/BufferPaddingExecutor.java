package com.nebula.uid.buffer;

import com.nebula.uid.utils.NamingThreadFactory;
import com.nebula.uid.utils.PaddedAtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * RingBuffer 填充执行器，提供两种填充方式：定时填充与即时（异步）填充。
 *
 * @author nebula
 */
@Slf4j
public class BufferPaddingExecutor {

    private static final String WORKER_NAME = "RingBuffer-Padding-Worker";
    private static final String SCHEDULE_NAME = "RingBuffer-Padding-Schedule";
    /** 默认调度间隔：5 分钟 */
    private static final long DEFAULT_SCHEDULE_INTERVAL = 5 * 60L;

    /** 是否正在填充 */
    private final AtomicBoolean running;

    /** 已消费到的最后一秒（可向未来借秒） */
    private final PaddedAtomicLong lastSecond;

    private final RingBuffer ringBuffer;
    private final BufferedUidProvider uidProvider;

    /** 异步填充线程池 */
    private final ExecutorService bufferPadExecutors;
    /** 定时填充调度线程 */
    private final ScheduledExecutorService bufferPadSchedule;

    /** 调度间隔（秒） */
    private long scheduleInterval = DEFAULT_SCHEDULE_INTERVAL;

    public BufferPaddingExecutor(RingBuffer ringBuffer, BufferedUidProvider uidProvider) {
        this(ringBuffer, uidProvider, true);
    }

    public BufferPaddingExecutor(RingBuffer ringBuffer, BufferedUidProvider uidProvider, boolean usingSchedule) {
        this.running = new AtomicBoolean(false);
        this.lastSecond = new PaddedAtomicLong(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()));
        this.ringBuffer = ringBuffer;
        this.uidProvider = uidProvider;

        int cores = Runtime.getRuntime().availableProcessors();
        bufferPadExecutors = Executors.newFixedThreadPool(cores * 2, new NamingThreadFactory(WORKER_NAME));

        if (usingSchedule) {
            bufferPadSchedule = Executors.newSingleThreadScheduledExecutor(new NamingThreadFactory(SCHEDULE_NAME));
        } else {
            bufferPadSchedule = null;
        }
    }

    /**
     * 启动调度
     */
    public void start() {
        if (bufferPadSchedule != null) {
            bufferPadSchedule.scheduleWithFixedDelay(this::paddingBuffer, scheduleInterval, scheduleInterval, TimeUnit.SECONDS);
        }
    }

    /**
     * 关闭线程池
     */
    public void shutdown() {
        if (!bufferPadExecutors.isShutdown()) {
            bufferPadExecutors.shutdownNow();
        }
        if (bufferPadSchedule != null && !bufferPadSchedule.isShutdown()) {
            bufferPadSchedule.shutdownNow();
        }
    }

    /** 是否正在填充 */
    public boolean isRunning() {
        return running.get();
    }

    /** 异步填充（提交到线程池） */
    public void asyncPadding() {
        bufferPadExecutors.submit(this::paddingBuffer);
    }

    /**
     * 同步填充：循环填满直至 tail 追上 cursor
     */
    public void paddingBuffer() {
        log.info("Ready to padding buffer lastSecond:{}. {}", lastSecond.get(), ringBuffer);

        if (!running.compareAndSet(false, true)) {
            log.info("Padding buffer is still running. {}", ringBuffer);
            return;
        }

        boolean isFullRingBuffer = false;
        while (!isFullRingBuffer) {
            List<Long> uidList = uidProvider.provide(lastSecond.incrementAndGet());
            for (Long uid : uidList) {
                isFullRingBuffer = !ringBuffer.put(uid);
                if (isFullRingBuffer) {
                    break;
                }
            }
        }

        running.compareAndSet(true, false);
        log.info("End to padding buffer lastSecond:{}. {}", lastSecond.get(), ringBuffer);
    }

    public void setScheduleInterval(long scheduleInterval) {
        Assert.isTrue(scheduleInterval > 0, "Schedule interval must positive!");
        this.scheduleInterval = scheduleInterval;
    }
}
