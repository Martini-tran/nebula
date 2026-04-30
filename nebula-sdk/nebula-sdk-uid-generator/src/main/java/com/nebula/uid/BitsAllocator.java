package com.nebula.uid;

import lombok.Getter;
import lombok.ToString;
import org.springframework.util.Assert;

/**
 * 64 位 UID 的位分配器：sign(1) + deltaSecond + workerId + sequence。
 *
 * <p>派生自 baidu/uid-generator (Apache License 2.0)</p>
 *
 * @author nebula
 */
@Getter
@ToString
public class BitsAllocator {

    /** 总共 64 位 */
    public static final int TOTAL_BITS = 1 << 6;

    /** 各段位数：符号位固定 1 位 */
    private final int signBits = 1;
    private final int timestampBits;
    private final int workerIdBits;
    private final int sequenceBits;

    /** 各段最大值 */
    private final long maxDeltaSeconds;
    private final long maxWorkerId;
    private final long maxSequence;

    /** 时间戳与 workerId 的左移量 */
    private final int timestampShift;
    private final int workerIdShift;

    /**
     * 按指定位数构造，三段之和必须等于 63（除去符号位）
     */
    public BitsAllocator(int timestampBits, int workerIdBits, int sequenceBits) {
        int allocateTotalBits = signBits + timestampBits + workerIdBits + sequenceBits;
        Assert.isTrue(allocateTotalBits == TOTAL_BITS, "allocate not enough 64 bits");

        this.timestampBits = timestampBits;
        this.workerIdBits = workerIdBits;
        this.sequenceBits = sequenceBits;

        this.maxDeltaSeconds = ~(-1L << timestampBits);
        this.maxWorkerId = ~(-1L << workerIdBits);
        this.maxSequence = ~(-1L << sequenceBits);

        this.timestampShift = workerIdBits + sequenceBits;
        this.workerIdShift = sequenceBits;
    }

    /**
     * 按 deltaSeconds、workerId、sequence 拼装 UID。最高位（符号位）固定为 0。
     */
    public long allocate(long deltaSeconds, long workerId, long sequence) {
        return (deltaSeconds << timestampShift) | (workerId << workerIdShift) | sequence;
    }
}
