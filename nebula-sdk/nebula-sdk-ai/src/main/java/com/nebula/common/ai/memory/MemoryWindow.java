package com.nebula.common.ai.memory;

import java.util.List;
import java.util.Map;

/**
 * 会话记忆窗口
 * 在消息历史发往模型之前做窗口裁剪，以避免超出模型上下文上限。
 *
 * @author nebula
 */
public interface MemoryWindow {

    /**
     * 对消息历史做窗口裁剪
     *
     * @param messages 原始消息历史
     * @return 裁剪后的新列表；入参为null/空时原样返回
     */
    List<Map<String, Object>> apply(List<Map<String, Object>> messages);
}
