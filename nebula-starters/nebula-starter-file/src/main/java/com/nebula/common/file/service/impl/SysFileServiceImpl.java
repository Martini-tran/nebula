package com.nebula.common.file.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.file.dto.FileBindRequest;
import com.nebula.common.file.dto.FilePageQuery;
import com.nebula.common.file.dto.FileUploadRequest;
import com.nebula.common.file.enums.FileResultCode;
import com.nebula.common.file.mapper.SysFileMapper;
import com.nebula.common.file.properties.FileProperties;
import com.nebula.common.file.service.SysFileService;
import com.nebula.common.file.vo.FileInfoVO;
import com.nebula.common.oss.api.ObjectStorageFactory;
import com.nebula.common.oss.api.ObjectStorageService;
import com.nebula.system.entity.SysFile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.List;

/**
 * 系统文件服务实现
 * <p>
 * 负责把对象存储能力（{@link ObjectStorageService}）和元数据持久化（sys_file 表）拼装到一起，
 * 对上层提供与具体存储无关的文件操作接口。
 *
 * @author nebula
 */
@Slf4j
@RequiredArgsConstructor
public class SysFileServiceImpl implements SysFileService {

    /**
     * 文件状态：正常
     */
    private static final int STATUS_NORMAL = 1;

    /**
     * 文件状态：已删除
     */
    private static final int STATUS_DELETED = 0;

    /**
     * 是否公开：是
     */
    private static final int PUBLIC_YES = 1;

