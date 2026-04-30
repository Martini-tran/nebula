package com.nebula.uid.autoconfigure;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.uid.UidGenerator;
import com.nebula.uid.impl.CachedUidGenerator;
import com.nebula.uid.impl.DefaultUidGenerator;
import com.nebula.uid.worker.DisposableWorkerIdAssigner;
import com.nebula.uid.worker.WorkerIdAssigner;
import com.nebula.uid.worker.mapper.WorkerNodeMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

/**
 * UID 生成器自动配置：
 * 在引入 MyBatis-Plus 与 DataSource 的环境下，根据 {@code nebula.uid.type}
 * 自动注入 {@link DefaultUidGenerator} 或 {@link CachedUidGenerator}。
 *
 * @author nebula
 */
@AutoConfiguration
@ConditionalOnClass({BaseMapper.class, DataSource.class})
@ConditionalOnProperty(prefix = "nebula.uid", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(UidGeneratorProperties.class)
@MapperScan(basePackageClasses = WorkerNodeMapper.class)
public class UidGeneratorAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public WorkerIdAssigner workerIdAssigner(WorkerNodeMapper workerNodeMapper) {
        return new DisposableWorkerIdAssigner(workerNodeMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public UidGenerator uidGenerator(WorkerIdAssigner workerIdAssigner, UidGeneratorProperties properties) {
        DefaultUidGenerator generator = (properties.getType() == UidGeneratorProperties.Type.CACHED)
                ? buildCached(properties)
                : new DefaultUidGenerator();

        generator.setWorkerIdAssigner(workerIdAssigner);
        generator.setTimeBits(properties.getTimeBits());
        generator.setWorkerBits(properties.getWorkerBits());
        generator.setSeqBits(properties.getSeqBits());
        generator.setEpochStr(properties.getEpoch());
        return generator;
    }

    private CachedUidGenerator buildCached(UidGeneratorProperties properties) {
        CachedUidGenerator cached = new CachedUidGenerator();
        cached.setBoostPower(properties.getBoostPower());
        cached.setPaddingFactor(properties.getPaddingFactor());
        if (properties.getScheduleInterval() > 0) {
            cached.setScheduleInterval(properties.getScheduleInterval());
        }
        return cached;
    }
}
