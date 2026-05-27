package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.TravelTripAdminPageQuery;
import com.nebula.blog.dto.admin.TravelTripCreateRequest;
import com.nebula.blog.dto.admin.TravelTripPostBindRequest;
import com.nebula.blog.dto.admin.TravelTripStatusUpdateRequest;
import com.nebula.blog.dto.admin.TravelTripUpdateRequest;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.entity.BlogPost;
import com.nebula.blog.entity.TravelCheckin;
import com.nebula.blog.entity.TravelTrip;
import com.nebula.blog.entity.TravelTripBlogPost;
import com.nebula.blog.entity.TravelTripDay;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.mapper.BlogPostMapper;
import com.nebula.blog.mapper.TravelCheckinMapper;
import com.nebula.blog.mapper.TravelTripBlogPostMapper;
import com.nebula.blog.mapper.TravelTripDayMapper;
import com.nebula.blog.mapper.TravelTripMapper;
import com.nebula.blog.service.TravelTripAdminService;
import com.nebula.blog.vo.admin.TravelTripAdminVO;
import com.nebula.blog.vo.admin.TravelTripPostVO;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 游记管理服务实现（管理员端）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelTripAdminServiceImpl implements TravelTripAdminService {

    private static final String STATUS_DRAFT = "draft";
    private static final String STATUS_PUBLISHED = "published";
    private static final String STATUS_ARCHIVED = "archived";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String VISIBILITY_PRIVATE = "private";
    private static final String OSS_STORAGE_TYPE = "oss";

    private static final Set<String> TRIP_STATUSES = Set.of(STATUS_DRAFT, STATUS_PUBLISHED, STATUS_ARCHIVED);
    private static final Set<String> TRIP_VISIBILITIES = Set.of(VISIBILITY_PUBLIC, VISIBILITY_PRIVATE);

    private static final int POST_TYPE_PRIMARY = 0;
    private static final int POST_TYPE_RELATED = 1;

    private static final int TITLE_MAX_LENGTH = 200;
    private static final int SLUG_MAX_LENGTH = 200;
    private static final int SUMMARY_MAX_LENGTH = 500;
    private static final int CURRENCY_MAX_LENGTH = 16;

    private final TravelTripMapper tripMapper;
    private final TravelTripDayMapper tripDayMapper;
    private final TravelCheckinMapper checkinMapper;
    private final TravelTripBlogPostMapper tripBlogPostMapper;
    private final BlogFileAssetMapper fileAssetMapper;
    private final BlogPostMapper postMapper;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    @Override
    public PageResult<TravelTripAdminVO> page(TravelTripAdminPageQuery query) {
        TravelTripAdminPageQuery safe = query == null ? new TravelTripAdminPageQuery() : query;

        Page<TravelTrip> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<TravelTrip> wrapper = new LambdaQueryWrapper<>();
        wrapper.and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(TravelTrip::getTitle, safe.getKeyword())
                        .or().like(TravelTrip::getSlug, safe.getKeyword())
                        .or().like(TravelTrip::getSummary, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getStatus()), TravelTrip::getStatus, safe.getStatus())
                .eq(StringUtils.hasText(safe.getVisibility()), TravelTrip::getVisibility, safe.getVisibility())
                .eq(safe.getUserId() != null, TravelTrip::getUserId, safe.getUserId())
                .ge(safe.getStartDateFrom() != null, TravelTrip::getStartDate, safe.getStartDateFrom())
                .le(safe.getStartDateTo() != null, TravelTrip::getStartDate, safe.getStartDateTo())
                .orderByDesc(TravelTrip::getCreateTime);

        Page<TravelTrip> result = tripMapper.selectPage(page, wrapper);
        List<TravelTripAdminVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public TravelTripAdminVO detail(Long id) {
        return toVO(requireTrip(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TravelTripCreateRequest req) {
        validateCreate(req);
        checkSlugUnique(req.getSlug(), null);

        if (req.getCoverFileId() != null) {
            validateFileExists(req.getCoverFileId());
        }
        if (req.getStartDate() != null && req.getEndDate() != null
                && req.getEndDate().isBefore(req.getStartDate())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "endDate 不能早于 startDate");
        }

        TravelTrip trip = new TravelTrip();
        trip.setUserId(resolveCurrentUserId());
        trip.setTitle(req.getTitle());
        trip.setSlug(req.getSlug());
        trip.setSummary(req.getSummary());
        trip.setCoverFileId(req.getCoverFileId());
        trip.setStatus(normalizeValue(req.getStatus(), STATUS_DRAFT, TRIP_STATUSES, "status"));
        trip.setVisibility(normalizeValue(req.getVisibility(), VISIBILITY_PUBLIC, TRIP_VISIBILITIES, "visibility"));
        trip.setStartDate(req.getStartDate());
        trip.setEndDate(req.getEndDate());
        trip.setDaysCount(calcDaysCount(req.getStartDate(), req.getEndDate()));
        trip.setPersons(req.getPersons());
        trip.setCostTotal(req.getCostTotal());
        trip.setCostCurrency(StringUtils.hasText(req.getCostCurrency()) ? req.getCostCurrency() : "CNY");
        trip.setViewCount(0);
        trip.setLikeCount(0);
        if (STATUS_PUBLISHED.equals(trip.getStatus())) {
            trip.setPublishedAt(LocalDateTime.now());
        }
        tripMapper.insert(trip);
        return trip.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TravelTripUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        TravelTrip existing = requireTrip(id);

        if (StringUtils.hasText(req.getTitle())) {
            checkLength(req.getTitle(), TITLE_MAX_LENGTH, "title");
            existing.setTitle(req.getTitle());
        }
        if (StringUtils.hasText(req.getSlug()) && !req.getSlug().equals(existing.getSlug())) {
            checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
            checkSlugUnique(req.getSlug(), id);
            existing.setSlug(req.getSlug());
        }
        if (req.getSummary() != null) {
            checkLength(req.getSummary(), SUMMARY_MAX_LENGTH, "summary");
            existing.setSummary(req.getSummary());
        }
        if (StringUtils.hasText(req.getStatus())) {
            String next = normalizeValue(req.getStatus(), null, TRIP_STATUSES, "status");
            if (STATUS_PUBLISHED.equals(next) && !STATUS_PUBLISHED.equals(existing.getStatus())
                    && existing.getPublishedAt() == null) {
                existing.setPublishedAt(LocalDateTime.now());
            }
            existing.setStatus(next);
        }
        if (StringUtils.hasText(req.getVisibility())) {
            existing.setVisibility(normalizeValue(req.getVisibility(), null, TRIP_VISIBILITIES, "visibility"));
        }

        LocalDate startDate = req.getStartDate() != null ? req.getStartDate() : existing.getStartDate();
        LocalDate endDate = req.getEndDate() != null ? req.getEndDate() : existing.getEndDate();
        if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "endDate 不能早于 startDate");
        }
        if (req.getStartDate() != null) existing.setStartDate(req.getStartDate());
        if (req.getEndDate() != null) existing.setEndDate(req.getEndDate());
        if (req.getStartDate() != null || req.getEndDate() != null) {
            existing.setDaysCount(calcDaysCount(existing.getStartDate(), existing.getEndDate()));
        }

        if (req.getPersons() != null) existing.setPersons(req.getPersons());
        if (req.getCostTotal() != null) existing.setCostTotal(req.getCostTotal());
        if (StringUtils.hasText(req.getCostCurrency())) {
            checkLength(req.getCostCurrency(), CURRENCY_MAX_LENGTH, "costCurrency");
            existing.setCostCurrency(req.getCostCurrency());
        }

        boolean clearCover = Boolean.TRUE.equals(req.getClearCoverFileId());
        if (clearCover) {
            existing.setCoverFileId(null);
            LambdaUpdateWrapper<TravelTrip> uw = new LambdaUpdateWrapper<TravelTrip>()
                    .eq(TravelTrip::getId, id)
                    .set(TravelTrip::getCoverFileId, null);
            tripMapper.update(existing, uw);
            return;
        } else if (req.getCoverFileId() != null) {
            validateFileExists(req.getCoverFileId());
            existing.setCoverFileId(req.getCoverFileId());
        }
        tripMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, TravelTripStatusUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        TravelTrip existing = requireTrip(id);
        boolean changed = false;
        if (StringUtils.hasText(req.getStatus())) {
            String next = normalizeValue(req.getStatus(), null, TRIP_STATUSES, "status");
            if (STATUS_PUBLISHED.equals(next) && !STATUS_PUBLISHED.equals(existing.getStatus())) {
                existing.setPublishedAt(req.getPublishedAt() != null ? req.getPublishedAt() : LocalDateTime.now());
            } else if (req.getPublishedAt() != null) {
                existing.setPublishedAt(req.getPublishedAt());
            }
            existing.setStatus(next);
            changed = true;
        } else if (req.getPublishedAt() != null) {
            existing.setPublishedAt(req.getPublishedAt());
            changed = true;
        }
        if (StringUtils.hasText(req.getVisibility())) {
            existing.setVisibility(normalizeValue(req.getVisibility(), null, TRIP_VISIBILITIES, "visibility"));
            changed = true;
        }
        if (changed) {
            tripMapper.updateById(existing);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTrip(id);
        // 1. 找出所有 trip_day -> 清掉其下 checkin
        List<TravelTripDay> days = tripDayMapper.selectList(
                new LambdaQueryWrapper<TravelTripDay>().eq(TravelTripDay::getTripId, id));
        if (!days.isEmpty()) {
            List<Long> dayIds = days.stream().map(TravelTripDay::getId).toList();
            checkinMapper.delete(new LambdaQueryWrapper<TravelCheckin>()
                    .in(TravelCheckin::getTripDayId, dayIds));
            tripDayMapper.delete(new LambdaQueryWrapper<TravelTripDay>()
                    .eq(TravelTripDay::getTripId, id));
        }
        // 2. 清掉文章关联
        tripBlogPostMapper.delete(new LambdaQueryWrapper<TravelTripBlogPost>()
                .eq(TravelTripBlogPost::getTripId, id));
        // 3. 删除游记主体
        tripMapper.deleteById(id);
    }

    @Override
    public List<TravelTripPostVO> listPosts(Long tripId) {
        requireTrip(tripId);
        List<TravelTripBlogPost> rels = tripBlogPostMapper.selectList(
                new LambdaQueryWrapper<TravelTripBlogPost>()
                        .eq(TravelTripBlogPost::getTripId, tripId)
                        .orderByAsc(TravelTripBlogPost::getPostType)
                        .orderByAsc(TravelTripBlogPost::getCreateTime));
        if (rels.isEmpty()) {
            return List.of();
        }
        List<Long> postIds = rels.stream().map(TravelTripBlogPost::getPostId).toList();
        Map<Long, BlogPost> postMap = postMapper.selectBatchIds(postIds).stream()
                .collect(Collectors.toMap(BlogPost::getId, p -> p));
        return rels.stream().map(rel -> {
            TravelTripPostVO vo = new TravelTripPostVO();
            vo.setTripId(rel.getTripId());
            vo.setPostId(rel.getPostId());
            vo.setPostType(rel.getPostType());
            vo.setCreateTime(rel.getCreateTime());
            BlogPost post = postMap.get(rel.getPostId());
            if (post != null) {
                vo.setPostTitle(post.getTitle());
                vo.setPostSlug(post.getSlug());
                vo.setPostStatus(post.getStatus());
            }
            return vo;
        }).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPosts(Long tripId, TravelTripPostBindRequest req) {
        requireTrip(tripId);
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        List<Long> postIds = cleanIds(req.getPostIds());
        validatePostsExist(postIds);
        if (req.getPrimaryPostId() != null && !postIds.contains(req.getPrimaryPostId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "primaryPostId 必须在 postIds 内");
        }

        tripBlogPostMapper.delete(new LambdaQueryWrapper<TravelTripBlogPost>()
                .eq(TravelTripBlogPost::getTripId, tripId));

        for (Long postId : postIds) {
            TravelTripBlogPost rel = new TravelTripBlogPost();
            rel.setTripId(tripId);
            rel.setPostId(postId);
            rel.setPostType(postId.equals(req.getPrimaryPostId()) ? POST_TYPE_PRIMARY : POST_TYPE_RELATED);
            tripBlogPostMapper.insert(rel);
        }
    }

    // -------------------- 校验 / 工具 --------------------

    private void validateCreate(TravelTripCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标题不能为空");
        }
        if (!StringUtils.hasText(req.getSlug())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "slug 不能为空");
        }
        checkLength(req.getTitle(), TITLE_MAX_LENGTH, "title");
        checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
        checkLength(req.getSummary(), SUMMARY_MAX_LENGTH, "summary");
        checkLength(req.getCostCurrency(), CURRENCY_MAX_LENGTH, "costCurrency");
    }

    private TravelTrip requireTrip(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "游记ID不能为空");
        }
        TravelTrip trip = tripMapper.selectById(id);
        if (trip == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "游记不存在");
        }
        return trip;
    }

    private Long resolveCurrentUserId() {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "作者不能为空");
        }
        return currentUserId;
    }

    private void checkSlugUnique(String slug, Long excludeId) {
        LambdaQueryWrapper<TravelTrip> wrapper = new LambdaQueryWrapper<TravelTrip>()
                .eq(TravelTrip::getSlug, slug);
        if (excludeId != null) {
            wrapper.ne(TravelTrip::getId, excludeId);
        }
        if (tripMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "slug 已存在");
        }
    }

    private void checkLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 长度不能超过 " + maxLength);
        }
    }

    private void validateFileExists(Long fileId) {
        if (fileId != null && fileAssetMapper.selectById(fileId) == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "coverFileId 不存在");
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

    private Integer calcDaysCount(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            return null;
        }
        return (int) (ChronoUnit.DAYS.between(start, end) + 1);
    }

    private List<Long> cleanIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream().filter(id -> id != null && id > 0).distinct().toList();
    }

    private void validatePostsExist(List<Long> postIds) {
        if (postIds.isEmpty()) {
            return;
        }
        if (postMapper.selectBatchIds(postIds).size() != postIds.size()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在不存在的文章");
        }
    }

    // -------------------- VO 转换 --------------------

    private TravelTripAdminVO toVO(TravelTrip t) {
        TravelTripAdminVO vo = new TravelTripAdminVO();
        vo.setId(t.getId());
        vo.setUserId(t.getUserId());
        vo.setTitle(t.getTitle());
        vo.setSlug(t.getSlug());
        vo.setSummary(t.getSummary());
        vo.setCoverFileId(t.getCoverFileId());
        vo.setStatus(t.getStatus());
        vo.setVisibility(t.getVisibility());
        vo.setStartDate(t.getStartDate());
        vo.setEndDate(t.getEndDate());
        vo.setDaysCount(t.getDaysCount());
        vo.setPersons(t.getPersons());
        vo.setCostTotal(t.getCostTotal());
        vo.setCostCurrency(t.getCostCurrency());
        vo.setViewCount(t.getViewCount());
        vo.setLikeCount(t.getLikeCount());
        vo.setPublishedAt(t.getPublishedAt());
        vo.setCreateTime(t.getCreateTime());
        vo.setUpdateTime(t.getUpdateTime());

        if (t.getCoverFileId() != null) {
            BlogFileAsset asset = fileAssetMapper.selectById(t.getCoverFileId());
            if (asset != null) {
                vo.setCoverUrl(resolveFileUrl(asset));
            }
        }
        return vo;
    }

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
