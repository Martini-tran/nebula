package com.nebula.common.ai.harness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** 草稿真实试跑的确认、等待、心跳和结果预算。 */
@Data
@ConfigurationProperties(prefix = "nebula.ai.harness.real-run")
public class HarnessRealRunProperties {

    private Duration confirmationTtl = Duration.ofMinutes(5);
    private Duration waitTimeout = Duration.ofSeconds(3);
    private Duration heartbeatInterval = Duration.ofSeconds(10);
    private Duration staleAfter = Duration.ofMinutes(2);
    private Duration confirmationRetention = Duration.ofDays(1);
    private Duration operationResultRetention = Duration.ofDays(7);
    private int maxConcurrentOperations = 4;
    private int maxResultBytes = 32 * 1024;
    private int maxResultEntries = 100;
    private int maxResultDepth = 8;
    private int maxResultStringLength = 2000;
}
