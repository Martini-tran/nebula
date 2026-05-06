package com.nebula.uid.buffer;

import com.nebula.uid.utils.PaddedAtomicLong;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.Assert;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于数组的环形缓冲（Ring Buffer），通过数组连续内存提升 CPU 缓存命中率。
 * 对 tail/cursor 使用 {@link PaddedAtomicLong} 避免伪共享。结构：
 *   slots：每个槽位存放一个 UID
 *   flags：与 slots 同索引，标记槽位可写或可读
 *   tail：生产者最大序号
 *   cursor：消费者最小序号
 *
 * @author nebula
 */
@Slf4j
public class RingBuffer {

    /**
     * 环形起始点
     */
    private static final int START_POINT = -1;
    /**
     * 可写标记
     */
    private static final long CAN_PUT_FLAG = 0L;
    /**
     * 可读标记
     */
    private static final long CAN_TAKE_FLAG = 1L;
    /**
     * 默认填充触发百分比
     */
    public static final int DEFAULT_PADDING_PERCENT = 50;

    /**
     * 槽位数量，必须是 2 的幂
     */
    @Getter
    private final int bufferSize;
    private final long indexMask;
    private final long[] slots;
    private final PaddedAtomicLong[] flags;

    /**
     * 生产者尾部序号
     */
    private final AtomicLong tail = new PaddedAtomicLong(START_POINT);

    /**
     * 消费者游标
     */
    private final AtomicLong cursor = new PaddedAtomicLong(START_POINT);

    /**
     * 触发填充的剩余槽位阈值
     */
    private final int paddingThreshold;

    /**
     * 写入/读取拒绝策略
     */
    @Setter
    private RejectedPutBufferHandler rejectedPutHandler = this::discardPutBuffer;
    @Setter
    private RejectedTakeBufferHandler rejectedTakeHandler = this::exceptionRejectedTakeBuffer;

    /**
     * 异步填充执行器
     */
    @Setter
    private BufferPaddingExecutor bufferPaddingExecutor;

    public RingBuffer(int bufferSize) {
        this(bufferSize, DEFAULT_PADDING_PERCENT);
    }

    /**
     * @param bufferSize    必须为正且为 2 的幂
     * @param paddingFactor 取值 (0,100)，剩余可消费 UID 不足 bufferSize*paddingFactor/100 时触发填充
     */
    public RingBuffer(int bufferSize, int paddingFactor) {
        Assert.isTrue(bufferSize > 0L, "RingBuffer size must be positive");
        Assert.isTrue(Integer.bitCount(bufferSize) == 1, "RingBuffer size must be a power of 2");
        Assert.isTrue(paddingFactor > 0 && paddingFactor < 100, "RingBuffer size must be positive");

        this.bufferSize = bufferSize;
        this.indexMask = bufferSize - 1;
        this.slots = new long[bufferSize];
        this.flags = initFlags(bufferSize);

        this.paddingThreshold = bufferSize * paddingFactor / 100;
    }

    /**
     * 写入一个 UID，并推进 tail。<br>
     * 使用 synchronized 保证写入槽位与发布 tail 的原子性。
     *
     * @return false 表示缓冲已满，已交由 {@link RejectedPutBufferHandler} 处理
     */
    public synchronized boolean put(long uid) {
        long currentTail = tail.get();
        long currentCursor = cursor.get();

        long distance = currentTail - (currentCursor == START_POINT ? 0 : currentCursor);
        if (distance == bufferSize - 1) {
            rejectedPutHandler.rejectPutBuffer(this, uid);
            return false;
        }

        int nextTailIndex = calSlotIndex(currentTail + 1);
        if (flags[nextTailIndex].get() != CAN_PUT_FLAG) {
            rejectedPutHandler.rejectPutBuffer(this, uid);
            return false;
        }

        slots[nextTailIndex] = uid;
        flags[nextTailIndex].set(CAN_TAKE_FLAG);
        tail.incrementAndGet();

        return true;
    }

    /**
     * 取出一个 UID。基于 CAS 的无锁读取。<br>
     * 取数前若剩余可用 UID 低于阈值，将异步触发填充。
     *
     * @return UID
     */
    public long take() {
        long currentCursor = cursor.get();
        long nextCursor = cursor.updateAndGet(old -> old == tail.get() ? old : old + 1);

        Assert.isTrue(nextCursor >= currentCursor, "Cursor can't move back");

        long currentTail = tail.get();
        if (currentTail - nextCursor < paddingThreshold) {
            log.info("Reach the padding threshold:{}. tail:{}, cursor:{}, rest:{}",
                    paddingThreshold, currentTail, nextCursor, currentTail - nextCursor);
            bufferPaddingExecutor.asyncPadding();
        }

        if (nextCursor == currentCursor) {
            rejectedTakeHandler.rejectTakeBuffer(this);
        }

        int nextCursorIndex = calSlotIndex(nextCursor);
        Assert.isTrue(flags[nextCursorIndex].get() == CAN_TAKE_FLAG, "Cursor not in can take status");

        long uid = slots[nextCursorIndex];
        flags[nextCursorIndex].set(CAN_PUT_FLAG);

        return uid;
    }

    /**
     * 计算槽位索引：sequence % bufferSize
     */
    protected int calSlotIndex(long sequence) {
        return (int) (sequence & indexMask);
    }

    /**
     * 默认 put 拒绝策略：仅打印日志
     */
    protected void discardPutBuffer(RingBuffer ringBuffer, long uid) {
        log.warn("Rejected putting buffer for uid:{}. {}", uid, ringBuffer);
    }

    /**
     * 默认 take 拒绝策略：抛出运行时异常
     */
    protected void exceptionRejectedTakeBuffer(RingBuffer ringBuffer) {
        log.warn("Rejected take buffer. {}", ringBuffer);
        throw new RuntimeException("Rejected take buffer. " + ringBuffer);
    }

    /**
     * 初始化 flags 为可写状态
     */
    private PaddedAtomicLong[] initFlags(int bufferSize) {
        PaddedAtomicLong[] flags = new PaddedAtomicLong[bufferSize];
        for (int i = 0; i < bufferSize; i++) {
            flags[i] = new PaddedAtomicLong(CAN_PUT_FLAG);
        }
        return flags;
    }

    public long getTail() {
        return tail.get();
    }

    public long getCursor() {
        return cursor.get();
    }

    @Override
    public String toString() {
        return "RingBuffer [bufferSize=" + bufferSize
                + ", tail=" + tail
                + ", cursor=" + cursor
                + ", paddingThreshold=" + paddingThreshold + "]";
    }
}
