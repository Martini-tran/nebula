package com.nebula.space.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import com.nebula.space.controller.AbstractAdminController;
import com.nebula.space.dto.admin.BookmarkAdminPageQuery;
import com.nebula.space.dto.admin.BookmarkAiSuggestRequest;
import com.nebula.space.dto.admin.BookmarkBatchDeleteRequest;
import com.nebula.space.dto.admin.BookmarkCreateRequest;
import com.nebula.space.dto.admin.BookmarkLinkCheckRequest;
import com.nebula.space.dto.admin.BookmarkMoveRequest;
import com.nebula.space.dto.admin.BookmarkStatusUpdateRequest;
import com.nebula.space.dto.admin.BookmarkTagBindRequest;
import com.nebula.space.dto.admin.BookmarkUpdateRequest;
import com.nebula.space.service.SpaceBookmarkAdminService;
import com.nebula.space.service.SpaceBookmarkAiService;
import com.nebula.space.service.SpaceBookmarkLinkCheckService;
import com.nebula.space.vo.admin.BookmarkAdminVO;
import com.nebula.space.vo.admin.BookmarkAiSuggestionVO;
import com.nebula.space.vo.admin.BookmarkLinkCheckVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台书签管理控制器
 */
@RestController
@RequestMapping("/admin/bookmarks")
@SaCheckLogin
@RequiredArgsConstructor
public class BookmarkAdminController extends AbstractAdminController {

    private final SpaceBookmarkAdminService bookmarkAdminService;
    private final SpaceBookmarkLinkCheckService linkCheckService;
    private final SpaceBookmarkAiService aiService;

    /**
     * 分页查询书签
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("space:bookmark:list")
    public R<PageResult<BookmarkAdminVO>> page(@ModelAttribute BookmarkAdminPageQuery query) {
        return R.success(bookmarkAdminService.page(query));
    }

    /**
     * 同一个网址是否已收藏（按规范化网址），有就返回那一条，没有返回空
     */
    @GetMapping("/duplicate")
    @SaCheckPermission("space:bookmark:list")
    public R<BookmarkAdminVO> duplicate(@RequestParam String url, @RequestParam(required = false) Long excludeId) {
        return R.success(bookmarkAdminService.findDuplicate(url, excludeId));
    }

    /**
     * 打开了一次书签（访问次数 +1）
     */
    @PostMapping("/{id}/visit")
    @SaCheckPermission("space:bookmark:list")
    public R<Void> visit(@PathVariable Long id) {
        bookmarkAdminService.recordVisit(id);
        return R.success();
    }

    /**
     * 抓网页补全标题与描述（只补自动填的标题与空描述，不覆盖手写的）
     */
    @PostMapping("/{id}/meta")
    @SaCheckPermission("space:bookmark:edit")
    public R<BookmarkAdminVO> fillMeta(@PathVariable Long id) {
        return R.success(bookmarkAdminService.fillMeta(id));
    }

    /**
     * 查询书签详情（含标签）
     */
    @GetMapping("/{id}")
    @SaCheckPermission("space:bookmark:query")
    public R<BookmarkAdminVO> detail(@PathVariable Long id) {
        return R.success(bookmarkAdminService.detail(id));
    }

    /**
     * 创建书签
     */
    @PostMapping
    @SaCheckPermission("space:bookmark:add")
    public R<Long> create(@RequestBody @Valid BookmarkCreateRequest req) {
        return R.success(bookmarkAdminService.create(req));
    }

    /**
     * 更新书签
     */
    @PutMapping("/{id}")
    @SaCheckPermission("space:bookmark:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid BookmarkUpdateRequest req) {
        bookmarkAdminService.update(id, req);
        return R.success();
    }

    /**
     * 更新书签状态（归档/恢复/失效）
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("space:bookmark:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid BookmarkStatusUpdateRequest req) {
        bookmarkAdminService.updateStatus(id, req);
        return R.success();
    }

    /**
     * 替换书签关联的标签
     */
    @PutMapping("/{id}/tags")
    @SaCheckPermission("space:bookmark:edit")
    public R<Void> bindTags(@PathVariable Long id, @RequestBody BookmarkTagBindRequest req) {
        bookmarkAdminService.bindTags(id, req);
        return R.success();
    }

    /**
     * 批量移动书签到目标目录
     */
    @PostMapping("/move")
    @SaCheckPermission("space:bookmark:edit")
    public R<Integer> move(@RequestBody @Valid BookmarkMoveRequest req) {
        return R.success(bookmarkAdminService.move(req));
    }

    /**
     * 批量删除书签
     */
    @PostMapping("/batch-delete")
    @SaCheckPermission("space:bookmark:delete")
    public R<Integer> batchDelete(@RequestBody @Valid BookmarkBatchDeleteRequest req) {
        return R.success(bookmarkAdminService.batchDelete(req));
    }

    /**
     * 删除书签
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("space:bookmark:delete")
    public R<Void> delete(@PathVariable Long id) {
        bookmarkAdminService.delete(id);
        return R.success();
    }

    /**
     * 检查一批书签的链接，打不开的标为失效、失效的又能打开时恢复正常
     */
    @PostMapping("/link-check")
    @SaCheckPermission("space:bookmark:edit")
    public R<List<BookmarkLinkCheckVO>> checkLinks(@RequestBody @Valid BookmarkLinkCheckRequest req) {
        return R.success(linkCheckService.check(req));
    }

    /**
     * AI 整理建议：归目录、打标签、改标题、补描述（只出建议，不改数据）
     */
    @PostMapping("/ai-suggest")
    @SaCheckPermission("space:bookmark:edit")
    public R<List<BookmarkAiSuggestionVO>> aiSuggest(@RequestBody @Valid BookmarkAiSuggestRequest req) {
        return R.success(aiService.suggest(req));
    }
}
