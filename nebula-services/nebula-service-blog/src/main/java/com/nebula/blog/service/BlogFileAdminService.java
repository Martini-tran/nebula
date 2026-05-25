package com.nebula.blog.service;

import com.nebula.blog.vo.admin.FileUploadVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 博客文件上传服务
 */
public interface BlogFileAdminService {

    /**
     * 上传文件到 OSS，并将元信息写入 blog_file_asset。
     *
     * @param file     上传的文件
     * @param fileType 文件用途（image / cover / attachment / other），传 null 默认 "image"
     * @return 上传结果（id、url、filename）
     */
    FileUploadVO upload(MultipartFile file, String fileType);
}
