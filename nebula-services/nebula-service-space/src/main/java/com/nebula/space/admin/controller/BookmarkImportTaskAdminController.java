package com.nebula.space.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.space.controller.AbstractAdminController;
import com.nebula.space.dto.admin.BookmarkTaskPageQuery;
import com.nebula.space.service.SpaceBookmarkImportTaskAdminService;
import com.nebula.space.vo.admin.BookmarkImportTaskAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台书签导入任务控制器
 */
@RestController
@RequestMapping("/admin/bookmark-import-tasks")
@SaCheckLogin
@RequiredArgsConstructor
public class BookmarkImportTaskAdminController extends AbstractAdminController {

    private final SpaceBookmarkImportTaskAdminService importTaskService;

    /**
     * 分页查询导入任务
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("space:bookmark-import:list")
    public R<PageResult<BookmarkImportTaskAdminVO>> page(@ModelAttribute BookmarkTaskPageQuery query) {
        return R.success(importTaskService.page(query));
    }

    /**
     * 查询任务详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("space:bookmark-import:query")
    public R<BookmarkImportTaskAdminVO> detail(@PathVariable Long id) {
        return R.success(importTaskService.detail(id));
    }

    /**
     * 取消待处理任务
     */
    @PostMapping("/{id}/cancel")
    @SaCheckPermission("space:bookmark-import:edit")
    public R<Void> cancel(@PathVariable Long id) {
        importTaskService.cancel(id);
        return R.success();
    }
}
