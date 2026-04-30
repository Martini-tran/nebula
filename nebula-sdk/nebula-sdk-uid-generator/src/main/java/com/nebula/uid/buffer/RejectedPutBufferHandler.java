package com.nebula.uid.buffer;

/**
 * RingBuffer 写入被拒绝时的处理策略（Lambda 友好）。
 * 当 tail 追上 cursor，意味着环形缓冲已满，再次 put 会触发该策略。
 * @author nebula
 */
@FunctionalInterface
public interface RejectedPutBufferHandler {

    /**
     * 处理被拒绝的 put 请求
     *
     * @param ringBuffer 触发拒绝的 RingBuffer
     * @param uid        被拒绝的 UID
     */
    void rejectPutBuffer(RingBuffer ringBuffer, long uid);
}
