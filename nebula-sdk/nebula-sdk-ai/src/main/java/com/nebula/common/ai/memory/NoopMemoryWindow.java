package com.nebula.common.ai.memory;

import java.util.List;
import java.util.Map;

/**
 * 不裁剪的记忆窗口
 * 直接返回原始消息历史，作为默认实现保持原有行为不变。
 *
 * @author nebula
 */
public final class NoopMemoryWindow implements MemoryWindow {

    /**
     * 全局单例
     */
    public static final NoopMemoryWindow INSTANCE = new NoopMemoryWindow();

    private NoopMemoryWindow() {
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> messages) {
        return messages;
    }
}
