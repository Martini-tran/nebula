package com.nebula.manager.ai.skill;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.common.ai.flow.store.AiSkill;
import com.nebula.common.ai.flow.store.AiSkillMapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.manager.dto.SkillPageQuery;
import com.nebula.manager.dto.SkillSaveRequest;
import com.nebula.manager.vo.SkillVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * AI技能管理服务实现（管理员端）
 * 直接操作 {@code ai_skill} 表完成 CRUD：写入端把 toolCodes/mcpServerCodes 序列化为 JSON 数组字符串，
 * 读出端还原为字符串数组。技能编码全局唯一且创建后不可变更，避免已被 Agent/节点引用的
 * {@code skill_code} 失效。
 *
 * <p>技能是纯 DB 配置，改完即刻对运行时生效（{@code DatabaseSkillRepository} 实时查库不缓存），
 * 故此处不需要任何刷新/重载动作。停用（{@code status=0}）即对运行时不可见，是「关掉一个技能」的开关。
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class SkillAdminServiceImpl implements SkillAdminService {

    /**
     * 允许的触发方式。AUTO=范围内始终装载；MANUAL=被显式引用才装载。
     */
    private static final Set<String> ALLOWED_TRIGGER_TYPES = Set.of("AUTO", "MANUAL");

    /**
     * 触发方式缺省值：绝大多数技能按需引用。
     */
    private static final String DEFAULT_TRIGGER_TYPE = "MANUAL";

    private final AiSkillMapper skillMapper;

    /**
     * JSON 处理器：自建实例而非容器注入。本工程 Web 层走 Jackson 3（{@code tools.jackson}），
     * 容器中并无 Jackson 2（{@code com.fasterxml.jackson}）的 {@code ObjectMapper} Bean，
     * 强行构造注入会导致启动失败。与 {@code PromptAdminServiceImpl} 的做法保持一致。
     */
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PageResult<SkillVO> page(SkillPageQuery query) {
        SkillPageQuery safe = query == null ? new SkillPageQuery() : query;
        Page<AiSkill> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<AiSkill>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiSkill::getSkillCode, safe.getKeyword())
                        .or()
                        .like(AiSkill::getName, safe.getKeyword())
                        .or()
                        .like(AiSkill::getDescription, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getTriggerType()), AiSkill::getTriggerType, safe.getTriggerType())
                .eq(safe.getStatus() != null, AiSkill::getStatus, safe.getStatus())
                .orderByAsc(AiSkill::getSortNo)
                .orderByDesc(AiSkill::getUpdateTime);
        Page<AiSkill> result = skillMapper.selectPage(page, wrapper);
        List<SkillVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public SkillVO detail(Long id) {
        return toVO(getExisting(id));
    }

    @Override
    public Long create(SkillSaveRequest request) {
        validate(request, true);
        if (exists(request.getSkillCode(), null)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "技能编码已存在: " + request.getSkillCode());
        }
        AiSkill entity = new AiSkill();
        applyRequest(entity, request, true);
        skillMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, SkillSaveRequest request) {
        validate(request, false);
        AiSkill entity = getExisting(id);
        // 技能编码不可变更，忽略请求中的 skillCode
        applyRequest(entity, request, false);
        skillMapper.updateById(entity);
    }

    @Override
    public void delete(Long id) {
        getExisting(id);
        skillMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态值非法");
        }
        AiSkill entity = getExisting(id);
        entity.setStatus(status);
        skillMapper.updateById(entity);
    }

    @Override
    public List<SkillVO> options() {
        List<AiSkill> entities = skillMapper.selectList(new LambdaQueryWrapper<AiSkill>()
                .eq(AiSkill::getStatus, 1)
                .orderByAsc(AiSkill::getSortNo)
                .orderByAsc(AiSkill::getSkillCode));
        List<SkillVO> rows = new ArrayList<>();
        for (AiSkill entity : entities) {
            SkillVO vo = new SkillVO();
            vo.setId(entity.getId());
            vo.setSkillCode(entity.getSkillCode());
            vo.setName(entity.getName());
            vo.setDescription(entity.getDescription());
            vo.setSortNo(entity.getSortNo());
            vo.setStatus(entity.getStatus());
            rows.add(vo);
        }
        return rows;
    }

    /**
     * 将保存请求合并进实体：序列化编码数组、补齐触发方式/排序号/状态缺省值。
     *
     * @param entity   目标实体
     * @param request  保存请求
     * @param creating 是否为创建（创建时写入 skillCode）
     */
    private void applyRequest(AiSkill entity, SkillSaveRequest request, boolean creating) {
        if (creating) {
            entity.setSkillCode(request.getSkillCode());
        }
        entity.setName(request.getName());
        entity.setDescription(request.getDescription());
        entity.setInstructions(request.getInstructions());
        entity.setTriggerType(StringUtils.hasText(request.getTriggerType())
                ? request.getTriggerType().trim().toUpperCase() : DEFAULT_TRIGGER_TYPE);
        entity.setToolCodes(writeCodes(request.getToolCodes()));
        entity.setMcpServerCodes(writeCodes(request.getMcpServerCodes()));
        entity.setSortNo(request.getSortNo() == null ? 0 : request.getSortNo());
        entity.setStatus(request.getStatus() == null ? 1 : request.getStatus());
        entity.setRemark(request.getRemark());
    }

    /**
     * 实体转 VO。
     */
    private SkillVO toVO(AiSkill entity) {
        SkillVO vo = new SkillVO();
        vo.setId(entity.getId());
        vo.setSkillCode(entity.getSkillCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setInstructions(entity.getInstructions());
        vo.setTriggerType(entity.getTriggerType());
        vo.setToolCodes(readCodes(entity.getToolCodes()));
        vo.setMcpServerCodes(readCodes(entity.getMcpServerCodes()));
        vo.setSortNo(entity.getSortNo());
        vo.setStatus(entity.getStatus());
        vo.setRemark(entity.getRemark());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    /**
     * 序列化编码数组为 JSON 数组字符串；为空返回 null。过滤空白项，避免写入无效编码。
     */
    private String writeCodes(List<String> codes) {
        if (codes == null || codes.isEmpty()) {
            return null;
        }
        List<String> cleaned = codes.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
        if (cleaned.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(cleaned);
        } catch (Exception e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "编码数组序列化失败");
        }
    }

    /**
     * 反序列化编码数组 JSON 字符串；解析失败或为空返回 null。
     */
    private List<String> readCodes(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<String>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }

    private AiSkill getExisting(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "技能ID不能为空");
        }
        AiSkill entity = skillMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "技能不存在: " + id);
        }
        return entity;
    }

    private boolean exists(String skillCode, Long excludeId) {
        LambdaQueryWrapper<AiSkill> wrapper = new LambdaQueryWrapper<AiSkill>()
                .eq(AiSkill::getSkillCode, skillCode)
                .ne(excludeId != null, AiSkill::getId, excludeId);
        return skillMapper.selectCount(wrapper) > 0;
    }

    private void validate(SkillSaveRequest request, boolean creating) {
        if (request == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求不能为空");
        }
        if (creating && !StringUtils.hasText(request.getSkillCode())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "技能编码不能为空");
        }
        if (!StringUtils.hasText(request.getInstructions())
                && (request.getToolCodes() == null || request.getToolCodes().isEmpty())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "指令正文与绑定工具不能同时为空");
        }
        if (StringUtils.hasText(request.getTriggerType())
                && !ALLOWED_TRIGGER_TYPES.contains(request.getTriggerType().trim().toUpperCase())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "触发方式非法，仅支持 AUTO/MANUAL");
        }
        if (request.getStatus() != null && request.getStatus() != 0 && request.getStatus() != 1) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态值非法");
        }
    }
}
