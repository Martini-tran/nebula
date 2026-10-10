package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckOr;
import cn.dev33.satoken.annotation.SaCheckPermission;
import cn.dev33.satoken.apikey.annotation.SaCheckApiKey;
import com.nebula.blog.dto.admin.PostAdminPageQuery;
import com.nebula.blog.dto.admin.PostCreateRequest;
import com.nebula.blog.dto.admin.PostPublishRequest;
import com.nebula.blog.dto.admin.PostStatusUpdateRequest;
import com.nebula.blog.dto.admin.PostUpdateRequest;
import com.nebula.blog.service.BlogPostAdminService;
import com.nebula.blog.service.BlogPostPublishService;
import com.nebula.blog.vo.admin.PostAdminVO;
import com.nebula.blog.vo.admin.PostPublishVO;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.domain.R;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import com.nebula.blog.controller.AbstractAdminController;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 后台文章管理控制器
 */
@RestController
@RequestMapping("/admin/articles")
@RequiredArgsConstructor
public class PostAdminController extends AbstractAdminController {

    private final BlogPostAdminService postAdminService;

    private final BlogPostPublishService postPublishService;

    /**
     * 分页查询文章
     */
    @GetMapping({"", "/page"})
    @SaCheckPermission("blog:article:list")
    public R<PageResult<PostAdminVO>> page(@ModelAttribute PostAdminPageQuery query) {
        return R.success(postAdminService.page(query));
    }

    /**
     * 获取文章详情
     */
    @GetMapping("/{id}")
    @SaCheckPermission("blog:article:query")
    public R<PostAdminVO> detail(@PathVariable Long id) {
        return R.success(postAdminService.detail(id));
    }

    /**
     * 创建文章
     */
    @PostMapping
    @SaCheckPermission("blog:article:add")
    public R<Long> create(@RequestBody @Valid PostCreateRequest req) {
        return R.success(postAdminService.create(req));
    }

    /**
     * 发布文章并可选挂到系列目录，按 slug 幂等（不存在则建、已存在则改）。
     * <p>供脚本等外部调用方使用：除登录态外，也接受带 {@code blog:article:publish} 授权范围的 API Key。
     * 两个注解要成对出现——{@code @SaCheckOr} 表达「登录有权限 或 API Key 有 scope」二选一，
     * {@code @SaCheckApiKey} 既是其中一支，也是 API Key 能访问本接口的唯一凭据（没标的接口 key 一律进不来）。
     */
    @PostMapping("/publish")
    @SaCheckOr(permission = @SaCheckPermission("blog:article:publish"), append = SaCheckApiKey.class)
    @SaCheckApiKey(scope = "blog:article:publish")
    public R<PostPublishVO> publish(@RequestBody @Valid PostPublishRequest req) {
        return R.success(postPublishService.publish(req));
    }

    /**
     * 更新文章
     */
    @PutMapping("/{id}")
    @SaCheckPermission("blog:article:edit")
    public R<Void> update(@PathVariable Long id, @RequestBody @Valid PostUpdateRequest req) {
        postAdminService.update(id, req);
        return R.success();
    }

    /**
     * 更新文章状态
     */
    @PutMapping("/{id}/status")
    @SaCheckPermission("blog:article:edit")
    public R<Void> updateStatus(@PathVariable Long id, @RequestBody @Valid PostStatusUpdateRequest req) {
        postAdminService.updateStatus(id, req);
        return R.success();
    }

    /**
     * 删除文章
     */
    @DeleteMapping("/{id}")
    @SaCheckPermission("blog:article:delete")
    public R<Void> delete(@PathVariable Long id) {
        postAdminService.delete(id);
        return R.success();
    }

    /**
     * 异步批量导入 Markdown 文件，每个文件创建一篇文章。
     * <p>立即返回导入任务 ID，后台逐文件处理；前端通过 {@code /admin/articles/import-tasks/{id}} 轮询进度与逐文件明细。
     *
     * @param files       上传的 .md / .markdown 文件（支持多文件 / 文件夹）
     * @param status      统一状态，默认 draft
     * @param visibility  统一可见性，默认 public
     * @param postType    内容类型，默认 article
     * @param categoryIds 统一关联分类，可选
     * @param rehostImages 是否下载正文外链图片转存到公开桶并替换，默认 true
     * @return 导入任务 ID
     */
    @PostMapping("/import")
    @SaCheckPermission("blog:article:add")
    public R<Long> importMarkdown(
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "status", required = false, defaultValue = "draft") String status,
            @RequestParam(value = "visibility", required = false, defaultValue = "public") String visibility,
            @RequestParam(value = "postType", required = false, defaultValue = "article") String postType,
            @RequestParam(value = "categoryIds", required = false) List<Long> categoryIds,
            @RequestParam(value = "rehostImages", required = false, defaultValue = "true") boolean rehostImages) {
        return R.success(postAdminService.importMarkdown(files, status, visibility, postType, categoryIds, rehostImages));
    }
}
