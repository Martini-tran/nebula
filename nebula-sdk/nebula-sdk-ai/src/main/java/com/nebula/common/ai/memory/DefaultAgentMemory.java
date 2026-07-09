package com.nebula.common.ai.memory;

import com.nebula.common.ai.api.AiConversationRepository;
import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Agent记忆门面默认实现
 * 短期记忆委托给{@link AiConversationRepository}并应用该Agent的窗口策略；
 * 长期记忆委托给{@link LongTermMemory}，写入/检索时自动注入Agent功能编码，并受配置开关与类型过滤约束。
 *
 * @author nebula
 */
public class DefaultAgentMemory implements AgentMemory {

    private final AgentMemoryConfig config;

    private final AiConversationRepository conversationRepository;

    private final LongTermMemory longTermMemory;

    /**
     * 构造Agent记忆门面
     *
     * @param config                  记忆配置（agentCode必填）
     * @param conversationRepository  短期会话历史仓储，可为null表示不支持短期记忆
     * @param longTermMemory          长期记忆，可为null表示不支持长期记忆
     */
    public DefaultAgentMemory(AgentMemoryConfig config,
                              AiConversationRepository conversationRepository,
                              LongTermMemory longTermMemory) {
        if (config == null || config.getAgentCode() == null || config.getAgentCode().isEmpty()) {
            throw new IllegalArgumentException("AgentMemoryConfig与agentCode不能为空");
        }
        this.config = config;
        this.conversationRepository = conversationRepository;
        this.longTermMemory = longTermMemory;
    }

    @Override
    public String agentCode() {
        return config.getAgentCode();
    }

    @Override
    public AgentMemoryConfig config() {
        return config;
    }

    @Override
    public void saveMessage(String conversationId, Map<String, Object> message) {
        if (conversationRepository == null) {
            return;
        }
        conversationRepository.saveMessage(conversationId, message);
    }

    @Override
    public List<Map<String, Object>> loadHistory(String conversationId) {
        if (conversationRepository == null) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> messages = conversationRepository.listMessages(conversationId);
        return config.getWindow().apply(messages);
    }

    @Override
    public void clearConversation(String conversationId) {
        if (conversationRepository == null) {
            return;
        }
        conversationRepository.clear(conversationId);
    }

    @Override
    public String remember(MemoryRecord record) {
        if (!longTermAvailable() || record == null) {
            return null;
        }
        if (!config.isTypeEnabled(record.getType())) {
            return null;
        }
        record.setAgentCode(config.getAgentCode());
        return longTermMemory.save(record);
    }

    @Override
    public List<MemoryRecord> recall(MemoryQuery query) {
        if (!longTermAvailable() || query == null) {
            return Collections.emptyList();
        }
        query.setAgentCode(config.getAgentCode());
        return longTermMemory.search(query);
    }

    @Override
    public void forget(String userId, String id) {
        if (!longTermAvailable()) {
            return;
        }
        longTermMemory.delete(config.getAgentCode(), userId, id);
    }

    private boolean longTermAvailable() {
        return config.isLongTermEnabled() && longTermMemory != null;
    }
}
