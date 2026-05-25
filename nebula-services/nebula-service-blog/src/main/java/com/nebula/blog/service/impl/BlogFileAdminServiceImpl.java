package com.nebula.blog.service.impl;

import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.service.BlogFileAdminService;
import com.nebula.blog.vo.admin.FileUploadVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Set;

/**
 * 博客文件上传服务实现
 * <p>
 * 接收 MultipartFile → 上传至 MinIO → 写 blog_file_asset → 返回 {id, url}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogFileAdminServiceImpl implements BlogFileAdminService {

    private static final String OSS_STORAGE_TYPE = "oss";
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024; // 10MB

    /**
     * 允许的图片 MIME 类型
     */
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/gif", "image/webp",
            "image/svg+xml", "image/bmp", "image/tiff"
    );

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;
    private final BlogFileAssetMapper fileAssetMapper;

    @Value("${nebula.minio.default-bucket}")
    private String defaultBucket;

    @Override
    public FileUploadVO upload(MultipartFile file, String fileType) {
        // ---- 基础校验 ----
        if (file == null || file.isEmpty()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "上传文件不能为空");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BizException(HttpStatus.BAD_REQUEST, "文件大小不能超过 10MB");
        }
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType) || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "不支持的文件类型：" + contentType);
        }

        String resolvedFileType = StringUtils.hasText(fileType) ? fileType : "image";
        String originalFilename = StringUtils.hasText(file.getOriginalFilename())
                ? file.getOriginalFilename()
                : "upload";

        // ---- 生成 objectKey 并上传 ----
        String prefix = "cover".equals(resolvedFileType) ? "covers" : "images";
        String objectKey = ossService.generateObjectKey(originalFilename, prefix);

        String url;
        byte[] bytes;
        try {
            bytes = file.getBytes();
            url = ossService.upload(defaultBucket, file.getInputStream(),
                    objectKey, contentType, file.getSize());
        } catch (Exception e) {
            log.error("Failed to upload file to OSS, filename={}", originalFilename, e);
            throw new BizException(HttpStatus.INTERNAL_SERVER_ERROR, "文件上传失败，请重试");
        }

        // ---- 提取扩展名 ----
        String extension = "";
        int dotIndex = originalFilename.lastIndexOf('.');
        if (dotIndex >= 0) {
            extension = originalFilename.substring(dotIndex);
        }

        // ---- 写 blog_file_asset ----
        BlogFileAsset asset = new BlogFileAsset();
        asset.setStorageType(OSS_STORAGE_TYPE);
        asset.setBucket(defaultBucket);
        asset.setObjectKey(objectKey);
        asset.setUrl(url);
        asset.setFilename(originalFilename);
        asset.setExtension(extension);
        asset.setMimeType(contentType);
        asset.setSizeBytes(file.getSize());
        asset.setHashSha256(sha256(bytes));
        asset.setFileType(resolvedFileType);
        fileAssetMapper.insert(asset);

        // ---- 组装返回 ----
        FileUploadVO vo = new FileUploadVO();
        vo.setId(asset.getId());
        vo.setUrl(url);
        vo.setFilename(originalFilename);
        vo.setFileType(resolvedFileType);
        return vo;
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
