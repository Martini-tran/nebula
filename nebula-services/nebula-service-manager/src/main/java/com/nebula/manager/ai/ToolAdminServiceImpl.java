package com.nebula.manager.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.store.AiTool;
import com.nebula.common.ai.flow.store.AiToolMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.ToolPageQuery;
import com.nebula.manager.vo.ToolVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * AI 工具查询服务实现（管理员端，只读）
 * 直接读取 {@code ai_tool} 表，反序列化 paramsSchema/resultSchema 后输出。本表由启动同步器维护，
 * 故此处不提供任何写入能力。
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ToolAdminServiceImpl implements ToolAdminService {

    private final AiToolMapper toolMapper;

    /**
     * JSON 处理器：自建实例而非容器注入（容器走 Jackson 3，无 Jackson 2 的 ObjectMapper Bean），
     * 与 {@code McpServerAdminServiceImpl} 做法一致。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResult<ToolVO> page(ToolPageQuery query) {
        ToolPageQuery safe = query == null ? new ToolPageQuery() : query;
        Page<AiTool> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiTool> wrapper = new LambdaQueryWrapper<AiTool>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiTool::getToolCode, safe.getKeyword())
                        .or()
                        .like(AiTool::getName, safe.getKeyword())
                        .or()
                        .like(AiTool::getDescription, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getCategory()), AiTool::getCategory, safe.getCategory())
                .eq(safe.getEnabled() != null, AiTool::getEnabled, safe.getEnabled())
                .orderByAsc(AiTool::getSortNo)
                .orderByDesc(AiTool::getUpdateTime);
        Page<AiTool> result = toolMapper.selectPage(page, wrapper);
        List<ToolVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ToolVO detail(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "工具ID不能为空");
        }
        AiTool entity = toolMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "工具不存在: " + id);
        }
        return toVO(entity);
    }

    /**
     * 实体转 VO：反序列化 paramsSchema/resultSchema。
     */
    private ToolVO toVO(AiTool entity) {
        ToolVO vo = new ToolVO();
        vo.setId(entity.getId());
        vo.setToolCode(entity.getToolCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setCategory(entity.getCategory());
        vo.setParamsSchema(readJson(entity.getParamsSchema(), new TypeReference<Map<String, Object>>() {
        }));
        vo.setResultSchema(readJson(entity.getResultSchema(), new TypeReference<Map<String, Object>>() {
        }));
        vo.setEnabled(entity.getEnabled());
        vo.setBuiltin(entity.getBuiltin());
        vo.setSortNo(entity.getSortNo());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 反序列化 JSON 字符串为目标类型；解析失败或为空返回 null。
     */
    private <T> T readJson(String json, TypeReference<T> type) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, type);
        } catch (Exception e) {
            return null;
        }
    }
}
