package com.nebula.manager.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.McpServerPageQuery;
import com.nebula.manager.dto.McpServerSaveRequest;
import com.nebula.manager.vo.McpServerVO;
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
 * AI MCP 服务器管理控制器（管理员端）
 * 维护 {@code ai_mcp_server}：分页/详情/创建/更新/删除/启停。authToken 入参为明文（服务端加密落库），
 * 出参一律掩码、不回传明文。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-mcp-server/servers")
@RequiredArgsConstructor
public class McpServerAdminController {

    private final McpServerAdminService mcpServerAdminService;

    /**
     * 分页查询 MCP 服务器
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("manager:ai-mcp-server:list")
    public R<PageResult<McpServerVO>> page(@ModelAttribute McpServerPageQuery query) {
        return R.success(mcpServerAdminService.page(query));
    }

    /**
     * 获取 MCP 服务器详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("manager:ai-mcp-server:query")
    public R<McpServerVO> detail(@PathVariable Long id) {
        return R.success(mcpServerAdminService.detail(id));
    }

    /**
     * 创建 MCP 服务器
     */
    @PostMapping
    @SaCheckPermission("manager:ai-mcp-server:add")
    public R<Long> create(@RequestBody McpServerSaveRequest request) {
        return R.success("create success", mcpServerAdminService.create(request));
    }

    /**
     * 更新 MCP 服务器
     */
    @PutMapping("/{id}")
    @SaCheckPermission("manager:ai-mcp-server:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody McpServerSaveRequest request) {
        mcpServerAdminService.update(id, request);
        return R.success("update success", null);
    }

    /**
     * 删除 MCP 服务器
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("manager:ai-mcp-server:delete")
    public R<Void> delete(@PathVariable Long id) {
        mcpServerAdminService.delete(id);
        return R.success("delete success", null);
    }

    /**
     * 更新启用/停用状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("manager:ai-mcp-server:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        mcpServerAdminService.updateStatus(id, status);
        return R.success("status updated", null);
    }
}
