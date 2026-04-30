package com.nebula.uid.impl;

import com.nebula.uid.BitsAllocator;
import com.nebula.uid.UidGenerator;
import com.nebula.uid.buffer.BufferPaddingExecutor;
import com.nebula.uid.buffer.RejectedPutBufferHandler;
import com.nebula.uid.buffer.RejectedTakeBufferHandler;
import com.nebula.uid.buffer.RingBuffer;
import com.nebula.uid.exception.UidGenerateException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.util.Assert;

import java.util.ArrayList;
import java.util.List;

/**
 * 基于无锁 {@link RingBuffer} 的缓存式 {@link UidGenerator} 实现，继承 {@link DefaultUidGenerator}。
 *
 * <p>可配置项：</p>
 * <ul>
 *   <li><b>boostPower</b>：RingBuffer 大小放大倍数（按 2 的幂），bufferSize = (maxSequence+1) &lt;&lt; boostPower</li>
 *   <li><b>paddingFactor</b>：剩余可消费 UID 占比阈值（0~100），低于阈值触发填充</li>
 *   <li><b>scheduleInterval</b>：定时填充间隔（秒），不设则不启用定时填充</li>
 *   <li><b>rejectedPutBufferHandler</b> / <b>rejectedTakeBufferHandler</b>：写/读拒绝策略</li>
 * </ul>
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
@Slf4j
public class CachedUidGenerator extends DefaultUidGenerator implements DisposableBean {

    private static final int DEFAULT_BOOST_POWER = 3;

    private int boostPower = DEFAULT_BOOST_POWER;
    private int paddingFactor = RingBuffer.DEFAULT_PADDING_PERCENT;
    private Long scheduleInterval;

    private RejectedPutBufferHandler rejectedPutBufferHandler;
    private RejectedTakeBufferHandler rejectedTakeBufferHandler;

    private RingBuffer ringBuffer;
    private BufferPaddingExecutor bufferPaddingExecutor;

    @Override
    public void afterPropertiesSet() throws Exception {
        super.afterPropertiesSet();
        this.initRingBuffer();
        log.info("Initialized RingBuffer successfully.");
    }

    @Override
    public long getUID() {
        try {
            return ringBuffer.take();
        } catch (Exception e) {
            log.error("Generate unique id exception. ", e);
            throw new UidGenerateException(e);
        }
    }

    @Override
    public String parseUID(long uid) {
        return super.parseUID(uid);
    }

    @Override
    public void destroy() throws Exception {
        bufferPaddingExecutor.shutdown();
    }

    /**
     * 在指定秒内连续生成 maxSequence+1 个 UID
     */
    protected List<Long> nextIdsForOneSecond(long currentSecond) {
        int listSize = (int) bitsAllocator.getMaxSequence() + 1;
        List<Long> uidList = new ArrayList<>(listSize);

        long firstSeqUid = bitsAllocator.allocate(currentSecond - epochSeconds, workerId, 0L);
        for (int offset = 0; offset < listSize; offset++) {
            uidList.add(firstSeqUid + offset);
        }
        return uidList;
    }

    private void initRingBuffer() {
        int bufferSize = ((int) bitsAllocator.getMaxSequence() + 1) << boostPower;
        this.ringBuffer = new RingBuffer(bufferSize, paddingFactor);
        log.info("Initialized ring buffer size:{}, paddingFactor:{}", bufferSize, paddingFactor);

        boolean usingSchedule = (scheduleInterval != null);
        this.bufferPaddingExecutor = new BufferPaddingExecutor(ringBuffer, this::nextIdsForOneSecond, usingSchedule);
        if (usingSchedule) {
            bufferPaddingExecutor.setScheduleInterval(scheduleInterval);
        }

        log.info("Initialized BufferPaddingExecutor. Using schedule:{}, interval:{}", usingSchedule, scheduleInterval);

        this.ringBuffer.setBufferPaddingExecutor(bufferPaddingExecutor);
        if (rejectedPutBufferHandler != null) {
            this.ringBuffer.setRejectedPutHandler(rejectedPutBufferHandler);
        }
        if (rejectedTakeBufferHandler != null) {
            this.ringBuffer.setRejectedTakeHandler(rejectedTakeBufferHandler);
        }

        bufferPaddingExecutor.paddingBuffer();
        bufferPaddingExecutor.start();
    }

    public void setBoostPower(int boostPower) {
        Assert.isTrue(boostPower > 0, "Boost power must be positive!");
        this.boostPower = boostPower;
    }

    public void setPaddingFactor(int paddingFactor) {
        Assert.isTrue(paddingFactor > 0 && paddingFactor < 100, "Padding factor must be in (0, 100)!");
        this.paddingFactor = paddingFactor;
    }

    public void setRejectedPutBufferHandler(RejectedPutBufferHandler rejectedPutBufferHandler) {
        Assert.notNull(rejectedPutBufferHandler, "RejectedPutBufferHandler can't be null!");
        this.rejectedPutBufferHandler = rejectedPutBufferHandler;
    }

    public void setRejectedTakeBufferHandler(RejectedTakeBufferHandler rejectedTakeBufferHandler) {
        Assert.notNull(rejectedTakeBufferHandler, "RejectedTakeBufferHandler can't be null!");
        this.rejectedTakeBufferHandler = rejectedTakeBufferHandler;
    }

    public void setScheduleInterval(long scheduleInterval) {
        Assert.isTrue(scheduleInterval > 0, "Schedule interval must positive!");
        this.scheduleInterval = scheduleInterval;
    }
}
