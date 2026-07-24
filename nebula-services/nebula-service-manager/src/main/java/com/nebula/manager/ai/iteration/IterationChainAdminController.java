package com.nebula.manager.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.IterationChainPageQuery;
import com.nebula.manager.dto.IterationChainSaveRequest;
import com.nebula.manager.vo.IterationChainVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 跨实例迭代链管理控制器（管理员端）
 * 链 CRUD + 暂停/恢复 + 立即推进（run-now）+ 详情（含时间线）。让"开一个系列 → 每天基于上轮产出生成一篇"
 * 可视化管理。沿用 {@code /admin/ai-agent} 风格，camelCase。见 docs/跨实例迭代层设计.md 第七章。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-iteration")
@RequiredArgsConstructor
public class IterationChainAdminController {

    private final IterationChainAdminService service;

    /**
     * 分页查询迭代链
     */
    @GetMapping({"/chains", "/chains/page"})
    @SaCheckPermission("manager:ai-iteration:list")
    public R<PageResult<IterationChainVO>> page(@ModelAttribute IterationChainPageQuery query) {
        return R.success(service.page(query));
    }

    /**
     * 链详情（含已产出实例时间线）
     */
    @GetMapping("/chains/{chainId}")
    @SaCheckPermission("manager:ai-iteration:query")
    public R<IterationChainVO> detail(@PathVariable String chainId) {
        return R.success(service.detail(chainId));
    }

    /**
     * 创建迭代链（开一个"系列"），返回 chainId
     */
    @PostMapping("/chains")
    @SaCheckPermission("manager:ai-iteration:add")
    public R<String> create(@RequestBody IterationChainSaveRequest request) {
        return R.success("create success", service.create(request));
    }

    /**
     * 更新迭代链配置
     */
    @PutMapping("/chains/{chainId}")
    @SaCheckPermission("manager:ai-iteration:edit")
    public R<Void> update(@PathVariable String chainId, @RequestBody IterationChainSaveRequest request) {
        service.update(chainId, request);
        return R.success("update success", null);
    }

    /**
     * 暂停链（ACTIVE → PAUSED）
     */
    @PostMapping("/chains/{chainId}/pause")
    @SaCheckPermission("manager:ai-iteration:edit")
    public R<Void> pause(@PathVariable String chainId) {
        service.pause(chainId);
        return R.success("paused", null);
    }

    /**
     * 恢复链（PAUSED → ACTIVE）
     */
    @PostMapping("/chains/{chainId}/resume")
    @SaCheckPermission("manager:ai-iteration:edit")
    public R<Void> resume(@PathVariable String chainId) {
        service.resume(chainId);
        return R.success("resumed", null);
    }

    /**
     * 立即推进一轮（run-now）：把 next_run_at 置当前，下次扫描即跑
     */
    @PostMapping("/chains/{chainId}/run-now")
    @SaCheckPermission("manager:ai-iteration:run")
    public R<Void> runNow(@PathVariable String chainId) {
        service.runNow(chainId);
        return R.success("will run on next scan", null);
    }

    /**
     * 删除链（不删已产出实例）
     */
    @DeleteMapping("/chains/{chainId}")
    @SaCheckPermission("manager:ai-iteration:delete")
    public R<Void> delete(@PathVariable String chainId) {
        service.delete(chainId);
        return R.success("delete success", null);
    }
}
