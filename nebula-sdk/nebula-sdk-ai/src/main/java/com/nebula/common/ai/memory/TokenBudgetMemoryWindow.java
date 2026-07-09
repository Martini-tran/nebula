package com.nebula.common.ai.memory;

import com.nebula.common.ai.util.TokenEstimator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 按token预算裁剪的记忆窗口
 * 保留全部system消息（其token先计入预算），再从最新到最旧累加非system历史，
 * 直到累计估算token超过{@code maxTokens}为止；保留下来的部分恢复为时间正序。
 * 这是用于避免超出模型上下文上限的核心实现。
 *
 * @author nebula
 */
public final class TokenBudgetMemoryWindow implements MemoryWindow {

    /**
     * token预算上限，小于等于0时不裁剪
     */
    private final int maxTokens;

    /**
     * 构造按token预算裁剪的记忆窗口
     *
     * @param maxTokens token预算上限
     */
    public TokenBudgetMemoryWindow(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    @Override
    public List<Map<String, Object>> apply(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty() || maxTokens <= 0) {
            return messages;
        }
        if (TokenEstimator.estimateMessages(messages) <= maxTokens) {
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

        int used = TokenEstimator.estimateMessages(systems);
        List<Map<String, Object>> keptReversed = new ArrayList<>();
        // 从最新到最旧累加，超过预算即停止
        for (int i = others.size() - 1; i >= 0; i--) {
            Map<String, Object> message = others.get(i);
            int cost = TokenEstimator.estimateMessage(message);
            if (used + cost > maxTokens) {
                break;
            }
            used += cost;
            keptReversed.add(message);
        }
        Collections.reverse(keptReversed);

        List<Map<String, Object>> result = new ArrayList<>(systems);
        result.addAll(keptReversed);
        return result;
    }

    private static boolean isSystem(Map<String, Object> message) {
        return message != null && "system".equals(message.get("role"));
    }
}
