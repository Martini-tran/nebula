package com.nebula.space.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.space.controller.AbstractAdminController;
import com.nebula.space.dto.admin.BookmarkTaskPageQuery;
import com.nebula.space.service.SpaceBookmarkExportTaskAdminService;
import com.nebula.space.service.SpaceBookmarkPorterService;
import com.nebula.space.vo.admin.BookmarkExportTaskAdminVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 后台书签导出任务控制器
 */
@Slf4j
@RestController
@RequestMapping("/admin/bookmark-export-tasks")
@SaCheckLogin
@RequiredArgsConstructor
public class BookmarkExportTaskAdminController extends AbstractAdminController {

    private final SpaceBookmarkExportTaskAdminService exportTaskService;
    private final SpaceBookmarkPorterService porterService;

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
     * 导出书签为 Chrome 兼容的 Netscape Bookmark HTML 并直接下载
     *
     * @param scopeType 范围：all / folder / tag，默认 all
     * @param scopeId   scopeType 为 folder/tag 时必填
     */
    @GetMapping("/chrome")
    @SaCheckPermission("space:bookmark-export:edit")
    public void exportChrome(
            @RequestParam(value = "scopeType", required = false, defaultValue = "all") String scopeType,
            @RequestParam(value = "scopeId", required = false) Long scopeId,
            HttpServletResponse response
    ) throws IOException {
        String filename = "bookmarks_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + ".html";
        String encoded = URLEncoder.encode(filename, StandardCharsets.UTF_8).replace("+", "%20");
        response.setContentType("text/html; charset=UTF-8");
        // 同时给出 ASCII fallback 与 UTF-8 编码版本，兼容老浏览器
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + encoded);
        porterService.exportChromeHtml(scopeType, scopeId, response.getOutputStream());
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
