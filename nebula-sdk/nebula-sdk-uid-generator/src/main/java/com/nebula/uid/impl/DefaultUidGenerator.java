package com.nebula.uid.impl;

import com.nebula.uid.BitsAllocator;
import com.nebula.uid.UidGenerator;
import com.nebula.uid.exception.UidGenerateException;
import com.nebula.uid.utils.DateUtils;
import com.nebula.uid.worker.WorkerIdAssigner;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;

import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * {@link UidGenerator} 默认实现。
 * UID 共 64 位，默认位分配如下：
 *  sign：最高位固定为 0（1 bit）
 *  delta seconds：相对自定义 epoch 的秒数（28 bit，约 8.7 年）
 *  worker id：基于数据库分配的 workerId（22 bit，约 420W）
 *  sequence：同一秒内序列号（13 bit，单秒最多 8192 个）
 * 
 *
 * <pre>{@code
 * +------+----------------------+----------------+-----------+
 * | sign |     delta seconds    | worker node id | sequence  |
 * +------+----------------------+----------------+-----------+
 *   1bit          28bits              22bits         13bits
 * }</pre>
 *
 * 位数与 epoch 可通过属性自定义，三段位数之和需为 63。
 *
 * 
 *
 * @author nebula
 */
@Slf4j
@Setter
public class DefaultUidGenerator implements UidGenerator, InitializingBean {

    /**
     * 位分配
     */
    protected int timeBits = 28;
    protected int workerBits = 22;
    protected int seqBits = 13;

    /**
     * 自定义 epoch（秒），默认 2016-05-20（毫秒：1463673600000）
     */
    protected String epochStr = "2016-05-20";
    protected long epochSeconds = TimeUnit.MILLISECONDS.toSeconds(1463673600000L);

    /**
     * Spring 初始化后稳定的字段
     */
    protected BitsAllocator bitsAllocator;
    protected long workerId;

    /**
     * 由 nextId() 写入的可变字段
     */
    protected long sequence = 0L;
    protected long lastSecond = -1L;

    /**
     * WorkerId 分配器
     */
    protected WorkerIdAssigner workerIdAssigner;

    @Override
    public void afterPropertiesSet() throws Exception {
        bitsAllocator = new BitsAllocator(timeBits, workerBits, seqBits);

        workerId = workerIdAssigner.assignWorkerId();
        if (workerId > bitsAllocator.getMaxWorkerId()) {
            throw new RuntimeException("Worker id " + workerId + " exceeds the max " + bitsAllocator.getMaxWorkerId());
        }

        log.info("Initialized bits(1, {}, {}, {}) for workerID:{}", timeBits, workerBits, seqBits, workerId);
    }

    @Override
    public long getUID() throws UidGenerateException {
        try {
            return nextId();
        } catch (Exception e) {
            log.error("Generate unique id exception. ", e);
            throw new UidGenerateException(e);
        }
    }

    @Override
    public String parseUID(long uid) {
        long totalBits = BitsAllocator.TOTAL_BITS;
        long signBits = bitsAllocator.getSignBits();
        long timestampBits = bitsAllocator.getTimestampBits();
        long workerIdBits = bitsAllocator.getWorkerIdBits();
        long sequenceBits = bitsAllocator.getSequenceBits();

        long sequence = (uid << (totalBits - sequenceBits)) >>> (totalBits - sequenceBits);
        long workerId = (uid << (timestampBits + signBits)) >>> (totalBits - workerIdBits);
        long deltaSeconds = uid >>> (workerIdBits + sequenceBits);

        Date thatTime = new Date(TimeUnit.SECONDS.toMillis(epochSeconds + deltaSeconds));
        String thatTimeStr = DateUtils.formatByDateTimePattern(thatTime);

        return String.format("{\"UID\":\"%d\",\"timestamp\":\"%s\",\"workerId\":\"%d\",\"sequence\":\"%d\"}",
                uid, thatTimeStr, workerId, sequence);
    }

    /**
     * 生成下一个 UID
     */
    protected synchronized long nextId() {
        long currentSecond = getCurrentSecond();

        if (currentSecond < lastSecond) {
            long refusedSeconds = lastSecond - currentSecond;
            throw new UidGenerateException("Clock moved backwards. Refusing for %d seconds", refusedSeconds);
        }

        if (currentSecond == lastSecond) {
            sequence = (sequence + 1) & bitsAllocator.getMaxSequence();
            if (sequence == 0) {
                currentSecond = getNextSecond(lastSecond);
            }
        } else {
            sequence = 0L;
        }

        lastSecond = currentSecond;
        return bitsAllocator.allocate(currentSecond - epochSeconds, workerId, sequence);
    }

    private long getNextSecond(long lastTimestamp) {
        long timestamp = getCurrentSecond();
        while (timestamp <= lastTimestamp) {
            timestamp = getCurrentSecond();
        }
        return timestamp;
    }

    private long getCurrentSecond() {
        long currentSecond = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        if (currentSecond - epochSeconds > bitsAllocator.getMaxDeltaSeconds()) {
            throw new UidGenerateException("Timestamp bits is exhausted. Refusing UID generate. Now: " + currentSecond);
        }
        return currentSecond;
    }

    public void setTimeBits(int timeBits) {
        if (timeBits > 0) {
            this.timeBits = timeBits;
        }
    }

    public void setWorkerBits(int workerBits) {
        if (workerBits > 0) {
            this.workerBits = workerBits;
        }
    }

    public void setSeqBits(int seqBits) {
        if (seqBits > 0) {
            this.seqBits = seqBits;
        }
    }

    public void setEpochStr(String epochStr) {
        if (epochStr != null && !epochStr.isBlank()) {
            this.epochStr = epochStr;
            this.epochSeconds = TimeUnit.MILLISECONDS.toSeconds(DateUtils.parseByDayPattern(epochStr).getTime());
        }
    }
}
