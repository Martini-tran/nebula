package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.ToolDefinition;
import com.nebula.common.ai.flow.ToolRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具注册表 → ai_tool 表同步器
 * 应用启动后把 {@link ToolRegistry} 中代码定义的全部工具镜像进 {@code ai_tool} 表：按 {@code tool_code} upsert
 * 元数据（名称/描述/分类/入参schema/出参schema），并把代码中已不存在的内置工具置 {@code enabled=0}（软下线），
 * 使表成为代码工具的只读镜像，供前端流程编辑器渲染可选工具列表。
 *
 * <p>代码 = 唯一真相源：本同步器是唯一的写入方，管理端只读。仅处理内置工具（{@code builtin=1}）的下线，
 * 不动外部登记工具（{@code builtin=0}，预留扩展位）。
 *
 * @author nebula
 */
@Slf4j
@Order(0)
public class ToolRegistrySynchronizer implements ApplicationRunner {

    private final ToolRegistry toolRegistry;

    private final AiToolMapper toolMapper;

    private final ObjectMapper objectMapper;

    public ToolRegistrySynchronizer(ToolRegistry toolRegistry, AiToolMapper toolMapper, ObjectMapper objectMapper) {
        this.toolRegistry = toolRegistry;
        this.toolMapper = toolMapper;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (toolRegistry == null) {
            return;
        }
        Set<String> codeCodes = new HashSet<>();
        int upserted = 0;
        for (ToolDefinition tool : toolRegistry.all()) {
            upsert(tool);
            codeCodes.add(tool.code());
            upserted++;
        }
        int disabled = disableMissing(codeCodes);
        log.info("AI工具同步完成：upsert {} 个，软下线 {} 个内置工具", upserted, disabled);
    }

    /**
     * 按 tool_code upsert：存在则更新元数据并确保 enabled=1，不存在则插入。
     */
    private void upsert(ToolDefinition tool) {
        AiTool existing = toolMapper.selectOne(new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getToolCode, tool.code())
                .last("limit 1"));
        if (existing == null) {
            AiTool entity = new AiTool();
            entity.setToolCode(tool.code());
            entity.setName(tool.name());
            entity.setDescription(tool.description());
            entity.setCategory(tool.category());
            entity.setParamsSchema(writeJson(tool.paramsSchema()));
            entity.setResultSchema(writeJson(tool.resultSchema()));
            entity.setEnabled(1);
            entity.setBuiltin(1);
            entity.setSortNo(tool.sortNo());
            toolMapper.insert(entity);
        } else {
            existing.setName(tool.name());
            existing.setDescription(tool.description());
            existing.setCategory(tool.category());
            existing.setParamsSchema(writeJson(tool.paramsSchema()));
            existing.setResultSchema(writeJson(tool.resultSchema()));
            existing.setEnabled(1);
            existing.setBuiltin(1);
            existing.setSortNo(tool.sortNo());
            toolMapper.updateById(existing);
        }
    }

    /**
     * 把代码中已不存在的内置工具（builtin=1 且当前 enabled=1）置 enabled=0；不触碰外部登记工具。
     *
     * @param codeCodes 代码中现存的工具编码集合
     * @return 软下线数量
     */
    private int disableMissing(Set<String> codeCodes) {
        List<AiTool> builtinEnabled = toolMapper.selectList(new LambdaQueryWrapper<AiTool>()
                .eq(AiTool::getBuiltin, 1)
                .eq(AiTool::getEnabled, 1));
        int disabled = 0;
        for (AiTool entity : builtinEnabled) {
            if (!codeCodes.contains(entity.getToolCode())) {
                entity.setEnabled(0);
                entity.setUpdateTime(LocalDateTime.now());
                toolMapper.updateById(entity);
                disabled++;
            }
        }
        return disabled;
    }

    /**
     * 序列化 schema 为 JSON 字符串；为空（null / 空 Map）返回 null。
     */
    private String writeJson(Map<String, Object> value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            log.warn("工具 schema 序列化失败，置空: {}", e.getMessage());
            return null;
        }
    }
}
