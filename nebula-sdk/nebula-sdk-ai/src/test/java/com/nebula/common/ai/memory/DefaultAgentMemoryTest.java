package com.nebula.common.ai.memory;

import com.nebula.common.ai.api.AiConversationRepository;
import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultAgentMemoryTest {

    private static Map<String, Object> message(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    /**
     * 仅记录最近一次写入参数的长期记忆桩
     */
    private static final class FakeLongTermMemory implements LongTermMemory {
        MemoryRecord saved;
        MemoryQuery searched;
        String deletedAgentCode;

        @Override
        public String save(MemoryRecord record) {
            this.saved = record;
            return "id-1";
        }

        @Override
        public List<String> saveAll(List<MemoryRecord> records) {
            return new ArrayList<>();
        }

        @Override
        public List<MemoryRecord> search(MemoryQuery query) {
            this.searched = query;
            return new ArrayList<>();
        }

        @Override
        public MemoryRecord get(String agentCode, String userId, String id) {
            return null;
        }

        @Override
        public void delete(String agentCode, String userId, String id) {
            this.deletedAgentCode = agentCode;
        }

        @Override
        public void clear(String agentCode, String userId) {
        }
    }

    private static final class FakeConversationRepository implements AiConversationRepository {
        final List<Map<String, Object>> messages = new ArrayList<>();

        @Override
        public void saveMessage(String conversationId, Map<String, Object> message) {
            messages.add(message);
        }

        @Override
        public void saveRecord(String conversationId, Map<String, Object> record) {
        }

        @Override
        public List<Map<String, Object>> listMessages(String conversationId) {
            return messages;
        }

        @Override
        public List<Map<String, Object>> listRecords(String conversationId) {
            return new ArrayList<>();
        }

        @Override
        public void clear(String conversationId) {
            messages.clear();
        }
    }

    @Test
    void rememberInjectsAgentCodeWhenEnabled() {
        FakeLongTermMemory longTerm = new FakeLongTermMemory();
        AgentMemoryConfig config = new AgentMemoryConfig("code-helper").setLongTermEnabled(true);
        AgentMemory memory = new DefaultAgentMemory(config, null, longTerm);

        String id = memory.remember(new MemoryRecord().setUserId("u1").setType(MemoryType.SEMANTIC).setContent("x"));

        assertEquals("id-1", id);
        assertEquals("code-helper", longTerm.saved.getAgentCode());
    }

    @Test
    void recallInjectsAgentCode() {
        FakeLongTermMemory longTerm = new FakeLongTermMemory();
        AgentMemoryConfig config = new AgentMemoryConfig("customer-service").setLongTermEnabled(true);
        AgentMemory memory = new DefaultAgentMemory(config, null, longTerm);

        memory.recall(new MemoryQuery().setUserId("u1").setText("hi"));

        assertEquals("customer-service", longTerm.searched.getAgentCode());
    }

    @Test
    void longTermDisabledSkipsWrite() {
        FakeLongTermMemory longTerm = new FakeLongTermMemory();
        AgentMemoryConfig config = new AgentMemoryConfig("plain").setLongTermEnabled(false);
        AgentMemory memory = new DefaultAgentMemory(config, null, longTerm);

        assertNull(memory.remember(new MemoryRecord().setType(MemoryType.SEMANTIC)));
        assertTrue(memory.recall(new MemoryQuery()).isEmpty());
        assertNull(longTerm.saved);
    }

    @Test
    void disabledMemoryTypeIsNotWritten() {
        FakeLongTermMemory longTerm = new FakeLongTermMemory();
        AgentMemoryConfig config = new AgentMemoryConfig("entity-only")
                .setLongTermEnabled(true)
                .setEnabledTypes(EnumSet.of(MemoryType.ENTITY));
        AgentMemory memory = new DefaultAgentMemory(config, null, longTerm);

        // SEMANTIC 未启用 -> 忽略
        assertNull(memory.remember(new MemoryRecord().setType(MemoryType.SEMANTIC)));
        assertNull(longTerm.saved);
        // ENTITY 启用 -> 写入
        assertEquals("id-1", memory.remember(new MemoryRecord().setType(MemoryType.ENTITY)));
    }

    @Test
    void loadHistoryAppliesAgentWindow() {
        FakeConversationRepository repo = new FakeConversationRepository();
        repo.saveMessage("c1", message("system", "S"));
        repo.saveMessage("c1", message("user", "u1"));
        repo.saveMessage("c1", message("assistant", "a1"));
        repo.saveMessage("c1", message("user", "u2"));

        AgentMemoryConfig config = new AgentMemoryConfig("windowed")
                .setWindow(new MessageCountMemoryWindow(1));
        AgentMemory memory = new DefaultAgentMemory(config, repo, null);

        List<Map<String, Object>> history = memory.loadHistory("c1");

        // system 保留 + 最近1条非system
        assertEquals(2, history.size());
        assertEquals("system", history.get(0).get("role"));
        assertEquals("u2", history.get(1).get("content"));
    }

    @Test
    void forgetPassesAgentCode() {
        FakeLongTermMemory longTerm = new FakeLongTermMemory();
        AgentMemoryConfig config = new AgentMemoryConfig("code-helper").setLongTermEnabled(true);
        AgentMemory memory = new DefaultAgentMemory(config, null, longTerm);

        memory.forget("u1", "id-1");

        assertEquals("code-helper", longTerm.deletedAgentCode);
    }
}
