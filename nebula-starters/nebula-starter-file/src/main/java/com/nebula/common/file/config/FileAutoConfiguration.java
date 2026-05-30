package com.nebula.common.file.config;

import com.nebula.common.file.mapper.SysFileMapper;
import com.nebula.common.file.properties.FileProperties;
import com.nebula.common.file.service.SysFileService;
import com.nebula.common.file.service.impl.SysFileServiceImpl;
import com.nebula.common.oss.api.ObjectStorageFactory;
import com.nebula.common.oss.config.MinioAutoConfiguration;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * 文件管理自动配置
 * <p>
 * 仅在容器存在 {@link ObjectStorageFactory}（即引入了 nebula-sdk-oss 并启用具体存储实现）
 * 且未关闭 nebula.file.enabled 时生效。
 *
 * @author nebula
 */
@AutoConfiguration(after = MinioAutoConfiguration.class)
@EnableConfigurationProperties(FileProperties.class)
@ConditionalOnProperty(prefix = "nebula.file", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(ObjectStorageFactory.class)
@MapperScan("com.nebula.common.file.mapper")
public class FileAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public SysFileService sysFileService(SysFileMapper sysFileMapper,
                                         ObjectStorageFactory storageFactory,
                                         FileProperties fileProperties) {
        return new SysFileServiceImpl(sysFileMapper, storageFactory, fileProperties);
    }
}
