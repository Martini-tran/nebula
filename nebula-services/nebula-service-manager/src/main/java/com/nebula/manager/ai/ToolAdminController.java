package com.nebula.manager.ai;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.manager.dto.ToolPageQuery;
import com.nebula.manager.vo.ToolVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 工具查询控制器（管理员端，只读）
 * 工具定义于代码、由启动同步器维护进 {@code ai_tool} 表，本控制器仅提供分页/详情查询，无增删改端点。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/admin/ai-tool/tools")
@RequiredArgsConstructor
public class ToolAdminController {

    private final ToolAdminService toolAdminService;

    /**
     * 分页查询工具
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("manager:ai-tool:list")
    public R<PageResult<ToolVO>> page(@ModelAttribute ToolPageQuery query) {
        return R.success(toolAdminService.page(query));
    }

    /**
     * 获取工具详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("manager:ai-tool:query")
    public R<ToolVO> detail(@PathVariable Long id) {
        return R.success(toolAdminService.detail(id));
    }
}
