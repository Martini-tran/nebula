package com.nebula.manager.ai.prompt;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.store.AiPrompt;
import com.nebula.common.ai.flow.store.AiPromptMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.PromptPageQuery;
import com.nebula.manager.dto.PromptSaveRequest;
import com.nebula.manager.vo.PromptVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI提示词管理服务实现（管理员端）
 * 直接操作 {@code ai_prompt} 表完成 CRUD：写入端把 variables 序列化为 JSON 数组字符串，读出端还原为对象数组。
 * 提示词编码全局唯一且创建后不可变更，避免已被流程/节点引用的 {@code prompt_code} 失效。
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class PromptAdminServiceImpl implements PromptAdminService {

    /**
     * 允许的消息角色。与 OpenAI 兼容协议的 role 取值一致。
     */
    private static final Set<String> ALLOWED_ROLES = Set.of("system", "user", "assistant");

    /**
     * 角色缺省值：绝大多数提示词用作系统设定。
     */
    private static final String DEFAULT_ROLE = "system";

    private final AiPromptMapper promptMapper;

    /**
     * JSON 处理器：自建实例而非容器注入。本工程 Web 层走 Jackson 3（{@code tools.jackson}），
     * 容器中并无 Jackson 2（{@code com.fasterxml.jackson}）的 {@code ObjectMapper} Bean，
     * 强行构造注入会导致启动失败。与 {@code ModelProfileAdminServiceImpl} 的做法保持一致。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResult<PromptVO> page(PromptPageQuery query) {
        PromptPageQuery safe = query == null ? new PromptPageQuery() : query;
        Page<AiPrompt> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiPrompt> wrapper = new LambdaQueryWrapper<AiPrompt>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiPrompt::getPromptCode, safe.getKeyword())
                        .or()
                        .like(AiPrompt::getName, safe.getKeyword())
                        .or()
                        .like(AiPrompt::getContent, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getRole()), AiPrompt::getRole, safe.getRole())
                .orderByDesc(AiPrompt::getUpdateTime);
        Page<AiPrompt> result = promptMapper.selectPage(page, wrapper);
        List<PromptVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public PromptVO detail(Long id) {
        return toVO(getExisting(id));
    }

    @Override
    public Long create(PromptSaveRequest request) {
        validate(request, true);
        if (exists(request.getPromptCode(), null)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "提示词编码已存在: " + request.getPromptCode());
        }
        AiPrompt entity = new AiPrompt();
        applyRequest(entity, request, true);
        promptMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, PromptSaveRequest request) {
        validate(request, false);
        AiPrompt entity = getExisting(id);
        // 提示词编码不可变更，忽略请求中的 promptCode
        applyRequest(entity, request, false);
        promptMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        getExisting(id);
        promptMapper.deleteById(id);
    }

    /**
     * 将保存请求合并进实体：序列化 variables、补齐 role 缺省值。
     *
     * @param entity   目标实体
     * @param request  保存请求
     * @param creating 是否为创建（创建时写入 promptCode）
     */
    private void applyRequest(AiPrompt entity, PromptSaveRequest request, boolean creating) {
        if (creating) {
            entity.setPromptCode(request.getPromptCode());
        }
        entity.setName(request.getName());
        entity.setRole(StringUtils.hasText(request.getRole()) ? request.getRole() : DEFAULT_ROLE);
        entity.setContent(request.getContent());
        entity.setRemark(request.getRemark());
        entity.setVariables(writeVariables(request.getVariables()));
    }

    /**
     * 实体转 VO。
     */
    private PromptVO toVO(AiPrompt entity) {
        PromptVO vo = new PromptVO();
        vo.setId(entity.getId());
        vo.setPromptCode(entity.getPromptCode());
        vo.setName(entity.getName());
        vo.setRole(entity.getRole());
        vo.setContent(entity.getContent());
        vo.setVariables(readVariables(entity.getVariables()));
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 序列化 variables 为 JSON 数组字符串；为空返回 null。
     */
    private String writeVariables(List<Map<String, Object>> variables) {
        if (variables == null || variables.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(variables);
        } catch (Exception e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "变量声明序列化失败");
        }
    }

    /**
     * 反序列化 variables JSON 数组字符串；解析失败或为空返回 null。
     */
    private List<Map<String, Object>> readVariables(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<Map<String, Object>>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private AiPrompt getExisting(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "提示词ID不能为空");
        }
        AiPrompt entity = promptMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "提示词不存在: " + id);
        }
        return entity;
    }

    private boolean exists(String promptCode, Long excludeId) {
        LambdaQueryWrapper<AiPrompt> wrapper = new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getPromptCode, promptCode)
                .ne(excludeId != null, AiPrompt::getId, excludeId);
        return promptMapper.selectCount(wrapper) > 0;
    }

    private void validate(PromptSaveRequest request, boolean creating) {
        if (request == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        if (creating && !StringUtils.hasText(request.getPromptCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "提示词编码不能为空");
        }
        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "提示词正文不能为空");
        }
        if (StringUtils.hasText(request.getRole()) && !ALLOWED_ROLES.contains(request.getRole())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "消息角色非法，仅支持 system/user/assistant");
        }
    }
}
