package com.nebula.uid.buffer;

/**
 * RingBuffer 读取被拒绝时的处理策略（Lambda 友好）。
 *
 * <p>当 cursor 追上 tail，意味着环形缓冲已空，再次 take 会触发该策略。</p>
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
@FunctionalInterface
public interface RejectedTakeBufferHandler {

    /**
     * 处理被拒绝的 take 请求
     *
     * @param ringBuffer 触发拒绝的 RingBuffer
     */
    void rejectTakeBuffer(RingBuffer ringBuffer);
}
