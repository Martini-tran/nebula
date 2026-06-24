package com.nebula.common.ai.memory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 按消息条数裁剪的记忆窗口
 * 保留全部system消息（按原顺序置于最前），再保留最近{@code maxMessages}条非system历史。
 *
 * @author nebula
 */
public final class MessageCountMemoryWindow implements MemoryWindow {

    /**
     * 最大保留的非system消息条数，小于等于0时不裁剪
     */
    private final int maxMessages;

    /**
     * 构造按条数裁剪的记忆窗口
     *
     * @param maxMessages 最大保留的非system消息条数
     */
    public MessageCountMemoryWindow(int maxMessages) {
        this.maxMessages = maxMessages;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty() || maxMessages <= 0) {
            return messages;
        }

        List<Map<String, Object>> systems = new ArrayList<>();
        List<Map<String, Object>> others = new ArrayList<>();
        for (Map<String, Object> message : messages) {
            if (isSystem(message)) {
                systems.add(message);
            } else {
                others.add(message);
            }
        }

        if (others.size() <= maxMessages) {
            return messages;
        }

        List<Map<String, Object>> result = new ArrayList<>(systems);
        result.addAll(others.subList(others.size() - maxMessages, others.size()));
        return result;
    }

    private static boolean isSystem(Map<String, Object> message) {
        return message != null && "system".equals(message.get("role"));
    }
}
