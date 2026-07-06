package com.nebula.manager.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.AgentInstancePageQuery;
import com.nebula.manager.dto.AgentPageQuery;
import com.nebula.manager.dto.AgentRunRequest;
import com.nebula.manager.dto.AgentSaveRequest;
import com.nebula.manager.dto.AgentSignalRequest;
import com.nebula.manager.vo.AgentInstanceVO;
import com.nebula.manager.vo.AgentRunResultVO;
import com.nebula.manager.vo.AgentSummaryVO;
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

/**
 * AI 智能体管理控制器（管理员端）
 * Agent 定义 CRUD（{@code /agents}）+ 实例运行/唤醒/续跑（{@code /agents/{code}/run}、{@code /instances/{id}/signal|resume}）
 * + 实例回放（{@code /instances}、{@code /instances/{id}}，含转移历史时间线）。沿用 {@code /admin/ai-flow} 风格，camelCase。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-agent")
@RequiredArgsConstructor
public class AiAgentAdminController {

    private final AiAgentAdminService aiAgentAdminService;

    /* ===================== Agent 定义 CRUD ===================== */

    /**
     * 分页查询 Agent 定义
     */
    @GetMapping({"/agents", "/agents/page"})
    @SaCheckPermission("manager:ai-agent:list")
    public R<PageResult<AgentSummaryVO>> page(@ModelAttribute AgentPageQuery query) {
        return R.success(aiAgentAdminService.page(query));
    }

    /**
     * 获取 Agent 定义详情
     */
    @GetMapping("/agents/{id}")
    @SaCheckPermission("manager:ai-agent:query")
    public R<AgentSummaryVO> detail(@PathVariable Long id) {
        return R.success(aiAgentAdminService.detail(id));
    }

    /**
     * 创建 Agent 定义
     */
    @PostMapping("/agents")
    @SaCheckPermission("manager:ai-agent:add")
    public R<Long> create(@RequestBody AgentSaveRequest request) {
        return R.success("create success", aiAgentAdminService.create(request));
    }

    /**
     * 更新 Agent 定义
     */
    @PutMapping("/agents/{id}")
    @SaCheckPermission("manager:ai-agent:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody AgentSaveRequest request) {
        aiAgentAdminService.update(id, request);
        return R.success("update success", null);
    }

    /**
     * 删除 Agent 定义
     */
    @DeleteMapping("/agents/{id}")
    @SaCheckPermission("manager:ai-agent:delete")
    public R<Void> delete(@PathVariable Long id) {
        aiAgentAdminService.delete(id);
        return R.success("delete success", null);
    }

    /**
     * 更新启用/停用状态
     */
    @PutMapping("/agents/{id}/status")
    @SaCheckPermission("manager:ai-agent:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        aiAgentAdminService.updateStatus(id, status);
        return R.success("status updated", null);
    }

    /* ===================== 实例运行 / 唤醒 / 续跑 ===================== */

    /**
     * 按 agentCode 创建实例并执行
     */
    @PostMapping("/agents/{agentCode}/run")
    @SaCheckPermission("manager:ai-agent:run")
    public R<AgentRunResultVO> run(@PathVariable String agentCode,
                                   @RequestBody(required = false) AgentRunRequest request) {
        return R.success(aiAgentAdminService.run(agentCode, request));
    }

    /**
     * 唤醒一个挂起（SUSPENDED）实例（Human-in-the-loop）
     */
    @PostMapping("/instances/{instanceId}/signal")
    @SaCheckPermission("manager:ai-agent:run")
    public R<AgentRunResultVO> signal(@PathVariable String instanceId,
                                      @RequestBody(required = false) AgentSignalRequest request) {
        return R.success(aiAgentAdminService.signal(instanceId, request));
    }

    /**
     * 崩溃恢复：从落库状态恢复实例并续跑（RUNNING 断点）
     */
    @PostMapping("/instances/{instanceId}/resume")
    @SaCheckPermission("manager:ai-agent:run")
    public R<AgentRunResultVO> resume(@PathVariable String instanceId) {
        return R.success(aiAgentAdminService.resume(instanceId));
    }

    /* ===================== 实例回放 ===================== */

    /**
     * 分页查询实例
     */
    @GetMapping("/instances")
    @SaCheckPermission("manager:ai-agent:query")
    public R<PageResult<AgentInstanceVO>> instances(@ModelAttribute AgentInstancePageQuery query) {
        return R.success(aiAgentAdminService.instances(query));
    }

    /**
     * 实例详情（含转移历史时间线，用于回放）
     */
    @GetMapping("/instances/{instanceId}")
    @SaCheckPermission("manager:ai-agent:query")
    public R<AgentInstanceVO> instanceDetail(@PathVariable String instanceId) {
        return R.success(aiAgentAdminService.instanceDetail(instanceId));
    }
}
