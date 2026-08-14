package com.nebula.manager.ai.skill;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.SkillPageQuery;
import com.nebula.manager.dto.SkillSaveRequest;
import com.nebula.manager.vo.SkillVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI技能管理控制器（管理员端）
 * 维护 {@code ai_skill}：分页/详情/创建/更新/删除/启停 + 下拉选项。技能按 {@code skill_code} 被
 * Agent（{@code ai_agent.skill_codes}）与节点（{@code nodeConfig.skillCodes}）引用，两层取并集后装载。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-skill/skills")
@RequiredArgsConstructor
public class SkillAdminController {

    private final SkillAdminService skillAdminService;

    /**
     * 分页查询技能
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("manager:ai-skill:list")
    public R<PageResult<SkillVO>> page(@ModelAttribute SkillPageQuery query) {
        return R.success(skillAdminService.page(query));
    }

    /**
     * 全部启用技能（编码+名称），供 Agent/节点配置面板下拉选择
     */
    @GetMapping("/options")
    @SaCheckPermission("manager:ai-skill:query")
    public R<List<SkillVO>> options() {
        return R.success(skillAdminService.options());
    }

    /**
     * 获取技能详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("manager:ai-skill:query")
    public R<SkillVO> detail(@PathVariable Long id) {
        return R.success(skillAdminService.detail(id));
    }

    /**
     * 创建技能
     */
    @PostMapping
    @SaCheckPermission("manager:ai-skill:add")
    public R<Long> create(@RequestBody SkillSaveRequest request) {
        return R.success("create success", skillAdminService.create(request));
    }

    /**
     * 更新技能
     */
    @PutMapping("/{id}")
    @SaCheckPermission("manager:ai-skill:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody SkillSaveRequest request) {
        skillAdminService.update(id, request);
        return R.success("update success", null);
    }

    /**
     * 删除技能
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("manager:ai-skill:delete")
    public R<Void> delete(@PathVariable Long id) {
        skillAdminService.delete(id);
        return R.success("delete success", null);
    }

    /**
     * 更新启用/停用状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("manager:ai-skill:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        skillAdminService.updateStatus(id, status);
        return R.success("status updated", null);
    }
}
