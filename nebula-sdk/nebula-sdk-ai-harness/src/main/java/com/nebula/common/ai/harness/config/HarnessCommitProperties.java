package com.nebula.common.ai.harness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Harness 提交门禁配置。 */
@Data
@ConfigurationProperties(prefix = "nebula.ai.harness.commit")
public class HarnessCommitProperties {

    /** 默认只允许提交已经模拟过的当前 revision。 */
    private boolean requireSimulation = true;
}
