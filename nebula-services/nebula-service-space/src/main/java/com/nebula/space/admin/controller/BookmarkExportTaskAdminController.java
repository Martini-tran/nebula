package com.nebula.space.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.space.controller.AbstractAdminController;
import com.nebula.space.dto.admin.BookmarkTaskPageQuery;
import com.nebula.space.service.SpaceBookmarkExportTaskAdminService;
import com.nebula.space.vo.admin.BookmarkExportTaskAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台书签导出任务控制器
 */
@RestController
@RequestMapping("/admin/bookmark-export-tasks")
@SaCheckLogin
@RequiredArgsConstructor
public class BookmarkExportTaskAdminController extends AbstractAdminController {

    private final SpaceBookmarkExportTaskAdminService exportTaskService;

    /**
     * 分页查询导出任务
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("space:bookmark-export:list")
    public R<PageResult<BookmarkExportTaskAdminVO>> page(@ModelAttribute BookmarkTaskPageQuery query) {
        return R.success(exportTaskService.page(query));
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("space:bookmark-export:query")
    public R<BookmarkExportTaskAdminVO> detail(@PathVariable Long id) {
        return R.success(exportTaskService.detail(id));
    }

    /**
     * 取消待处理任务
     */
    @PostMapping("/{id}/cancel")
    @SaCheckPermission("space:bookmark-export:edit")
    public R<Void> cancel(@PathVariable Long id) {
        exportTaskService.cancel(id);
        return R.success();
    }
}