    private final SysFileMapper sysFileMapper;
    private final ObjectStorageFactory storageFactory;
    private final FileProperties fileProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoVO upload(MultipartFile file, FileUploadRequest request) {
        if (file == null || file.isEmpty()) {
            throw new BizException(FileResultCode.FILE_EMPTY);
        }
        try {
            return doUpload(file.getBytes(), file.getOriginalFilename(), file.getContentType(),
                    file.getSize(), request);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("读取上传文件失败, filename={}", file.getOriginalFilename(), e);
            throw new BizException(FileResultCode.FILE_UPLOAD_FAILED);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfoVO upload(byte[] bytes, String originalFilename, String contentType,
                             FileUploadRequest request) {
        if (bytes == null || bytes.length == 0) {
            throw new BizException(FileResultCode.FILE_EMPTY);
        }
        return doUpload(bytes, originalFilename, contentType, bytes.length, request);
    }

    @Override
    public FileInfoVO getById(Long id) {
        SysFile entity = requireExisting(id);
        return toVO(entity, true);
    }

    @Override
    public SysFile getEntityById(Long id) {
        return sysFileMapper.selectById(id);
    }

    @Override
    public List<FileInfoVO> listByTarget(String targetType, Long targetId) {
        return listByTarget(targetType, targetId, null);
    }

    @Override
    public List<FileInfoVO> listByTarget(String targetType, Long targetId, String fileType) {
        if (!StringUtils.hasText(targetType) || targetId == null) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .eq(SysFile::getTargetType, targetType)
                .eq(SysFile::getTargetId, targetId)
                .eq(SysFile::getStatus, STATUS_NORMAL)
                .eq(StringUtils.hasText(fileType), SysFile::getFileType, fileType)
                .orderByAsc(SysFile::getSortOrder)
                .orderByAsc(SysFile::getCreateTime);
        List<SysFile> records = sysFileMapper.selectList(wrapper);
        return records.stream().map(e -> toVO(e, true)).toList();
    }

    @Override
    public PageResult<FileInfoVO> page(FilePageQuery query) {
        Page<SysFile> page = new Page<>(query.safePageNum(), query.safePageSize());
        LambdaQueryWrapper<SysFile> wrapper = new LambdaQueryWrapper<SysFile>()
                .eq(StringUtils.hasText(query.getTargetType()), SysFile::getTargetType, query.getTargetType())
                .eq(query.getTargetId() != null, SysFile::getTargetId, query.getTargetId())
                .eq(StringUtils.hasText(query.getFileType()), SysFile::getFileType, query.getFileType())
                .eq(StringUtils.hasText(query.getStorageType()), SysFile::getStorageType, query.getStorageType())
                .eq(query.getStatus() != null, SysFile::getStatus, query.getStatus())
                .eq(query.getCreateBy() != null, SysFile::getCreateBy, query.getCreateBy())
                .like(StringUtils.hasText(query.getOriginalFilename()),
                        SysFile::getOriginalFilename, query.getOriginalFilename())
                .orderByDesc(SysFile::getCreateTime);

        Page<SysFile> result = sysFileMapper.selectPage(page, wrapper);
        List<FileInfoVO> rows = result.getRecords().stream().map(e -> toVO(e, true)).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public String getAccessUrl(Long id) {
        SysFile entity = requireExisting(id);
        return resolveUrl(entity, fileProperties.getPresignedUrlExpirySeconds());
    }

    @Override
    public String getPresignedUrl(Long id, Integer expirySeconds) {
        SysFile entity = requireExisting(id);
        int seconds = clampExpiry(expirySeconds);
        ObjectStorageService oss = storageFactory.getServiceByBucket(entity.getBucket());
        try {
            return oss.getPresignedUrl(entity.getBucket(), entity.getObjectKey(), seconds);
        } catch (Exception e) {
            log.error("生成临时URL失败, fileId={}", id, e);
            throw new BizException(FileResultCode.PRESIGNED_URL_FAILED);
        }
    }

    @Override
    public List<FileInfoVO> listByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<SysFile> records = sysFileMapper.selectBatchIds(ids);
        return records.stream().map(e -> toVO(e, true)).toList();
    }

    @Override
    public InputStream download(Long id) {
        SysFile entity = requireExisting(id);
        ObjectStorageService oss = storageFactory.getServiceByBucket(entity.getBucket());
        try {
            return oss.getInputStream(entity.getBucket(), entity.getObjectKey());
        } catch (Exception e) {
            log.error("文件下载失败, fileId={}", id, e);
            throw new BizException(FileResultCode.FILE_DOWNLOAD_FAILED);
        }
    }

    @Override
    public void bind(Long id, FileBindRequest request) {
        if (request == null
                || !StringUtils.hasText(request.getTargetType())
                || request.getTargetId() == null) {
            throw new BizException(FileResultCode.FILE_PARAM_INVALID);
        }
        requireExisting(id);
        SysFile patch = new SysFile();
        patch.setId(id);
        patch.setTargetType(request.getTargetType());
        patch.setTargetId(request.getTargetId());
        sysFileMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindAll(List<Long> ids, FileBindRequest request) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if (request == null
                || !StringUtils.hasText(request.getTargetType())
                || request.getTargetId() == null) {
            throw new BizException(FileResultCode.FILE_PARAM_INVALID);
        }
        for (Long id : ids) {
            bind(id, request);
        }
    }

    @Override
    public void unbind(Long id) {
        requireExisting(id);
        SysFile patch = new SysFile();
        patch.setId(id);
        patch.setTargetType("");
        patch.setTargetId(0L);
        sysFileMapper.updateById(patch);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, boolean removeStorage) {
        SysFile entity = sysFileMapper.selectById(id);
        if (entity == null) {
            return;
        }
        SysFile patch = new SysFile();
        patch.setId(id);
        patch.setStatus(STATUS_DELETED);
        sysFileMapper.updateById(patch);

        if (removeStorage) {
            try {
                ObjectStorageService oss = storageFactory.getServiceByBucket(entity.getBucket());
                oss.delete(entity.getBucket(), entity.getObjectKey());
            } catch (Exception e) {
                log.warn("从对象存储删除失败, fileId={}, bucket={}, objectKey={}",
                        id, entity.getBucket(), entity.getObjectKey(), e);
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteAll(List<Long> ids, boolean removeStorage) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        for (Long id : ids) {
            delete(id, removeStorage);
        }
    }

    private FileInfoVO doUpload(byte[] bytes, String originalFilenameRaw, String contentTypeRaw,
                                long size, FileUploadRequest requestRaw) {
        FileUploadRequest request = requestRaw == null ? new FileUploadRequest() : requestRaw;
        validateSize(size);
        String contentType = StringUtils.hasText(contentTypeRaw)
                ? contentTypeRaw : "application/octet-stream";
        String originalFilename = StringUtils.hasText(originalFilenameRaw)
                ? originalFilenameRaw : "upload";
        String fileType = resolveFileType(request.getFileType(), contentType);
        validateMime(fileType, contentType);

        String bucket = StringUtils.hasText(request.getBucket())
                ? request.getBucket() : fileProperties.getDefaultBucket();
        if (!StringUtils.hasText(bucket)) {
            throw new BizException(FileResultCode.FILE_PARAM_INVALID, "未配置默认存储桶 nebula.file.default-bucket");
        }

        ObjectStorageService oss = storageFactory.getServiceByBucket(bucket);
        String prefix = StringUtils.hasText(request.getPrefix())
                ? request.getPrefix() : fileType;
        String objectKey = oss.generateObjectKey(originalFilename, prefix);
        String extension = extractExtension(originalFilename);
        Integer isPublic = request.getIsPublic() == null ? PUBLIC_YES : request.getIsPublic();

        // 上传到对象存储
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            oss.upload(bucket, in, objectKey, contentType, size);
        } catch (Exception e) {
            log.error("文件上传到对象存储失败, bucket={}, objectKey={}", bucket, objectKey, e);
            throw new BizException(FileResultCode.FILE_UPLOAD_FAILED);
        }

        // 落库
        SysFile entity = new SysFile();
        entity.setTargetType(StringUtils.hasText(request.getTargetType()) ? request.getTargetType() : "");
        entity.setTargetId(request.getTargetId() == null ? 0L : request.getTargetId());
        entity.setFileType(fileType);
        entity.setStorageType(storageFactory.getSupportedStorageType().getCode());
        entity.setBucket(bucket);
        entity.setObjectKey(objectKey);
        entity.setOriginalFilename(originalFilename);
        entity.setStoredFilename(extractStoredFilename(objectKey));
        entity.setExtension(extension);
        entity.setMimeType(contentType);
        entity.setSizeBytes(size);
        entity.setHashSha256(sha256(bytes));
        entity.setIsPublic(isPublic);
        entity.setStatus(STATUS_NORMAL);
        entity.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        // 公开文件直接保存稳定URL，私有文件URL字段留空，由 getAccessUrl 动态生成签名URL
        if (isPublic == PUBLIC_YES) {
            try {
                entity.setUrl(oss.getUrl(bucket, objectKey));
            } catch (Exception e) {
                log.warn("生成公开URL失败, bucket={}, objectKey={}", bucket, objectKey, e);
            }
        }
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            entity.setCreateBy(currentUserId);
        }

        try {
            sysFileMapper.insert(entity);
        } catch (Exception e) {
            log.error("文件元数据落库失败, bucket={}, objectKey={}", bucket, objectKey, e);
            // 落库失败时尝试清理已上传的对象，避免脏数据
            try {
                oss.delete(bucket, objectKey);
            } catch (Exception ignored) {
            }
            throw new BizException(FileResultCode.FILE_UPLOAD_FAILED);
        }

        return toVO(entity, true);
    }

    private SysFile requireExisting(Long id) {
        if (id == null) {
            throw new BizException(FileResultCode.FILE_PARAM_INVALID);
        }
        SysFile entity = sysFileMapper.selectById(id);
        if (entity == null) {
            throw new BizException(FileResultCode.FILE_NOT_FOUND);
        }
        if (entity.getStatus() != null && entity.getStatus() == STATUS_DELETED) {
            throw new BizException(FileResultCode.FILE_DELETED);
        }
        return entity;
    }

    private FileInfoVO toVO(SysFile entity, boolean withUrl) {
        FileInfoVO vo = new FileInfoVO();
        BeanUtils.copyProperties(entity, vo);
        if (withUrl) {
            vo.setUrl(resolveUrl(entity, fileProperties.getPresignedUrlExpirySeconds()));
        }
        return vo;
    }

    private String resolveUrl(SysFile entity, int presignedExpirySeconds) {
        if (entity.getIsPublic() != null && entity.getIsPublic() == PUBLIC_YES
                && StringUtils.hasText(entity.getUrl())) {
            return entity.getUrl();
        }
        try {
            ObjectStorageService oss = storageFactory.getServiceByBucket(entity.getBucket());
            return oss.getPresignedUrl(entity.getBucket(), entity.getObjectKey(),
                    clampExpiry(presignedExpirySeconds));
        } catch (Exception e) {
            log.warn("解析文件访问URL失败, fileId={}", entity.getId(), e);
            return entity.getUrl();
        }
    }

    private int clampExpiry(Integer seconds) {
        int max = fileProperties.getMaxPresignedUrlExpirySeconds();
        int defaultSeconds = fileProperties.getPresignedUrlExpirySeconds();
        if (seconds == null || seconds <= 0) {
            return defaultSeconds;
        }
        return Math.min(seconds, max);
    }

    private void validateSize(long size) {
        if (size > fileProperties.getMaxFileSize()) {
            long limitMb = fileProperties.getMaxFileSize() / 1024 / 1024;
            throw new BizException(FileResultCode.FILE_SIZE_EXCEEDED,
                    "文件大小不能超过 " + limitMb + "MB");
        }
    }

    private void validateMime(String fileType, String contentType) {
        if (!fileProperties.isStrictMimeCheck()) {
            return;
        }
        if (("image".equalsIgnoreCase(fileType) || "cover".equalsIgnoreCase(fileType)
                || "avatar".equalsIgnoreCase(fileType) || "logo".equalsIgnoreCase(fileType))
                && !fileProperties.getAllowedImageMimeTypes().contains(contentType)) {
            throw new BizException(FileResultCode.FILE_TYPE_NOT_ALLOWED, "不支持的图片类型: " + contentType);
        }
        if ("video".equalsIgnoreCase(fileType)
                && !fileProperties.getAllowedVideoMimeTypes().contains(contentType)) {
            throw new BizException(FileResultCode.FILE_TYPE_NOT_ALLOWED, "不支持的视频类型: " + contentType);
        }
    }

    private String resolveFileType(String fileType, String contentType) {
        if (StringUtils.hasText(fileType)) {
            return fileType;
        }
        if (contentType == null) {
            return "other";
        }
        if (contentType.startsWith("image/")) {
            return "image";
        }
        if (contentType.startsWith("video/")) {
            return "video";
        }
        if (contentType.startsWith("audio/")) {
            return "audio";
        }
        return "attachment";
    }

    private String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return null;
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return null;
        }
        return filename.substring(dot + 1);
    }

    private String extractStoredFilename(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return null;
        }
        int slash = objectKey.lastIndexOf('/');
        return slash < 0 ? objectKey : objectKey.substring(slash + 1);
    }

    private String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder sb = new StringBuilder(64);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm unavailable", e);
        }
    }
}
