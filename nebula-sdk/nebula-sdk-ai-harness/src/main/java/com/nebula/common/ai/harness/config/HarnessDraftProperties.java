package com.nebula.common.ai.harness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Harness 草稿大小与读取预算。
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.ai.harness.draft")
public class HarnessDraftProperties {

    private int maxNodes = 100;

    private int maxEdges = 200;

    private int maxTemplateBytes = 16 * 1024;

    private int maxNodeConfigBytes = 32 * 1024;

    private int maxReadItems = 200;

    private int maxReadPageSize = 50;

    private int maxToolResultBytes = 24 * 1024;
}
