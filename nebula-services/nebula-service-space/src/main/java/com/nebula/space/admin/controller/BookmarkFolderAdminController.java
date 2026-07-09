package com.nebula.space.admin.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.common.core.domain.R;
import com.nebula.space.controller.AbstractAdminController;
import com.nebula.space.dto.admin.FolderCreateRequest;
import com.nebula.space.dto.admin.FolderMoveRequest;
import com.nebula.space.dto.admin.FolderUpdateRequest;
import com.nebula.space.service.SpaceBookmarkFolderAdminService;
import com.nebula.space.vo.admin.FolderAdminVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台书签目录管理控制器
 */
@RestController
@RequestMapping("/admin/bookmark-folders")
@SaCheckLogin
@RequiredArgsConstructor
public class BookmarkFolderAdminController extends AbstractAdminController {

    private final SpaceBookmarkFolderAdminService folderAdminService;

    /**
     * 查询当前用户的目录树
     */
    @GetMapping("/tree")
    @SaCheckPermission("space:folder:list")
    public R<List<FolderAdminVO>> tree() {
        return R.success(folderAdminService.tree());
    }

    /**
     * 查询目录详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("space:folder:query")
    public R<FolderAdminVO> detail(@PathVariable Long id) {
        return R.success(folderAdminService.detail(id));
    }

    /**
     * 创建目录
     */
    @PostMapping
    @SaCheckPermission("space:folder:add")
    public R<Long> create(@RequestBody @Valid FolderCreateRequest req) {
        return R.success(folderAdminService.create(req));
    }

    /**
     * 更新目录
     */
    @PutMapping("/{id}")
    @SaCheckPermission("space:folder:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid FolderUpdateRequest req) {
        folderAdminService.update(id, req);
        return R.success();
    }

    /**
     * 移动目录
     */
    @PutMapping("/{id}/move")
    @SaCheckPermission("space:folder:edit")
    public R<Void> move(@PathVariable Long id, @RequestBody @Valid FolderMoveRequest req) {
        folderAdminService.move(id, req);
        return R.success();
    }

    /**
     * 删除目录
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("space:folder:delete")
    public R<Void> delete(@PathVariable Long id) {
        folderAdminService.delete(id);
        return R.success();
    }
}
