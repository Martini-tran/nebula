package com.nebula.common.ai.harness.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 草稿模拟的输入与路径预算，所有限制都在执行任何模拟节点前生效。
 *
 * @author nebula
 */
@Data
@ConfigurationProperties(prefix = "nebula.ai.harness.simulation")
public class HarnessSimulationProperties {

    private int maxInitialInputBytes = 16 * 1024;

    private int maxInitialInputEntries = 100;

    private int maxInitialInputDepth = 8;

    private int maxDagPaths = 16;

    private int maxBranchCandidates = 4;

    private int maxStateTransitions = 50;

    private int loopIterations = 2;
}
