package com.nebula.manager.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.ai.flow.FlowDefinition;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.FlowPageQuery;
import com.nebula.manager.dto.FlowRunRequest;
import com.nebula.manager.service.FlowAdminService;
import com.nebula.manager.vo.FlowRunResultVO;
import com.nebula.manager.vo.FlowSummaryVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * AI流程编排管理控制器（管理员端）
 * 提供流程定义的列表/详情/保存整图/删除/一键运行，以及节点类型元数据。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-flow/flows")
@RequiredArgsConstructor
public class FlowAdminController {

    private final FlowAdminService flowAdminService;

    /**
     * 分页查询流程
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("manager:ai-flow:list")
    public R<PageResult<FlowSummaryVO>> page(@ModelAttribute FlowPageQuery query) {
        return R.success(flowAdminService.page(query));
    }

    /**
     * 获取流程完整定义（含节点与边）
     */
    @GetMapping("/{flowCode}")
    @SaCheckPermission("manager:ai-flow:query")
    public R<FlowDefinition> detail(@PathVariable String flowCode) {
        return R.success(flowAdminService.getDefinition(flowCode));
    }

    /**
     * 保存整图（前端导出的流程定义直接落库）
     */
    @PostMapping
    @SaCheckPermission("manager:ai-flow:save")
    public R<String> save(@RequestBody FlowDefinition definition) {
        return R.success(flowAdminService.save(definition));
    }

    /**
     * 删除流程
     */
    @DeleteMapping("/{flowCode}")
    @SaCheckPermission("manager:ai-flow:delete")
    public R<Void> delete(@PathVariable String flowCode) {
        flowAdminService.delete(flowCode);
        return R.success();
    }

    /**
     * 一键运行流程
     */
    @PostMapping("/{flowCode}/run")
    @SaCheckPermission("manager:ai-flow:run")
    public R<FlowRunResultVO> run(@PathVariable String flowCode, @RequestBody(required = false) FlowRunRequest request) {
        return R.success(flowAdminService.run(flowCode, request));
    }

    /**
     * 节点类型元数据（驱动前端节点面板）。MVP 仅 PROMPT。
     */
    @GetMapping("/node-types")
    @SaCheckPermission("manager:ai-flow:query")
    public R<List<Map<String, String>>> nodeTypes() {
        return R.success(List.of(Map.of("type", "PROMPT", "name", "提示词节点")));
    }
}
