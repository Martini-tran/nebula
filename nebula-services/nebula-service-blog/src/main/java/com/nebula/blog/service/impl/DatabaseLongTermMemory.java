package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.blog.entity.AiMemory;
import com.nebula.blog.mapper.AiMemoryMapper;
import com.nebula.common.ai.api.LongTermMemory;
import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据库版长期记忆实现
 * 以 MyBatis-Plus 将 {@link MemoryRecord} 落库到 ai_memory 表，按 agentCode + userId 隔离。
 * 检索采用关键词 LIKE + 过滤（content 模糊匹配，按创建时间倒序取 topK），不做语义/向量检索。
 *
 * <p>该 Bean 由 sdk-ai 的 AiMemoryAutoConfiguration 通过 ObjectProvider 自动接入各Agent的记忆门面。
 *
 * @author nebula
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DatabaseLongTermMemory implements LongTermMemory {

    private final AiMemoryMapper aiMemoryMapper;

    /**
     * 元数据 JSON 序列化器，避免抢占应用主 ObjectMapper
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String save(MemoryRecord record) {
        if (record == null) {
            return null;
        }
        AiMemory entity = toEntity(record);
        aiMemoryMapper.insert(entity);
        String id = String.valueOf(entity.getId());
        record.setId(id);
        return id;
    }

    @Override
    public List<String> saveAll(List<MemoryRecord> records) {
        List<String> ids = new ArrayList<>();
        if (records == null) {
            return ids;
        }
        for (MemoryRecord record : records) {
            String id = save(record);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    @Override
    public List<MemoryRecord> search(MemoryQuery query) {
        if (query == null) {
            return List.of();
        }
        LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMemory::getAgentCode, query.getAgentCode());
        if (StringUtils.hasText(query.getUserId())) {
            wrapper.eq(AiMemory::getUserId, query.getUserId());
        }
        if (StringUtils.hasText(query.getConversationId())) {
            wrapper.eq(AiMemory::getConversationId, query.getConversationId());
        }
        if (query.getType() != null) {
            wrapper.eq(AiMemory::getMemType, query.getType().name());
        }
        if (StringUtils.hasText(query.getText())) {
            wrapper.like(AiMemory::getContent, query.getText());
        }
        wrapper.orderByDesc(AiMemory::getCreateTime);
        int topK = query.getTopK() > 0 ? query.getTopK() : 5;
        wrapper.last("limit " + topK);

        List<AiMemory> rows = aiMemoryMapper.selectList(wrapper);
        List<MemoryRecord> records = new ArrayList<>(rows.size());
        for (AiMemory row : rows) {
            records.add(toRecord(row));
        }
        return records;
    }

    @Override
    public MemoryRecord get(String agentCode, String userId, String id) {
        Long pk = parseId(id);
        if (pk == null) {
            return null;
        }
        AiMemory entity = aiMemoryMapper.selectById(pk);
        if (entity == null || !matchesOwner(entity, agentCode, userId)) {
            return null;
        }
        return toRecord(entity);
    }

    @Override
    public void delete(String agentCode, String userId, String id) {
        Long pk = parseId(id);
        if (pk == null) {
            return;
        }
        LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMemory::getId, pk)
                .eq(AiMemory::getAgentCode, agentCode);
        if (StringUtils.hasText(userId)) {
            wrapper.eq(AiMemory::getUserId, userId);
        }
        aiMemoryMapper.delete(wrapper);
    }

    @Override
    public void clear(String agentCode, String userId) {
        LambdaQueryWrapper<AiMemory> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AiMemory::getAgentCode, agentCode);
        if (StringUtils.hasText(userId)) {
            wrapper.eq(AiMemory::getUserId, userId);
        }
        aiMemoryMapper.delete(wrapper);
    }

    /**
     * 记录转实体（写入用），时间由 MyBatis-Plus 自动填充
     *
     * @param record 记忆条目
     * @return 实体
     */
    private AiMemory toEntity(MemoryRecord record) {
        AiMemory entity = new AiMemory();
        entity.setAgentCode(record.getAgentCode());
        entity.setUserId(record.getUserId());
        entity.setConversationId(record.getConversationId());
        entity.setMemType(record.getType() == null ? null : record.getType().name());
        entity.setContent(record.getContent());
        entity.setMetadata(writeMetadata(record.getMetadata()));
        return entity;
    }

    /**
     * 实体转记录（读取用）
     *
     * @param entity 实体
     * @return 记忆条目
     */
    private MemoryRecord toRecord(AiMemory entity) {
        MemoryRecord record = new MemoryRecord()
                .setId(String.valueOf(entity.getId()))
                .setAgentCode(entity.getAgentCode())
                .setUserId(entity.getUserId())
                .setConversationId(entity.getConversationId())
                .setType(parseType(entity.getMemType()))
                .setContent(entity.getContent())
                .setMetadata(readMetadata(entity.getMetadata()))
                .setCreatedAt(toEpochMilli(entity.getCreateTime()))
                .setUpdatedAt(toEpochMilli(entity.getUpdateTime()));
        return record;
    }

    /**
     * 校验记忆归属，强制 agentCode + userId 隔离
     */
    private boolean matchesOwner(AiMemory entity, String agentCode, String userId) {
        if (agentCode != null && !agentCode.equals(entity.getAgentCode())) {
            return false;
        }
        return !StringUtils.hasText(userId) || userId.equals(entity.getUserId());
    }

    private MemoryType parseType(String memType) {
        if (!StringUtils.hasText(memType)) {
            return null;
        }
        try {
            return MemoryType.valueOf(memType);
        } catch (IllegalArgumentException e) {
            log.warn("未知的记忆类型: {}", memType);
            return null;
        }
    }

    private String writeMetadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception e) {
            log.warn("序列化记忆元数据失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, Object> readMetadata(String metadata) {
        if (!StringUtils.hasText(metadata)) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(metadata, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            log.warn("解析记忆元数据失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    private Long toEpochMilli(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private Long parseId(String id) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        try {
            return Long.valueOf(id);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
