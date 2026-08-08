package com.nebula.manager.ai.copilot.tool;

/**
 * Manager 兼容 Copilot 工具的共享元数据。
 *
 * <p>不挂载 Spring Bean，也不定义工具编码，避免与 Harness 权威工具产生重复注册。
 *
 * @author nebula
 */
final class CopilotToolSupport {

    static final String CATEGORY = "copilot";

    private CopilotToolSupport() {
    }
}
