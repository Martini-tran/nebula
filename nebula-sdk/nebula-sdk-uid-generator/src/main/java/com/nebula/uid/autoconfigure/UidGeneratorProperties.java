package com.nebula.uid.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * UID 生成器配置属性
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.uid")
public class UidGeneratorProperties {

    /** 是否启用 UID 生成器 */
    private boolean enabled = true;

    /** 生成器类型：default 同步生成；cached 基于 RingBuffer 缓存生成 */
    private Type type = Type.CACHED;

    /** 时间位数（秒），默认 28 位约 8.7 年 */
    private int timeBits = 28;

    /** WorkerId 位数，默认 22 位约 420W */
    private int workerBits = 22;

    /** 同一秒内序列号位数，默认 13 位即每秒 8192 个 */
    private int seqBits = 13;

    /** epoch 起点日期（yyyy-MM-dd），默认 2016-05-20 */
    private String epoch = "2016-05-20";

    /** RingBuffer 倍率（cached 类型），bufferSize = (maxSequence+1) << boostPower */
    private int boostPower = 3;

    /** 填充触发阈值百分比（cached 类型），取值 (0,100) */
    private int paddingFactor = 50;

    /** 定时填充间隔（秒），cached 类型生效；&lt;=0 表示不启用定时填充 */
    private long scheduleInterval = 0L;

    public enum Type {
        /** 实时生成 */
        DEFAULT,
        /** 基于 RingBuffer 的缓存生成 */
        CACHED
    }
}
