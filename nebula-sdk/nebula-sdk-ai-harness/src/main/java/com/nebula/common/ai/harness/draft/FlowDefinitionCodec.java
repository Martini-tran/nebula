package com.nebula.common.ai.harness.draft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.FlowDefinition;

/**
 * FlowDefinition 专用严格 JSON codec。
 *
 * <p>持久化 JSON 损坏或出现未知字段属于数据完整性错误，不能像通用 Map codec 一样静默降级为空图。
 *
 * @author nebula
 */
public class FlowDefinitionCodec {

    private final ObjectMapper objectMapper;

    public FlowDefinitionCodec(ObjectMapper objectMapper) {
        ObjectMapper source = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.objectMapper = source.copy()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    }

    public String write(FlowDefinition definition) {
        if (definition == null) {
            throw new IllegalArgumentException("流程图不能为空");
        }
        try {
            return objectMapper.writeValueAsString(definition);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("流程草稿序列化失败", e);
        }
    }

    public FlowDefinition read(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalStateException("流程草稿 graph_json 为空");
        }
        try {
            FlowDefinition definition = objectMapper.readValue(json, FlowDefinition.class);
            if (definition == null) {
                throw new IllegalStateException("流程草稿 graph_json 解析结果为空");
            }
            return definition;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("流程草稿 graph_json 已损坏", e);
        }
    }

    public FlowDefinition copy(FlowDefinition definition) {
        return read(write(definition));
    }

    public int byteSize(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value).length;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("草稿大小计算失败", e);
        }
    }
}
