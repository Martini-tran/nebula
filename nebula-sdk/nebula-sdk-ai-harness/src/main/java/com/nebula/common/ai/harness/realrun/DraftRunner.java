package com.nebula.common.ai.harness.realrun;

/** 宿主提供的草稿隔离执行 SPI。 */
public interface DraftRunner {

    DraftRunResult run(DraftRunRequest request);
}
