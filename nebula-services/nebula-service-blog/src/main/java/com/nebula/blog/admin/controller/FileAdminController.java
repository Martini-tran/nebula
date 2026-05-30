package com.nebula.blog.admin.controller;

import cn.dev33.satoken.annotation.SaCheckPermission;
import com.nebula.blog.controller.AbstractAdminController;
import com.nebula.blog.service.BlogFileAdminService;
import com.nebula.blog.vo.admin.FileUploadVO;
import com.nebula.common.core.domain.R;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 后台文件上传控制器
 * <p>
 * 接收图片、封面等文件，上传至 OSS 后返回访问 URL 和文件资产 ID。
 */
@RestController
@RequestMapping("/admin/files")
@RequiredArgsConstructor
public class FileAdminController extends AbstractAdminController {

    private final BlogFileAdminService fileAdminService;

    /**
     * 上传文件
     *
     * @param file     MultipartFile，支持 image/jpeg、image/png、image/gif、image/webp 等
     * @param fileType 文件用途：image（默认）/ cover / attachment / other
     * @return 上传结果 {id, url, filename, fileType}
     */
    @PostMapping("/upload")
    @SaCheckPermission("blog:article:edit")
    public R<FileUploadVO> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "fileType", required = false, defaultValue = "image") String fileType
    ) {
        return R.success(fileAdminService.upload(file, fileType));
    }
}
