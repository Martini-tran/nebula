package com.nebula.space.files;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/**
 * 文件柜配置；单个文件的大小上限沿用公共文件组件的 nebula.file.max-file-size
 */
@Data
@Component
@ConfigurationProperties(prefix = "nebula.space.files")
public class SpaceFileProperties {

    /**
     * 每人容量，最近删除里的也算
     */
    private DataSize quota = DataSize.ofGigabytes(10);
}
