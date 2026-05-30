package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.SeriesAdminPageQuery;
import com.nebula.blog.dto.admin.SeriesCreateRequest;
import com.nebula.blog.dto.admin.SeriesUpdateRequest;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.entity.BlogSeries;
import com.nebula.blog.entity.BlogSeriesCatalog;
import com.nebula.blog.entity.BlogSeriesCatalogPost;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogMapper;
import com.nebula.blog.mapper.BlogSeriesCatalogPostMapper;
import com.nebula.blog.mapper.BlogSeriesMapper;
import com.nebula.blog.service.BlogSeriesAdminService;
import com.nebula.blog.vo.admin.SeriesAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 博客系列管理服务实现（管理员端）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogSeriesAdminServiceImpl implements BlogSeriesAdminService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String VISIBILITY_PRIVATE = "private";
    private static final String OSS_STORAGE_TYPE = "oss";

    private static final int NAME_MAX_LENGTH = 100;
    private static final int SLUG_MAX_LENGTH = 120;
    private static final int DESCRIPTION_MAX_LENGTH = 500;

    private static final Set<String> SERIES_STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_ARCHIVED);
    private static final Set<String> SERIES_VISIBILITIES = Set.of(VISIBILITY_PUBLIC, VISIBILITY_PRIVATE);

    private final BlogSeriesMapper seriesMapper;
    private final BlogSeriesCatalogMapper catalogMapper;
    private final BlogSeriesCatalogPostMapper catalogPostMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    @Override
    public PageResult<SeriesAdminVO> page(SeriesAdminPageQuery query) {
        SeriesAdminPageQuery safe = query == null ? new SeriesAdminPageQuery() : query;

        Page<BlogSeries> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<BlogSeries> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(BlogSeries::getName, safe.getKeyword())
                        .or().like(BlogSeries::getSlug, safe.getKeyword())
                        .or().like(BlogSeries::getDescription, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getStatus()), BlogSeries::getStatus, safe.getStatus())
                .eq(StringUtils.hasText(safe.getVisibility()), BlogSeries::getVisibility, safe.getVisibility())
                .eq(safe.getIsFinished() != null, BlogSeries::getIsFinished, safe.getIsFinished())
                .eq(safe.getCreateBy() != null, BlogSeries::getCreateBy, safe.getCreateBy())
                .orderByAsc(BlogSeries::getSortOrder)
                .orderByDesc(BlogSeries::getCreateTime);

        Page<BlogSeries> result = seriesMapper.selectPage(page, wrapper);
        List<SeriesAdminVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public SeriesAdminVO detail(Long id) {
        return toVO(requireSeries(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(SeriesCreateRequest req) {
        validateCreate(req);
        checkSlugUnique(req.getSlug(), null);

        BlogSeries series = new BlogSeries();
        series.setName(req.getName());
        series.setSlug(req.getSlug());
        series.setDescription(req.getDescription());
        if (req.getCoverFileId() != null) {
            validateFileExists(req.getCoverFileId(), "coverFileId");
            series.setCoverFileId(req.getCoverFileId());
        }
        series.setStatus(normalizeValue(req.getStatus(), STATUS_DRAFT, SERIES_STATUSES, "status"));
        series.setVisibility(normalizeValue(req.getVisibility(), VISIBILITY_PUBLIC, SERIES_VISIBILITIES, "visibility"));
        series.setIsFinished(req.getIsFinished() != null ? req.getIsFinished() : Boolean.FALSE);
        series.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        series.setCreateBy(resolveCurrentUserId());
        seriesMapper.insert(series);
        return series.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SeriesUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        BlogSeries existing = requireSeries(id);

        if (StringUtils.hasText(req.getName())) {
            checkLength(req.getName(), NAME_MAX_LENGTH, "name");
            existing.setName(req.getName());
        }
        if (StringUtils.hasText(req.getSlug()) && !req.getSlug().equals(existing.getSlug())) {
            checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
            checkSlugUnique(req.getSlug(), id);
            existing.setSlug(req.getSlug());
        }
        if (req.getDescription() != null) {
            checkLength(req.getDescription(), DESCRIPTION_MAX_LENGTH, "description");
            existing.setDescription(req.getDescription());
        }
        boolean clearCover = Boolean.TRUE.equals(req.getClearCoverFileId());
        if (clearCover) {
            existing.setCoverFileId(null);
        } else if (req.getCoverFileId() != null) {
            validateFileExists(req.getCoverFileId(), "coverFileId");
            existing.setCoverFileId(req.getCoverFileId());
        }
        if (StringUtils.hasText(req.getStatus())) {
            existing.setStatus(normalizeValue(req.getStatus(), null, SERIES_STATUSES, "status"));
        }
        if (StringUtils.hasText(req.getVisibility())) {
            existing.setVisibility(normalizeValue(req.getVisibility(), null, SERIES_VISIBILITIES, "visibility"));
        }
        if (req.getIsFinished() != null) {
            existing.setIsFinished(req.getIsFinished());
        }
        if (req.getSortOrder() != null) {
            existing.setSortOrder(req.getSortOrder());
        }

        if (clearCover) {
            LambdaUpdateWrapper<BlogSeries> updateWrapper = new LambdaUpdateWrapper<BlogSeries>()
                    .eq(BlogSeries::getId, id)
                    .set(BlogSeries::getCoverFileId, null);
            seriesMapper.update(existing, updateWrapper);
        } else {
            seriesMapper.updateById(existing);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireSeries(id);
        // 数据库已配置 ON DELETE CASCADE，这里仍显式清理以保证未启用 FK 时正确
        List<BlogSeriesCatalog> catalogs = catalogMapper.selectList(
                new LambdaQueryWrapper<BlogSeriesCatalog>().eq(BlogSeriesCatalog::getSeriesId, id));
        if (!catalogs.isEmpty()) {
            List<Long> catalogIds = catalogs.stream().map(BlogSeriesCatalog::getId).toList();
            catalogPostMapper.delete(new LambdaQueryWrapper<BlogSeriesCatalogPost>()
                    .in(BlogSeriesCatalogPost::getCatalogId, catalogIds));
            catalogMapper.delete(new LambdaQueryWrapper<BlogSeriesCatalog>()
                    .eq(BlogSeriesCatalog::getSeriesId, id));
        }
        seriesMapper.deleteById(id);
    }

    // -------------------- 校验工具 --------------------

    private void validateCreate(SeriesCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "系列名称不能为空");
        }
        if (!StringUtils.hasText(req.getSlug())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "系列 slug 不能为空");
        }
        checkLength(req.getName(), NAME_MAX_LENGTH, "name");
        checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
        checkLength(req.getDescription(), DESCRIPTION_MAX_LENGTH, "description");
    }

    private BlogSeries requireSeries(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "系列ID不能为空");
        }
        BlogSeries series = seriesMapper.selectById(id);
        if (series == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "系列不存在");
        }
        return series;
    }

    private Long resolveCurrentUserId() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "创建者不能为空");
        }
        return currentUserId;
    }

    private void checkSlugUnique(String slug, Long excludeId) {
        LambdaQueryWrapper<BlogSeries> wrapper = new LambdaQueryWrapper<BlogSeries>()
                .eq(BlogSeries::getSlug, slug);
        if (excludeId != null) {
            wrapper.ne(BlogSeries::getId, excludeId);
        }
        if (seriesMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "slug 已存在");
        }
    }

    private void checkLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 长度不能超过 " + maxLength);
        }
    }

    private void validateFileExists(Long fileId, String fieldName) {
        if (fileId != null && fileAssetMapper.selectById(fileId) == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 不存在");
        }
    }

    private String normalizeValue(String value, String fallback, Set<String> allowed, String fieldName) {
        String normalized = StringUtils.hasText(value) ? value : fallback;
        if (!StringUtils.hasText(normalized)) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 不能为空");
        }
        if (!allowed.contains(normalized)) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 非法");
        }
        return normalized;
    }

    // -------------------- VO 转换 --------------------

    private SeriesAdminVO toVO(BlogSeries entity) {
        SeriesAdminVO vo = new SeriesAdminVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setSlug(entity.getSlug());
        vo.setDescription(entity.getDescription());
        vo.setCoverFileId(entity.getCoverFileId());
        vo.setStatus(entity.getStatus());
        vo.setVisibility(entity.getVisibility());
        vo.setIsFinished(entity.getIsFinished());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreateBy(entity.getCreateBy());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());

        if (entity.getCoverFileId() != null) {
            BlogFileAsset cover = fileAssetMapper.selectById(entity.getCoverFileId());
            if (cover != null) {
                vo.setCoverUrl(resolveFileUrl(cover));
            }
        }
        return vo;
    }

    /**
     * 将文件资产解析为可访问 URL（OSS 私有桶通过预签名访问）。
     */
    private String resolveFileUrl(BlogFileAsset asset) {
        if (asset == null) {
            return null;
        }
        if (OSS_STORAGE_TYPE.equals(asset.getStorageType())
                && StringUtils.hasText(asset.getBucket())
                && StringUtils.hasText(asset.getObjectKey())) {
            try {
                return ossService.getPresignedUrl(asset.getBucket(), asset.getObjectKey(),
                        presignedUrlExpirySeconds);
            } catch (Exception e) {
                log.warn("Failed to generate presigned URL, bucket={}, key={}",
                        asset.getBucket(), asset.getObjectKey(), e);
            }
        }
        return asset.getUrl();
    }
}
