package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.blog.dto.front.TravelTripPageQuery;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.entity.TravelCheckin;
import com.nebula.blog.entity.TravelDestination;
import com.nebula.blog.entity.TravelTrip;
import com.nebula.blog.entity.TravelTripDay;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.mapper.TravelCheckinMapper;
import com.nebula.blog.mapper.TravelDestinationMapper;
import com.nebula.blog.mapper.TravelTripDayMapper;
import com.nebula.blog.mapper.TravelTripMapper;
import com.nebula.blog.service.TravelTripFrontService;
import com.nebula.blog.vo.front.TravelCheckinVO;
import com.nebula.blog.vo.front.TravelDestinationSummaryVO;
import com.nebula.blog.vo.front.TravelTripDayVO;
import com.nebula.blog.vo.front.TravelTripDetailVO;
import com.nebula.blog.vo.front.TravelTripListResponse;
import com.nebula.blog.vo.front.TravelTripListVO;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 游记前台服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelTripFrontServiceImpl implements TravelTripFrontService {

    private static final String STATUS_PUBLISHED = "published";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String OSS_STORAGE_TYPE = "oss";
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final TravelTripMapper tripMapper;
    private final TravelTripDayMapper tripDayMapper;
    private final TravelCheckinMapper checkinMapper;
    private final TravelDestinationMapper destinationMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    @Override
    public TravelTripListResponse listTrips(TravelTripPageQuery query) {
        TravelTripPageQuery safe = query == null ? new TravelTripPageQuery() : query;

        List<Long> destTripIds = filterTripIdsByDestination(safe.getDestinationId());
        if (destTripIds != null && destTripIds.isEmpty()) {
            return new TravelTripListResponse(List.of(), null);
        }

        LambdaQueryWrapper<TravelTrip> wrapper = baseWrapper();
        if (destTripIds != null) {
            wrapper.in(TravelTrip::getId, destTripIds);
        }
        if (StringUtils.hasText(safe.getKeyword())) {
            wrapper.and(w -> w.like(TravelTrip::getTitle, safe.getKeyword())
                    .or().like(TravelTrip::getSummary, safe.getKeyword()));
        }
        applyCursor(wrapper, safe.getCursor());

        int limit = safeLimit(safe.getLimit());
        wrapper.last("LIMIT " + (limit + 1));

        List<TravelTrip> trips = tripMapper.selectList(wrapper);
        return paginate(trips, limit);
    }

    @Override
    public TravelTripListResponse searchTrips(TravelTripPageQuery query) {
        // 当前未接入 Meilisearch 旅行索引，沿用 LIKE 搜索；保留独立入口以便后续切换。
        return listTrips(query);
    }

    @Override
    public List<TravelTripListVO> getHotTrips(int limit) {
        int safeLimit = safeLimit(limit);
        List<TravelTrip> trips = tripMapper.selectList(
                new LambdaQueryWrapper<TravelTrip>()
                        .eq(TravelTrip::getStatus, STATUS_PUBLISHED)
                        .eq(TravelTrip::getVisibility, VISIBILITY_PUBLIC)
                        .orderByDesc(TravelTrip::getViewCount)
                        .orderByDesc(TravelTrip::getLikeCount)
                        .last("LIMIT " + safeLimit));
        return trips.stream().map(this::toListVO).collect(Collectors.toList());
    }

    @Override
    public TravelTripDetailVO getTripDetail(String slug) {
        if (!StringUtils.hasText(slug)) {
            return null;
        }
        TravelTrip trip = tripMapper.selectOne(new LambdaQueryWrapper<TravelTrip>()
                .eq(TravelTrip::getSlug, slug)
                .eq(TravelTrip::getStatus, STATUS_PUBLISHED)
                .eq(TravelTrip::getVisibility, VISIBILITY_PUBLIC));
        if (trip == null) {
            return null;
        }

        tripMapper.update(null, new LambdaUpdateWrapper<TravelTrip>()
                .eq(TravelTrip::getId, trip.getId())
                .setSql("view_count = view_count + 1"));

        TravelTripDetailVO vo = new TravelTripDetailVO();
        copyListFields(trip, vo);

        List<TravelTripDay> days = tripDayMapper.selectList(new LambdaQueryWrapper<TravelTripDay>()
                .eq(TravelTripDay::getTripId, trip.getId())
                .orderByAsc(TravelTripDay::getDayNumber)
                .orderByAsc(TravelTripDay::getSortOrder));

        if (days.isEmpty()) {
            vo.setDays(List.of());
            vo.setDestinations(List.of());
            return vo;
        }

        List<Long> dayIds = days.stream().map(TravelTripDay::getId).toList();
        List<TravelCheckin> checkins = checkinMapper.selectList(new LambdaQueryWrapper<TravelCheckin>()
                .in(TravelCheckin::getTripDayId, dayIds)
                .orderByAsc(TravelCheckin::getSortOrder)
                .orderByAsc(TravelCheckin::getId));

        Map<Long, TravelDestination> destMap = loadDestinations(checkins);
        Map<Long, BlogFileAsset> photoMap = loadCheckinPhotos(checkins);

        Map<Long, List<TravelCheckinVO>> checkinByDay = new LinkedHashMap<>();
        for (TravelCheckin c : checkins) {
            checkinByDay.computeIfAbsent(c.getTripDayId(), k -> new ArrayList<>())
                    .add(toCheckinVO(c, destMap, photoMap));
        }

        List<TravelTripDayVO> dayVOs = days.stream().map(d -> {
            TravelTripDayVO dvo = new TravelTripDayVO();
            dvo.setId(d.getId());
            dvo.setDayNumber(d.getDayNumber());
            dvo.setTitle(d.getTitle());
            dvo.setDescription(d.getDescription());
            dvo.setAccommodation(d.getAccommodation());
            dvo.setMealCost(d.getMealCost());
            dvo.setTransportCost(d.getTransportCost());
            dvo.setOtherCost(d.getOtherCost());
            dvo.setSortOrder(d.getSortOrder());
            dvo.setCheckins(checkinByDay.getOrDefault(d.getId(), List.of()));
            return dvo;
        }).toList();

        // 足迹：去重的目的地列表，保留首次出现顺序
        LinkedHashMap<Long, TravelDestinationSummaryVO> footprint = new LinkedHashMap<>();
        for (TravelCheckin c : checkins) {
            if (c.getDestinationId() == null) continue;
            TravelDestination d = destMap.get(c.getDestinationId());
            if (d == null) continue;
            footprint.computeIfAbsent(d.getId(), k -> {
                TravelDestinationSummaryVO s = new TravelDestinationSummaryVO();
                s.setId(d.getId());
                s.setName(d.getName());
                s.setSlug(d.getSlug());
                s.setType(d.getType());
                return s;
            });
        }

        vo.setDays(dayVOs);
        vo.setDestinations(new ArrayList<>(footprint.values()));
        return vo;
    }

    // -------------------- 辅助 --------------------

    private LambdaQueryWrapper<TravelTrip> baseWrapper() {
        return new LambdaQueryWrapper<TravelTrip>()
                .eq(TravelTrip::getStatus, STATUS_PUBLISHED)
                .eq(TravelTrip::getVisibility, VISIBILITY_PUBLIC)
                .orderByDesc(TravelTrip::getPublishedAt)
                .orderByDesc(TravelTrip::getId);
    }

    private void applyCursor(LambdaQueryWrapper<TravelTrip> wrapper, String cursor) {
        if (!StringUtils.hasText(cursor)) {
            return;
        }
        try {
            long epochSeconds = Long.parseLong(cursor);
            LocalDateTime cursorTime = LocalDateTime.ofEpochSecond(epochSeconds, 0, ZoneOffset.UTC);
            wrapper.lt(TravelTrip::getPublishedAt, cursorTime);
        } catch (NumberFormatException e) {
            log.warn("Invalid travel trip cursor: {}", cursor);
        }
    }

    private TravelTripListResponse paginate(List<TravelTrip> trips, int limit) {
        boolean hasMore = trips.size() > limit;
        if (hasMore) {
            trips = trips.subList(0, limit);
        }
        List<TravelTripListVO> items = trips.stream().map(this::toListVO).toList();
        String nextCursor = null;
        if (hasMore && !items.isEmpty()) {
            TravelTripListVO last = items.get(items.size() - 1);
            nextCursor = last.getPublishedAt() != null
                    ? String.valueOf(last.getPublishedAt().toEpochSecond(ZoneOffset.UTC))
                    : null;
        }
        return new TravelTripListResponse(items, nextCursor);
    }

    /**
     * 通过 destinationId 反查 tripIds：先 checkin → tripDayIds → tripIds。
     * 返回 null 表示不限定；返回空列表表示该 destination 下无任何 trip。
     */
    private List<Long> filterTripIdsByDestination(Long destinationId) {
        if (destinationId == null) {
            return null;
        }
        List<TravelCheckin> matchedCheckins = checkinMapper.selectList(
                new LambdaQueryWrapper<TravelCheckin>()
                        .select(TravelCheckin::getTripDayId)
                        .eq(TravelCheckin::getDestinationId, destinationId));
        if (matchedCheckins.isEmpty()) {
            return List.of();
        }
        Set<Long> dayIds = matchedCheckins.stream()
                .map(TravelCheckin::getTripDayId)
                .collect(Collectors.toSet());
        List<TravelTripDay> days = tripDayMapper.selectList(
                new LambdaQueryWrapper<TravelTripDay>()
                        .select(TravelTripDay::getTripId)
                        .in(TravelTripDay::getId, dayIds));
        if (days.isEmpty()) {
            return List.of();
        }
        return days.stream().map(TravelTripDay::getTripId).distinct().toList();
    }

    private Map<Long, TravelDestination> loadDestinations(List<TravelCheckin> checkins) {
        Set<Long> ids = checkins.stream()
                .map(TravelCheckin::getDestinationId)
                .filter(id -> id != null)
                .collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Collections.emptyMap();
        }
        return destinationMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(TravelDestination::getId, d -> d));
    }

    private Map<Long, BlogFileAsset> loadCheckinPhotos(List<TravelCheckin> checkins) {
        Set<Long> photoIds = new java.util.HashSet<>();
        for (TravelCheckin c : checkins) {
            for (Long id : parsePhotoIds(c.getPhotos())) {
                photoIds.add(id);
            }
        }
        if (photoIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return fileAssetMapper.selectBatchIds(photoIds).stream()
                .collect(Collectors.toMap(BlogFileAsset::getId, a -> a));
    }

    private List<Long> parsePhotoIds(String photos) {
        if (!StringUtils.hasText(photos)) {
            return List.of();
        }
        List<Long> ids = new ArrayList<>();
        for (String s : photos.split(",")) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ignore) {
                // 跳过非法 id
            }
        }
        return ids;
    }

    private TravelCheckinVO toCheckinVO(TravelCheckin c,
                                        Map<Long, TravelDestination> destMap,
                                        Map<Long, BlogFileAsset> photoMap) {
        TravelCheckinVO vo = new TravelCheckinVO();
        vo.setId(c.getId());
        vo.setCustomName(c.getCustomName());
        vo.setDestinationId(c.getDestinationId());
        vo.setCustomLongitude(c.getCustomLongitude());
        vo.setCustomLatitude(c.getCustomLatitude());
        vo.setArrivalTime(c.getArrivalTime());
        vo.setDepartureTime(c.getDepartureTime());
        vo.setNotes(c.getNotes());
        vo.setRating(c.getRating());
        vo.setSortOrder(c.getSortOrder());
        if (c.getDestinationId() != null) {
            TravelDestination d = destMap.get(c.getDestinationId());
            if (d != null) {
                vo.setDestinationName(d.getName());
            }
        }
        List<String> urls = parsePhotoIds(c.getPhotos()).stream()
                .map(photoMap::get)
                .filter(a -> a != null)
                .map(this::resolveFileUrl)
                .filter(StringUtils::hasText)
                .toList();
        vo.setPhotoUrls(urls);
        return vo;
    }

    private TravelTripListVO toListVO(TravelTrip t) {
        TravelTripListVO vo = new TravelTripListVO();
        copyListFields(t, vo);
        return vo;
    }

    private void copyListFields(TravelTrip t, TravelTripListVO vo) {
        vo.setId(t.getId());
        vo.setSlug(t.getSlug());
        vo.setTitle(t.getTitle());
        vo.setSummary(t.getSummary());
        vo.setStartDate(t.getStartDate());
        vo.setEndDate(t.getEndDate());
        vo.setDaysCount(t.getDaysCount());
        vo.setPersons(t.getPersons());
        vo.setCostTotal(t.getCostTotal());
        vo.setCostCurrency(t.getCostCurrency());
        vo.setViewCount(t.getViewCount());
        vo.setLikeCount(t.getLikeCount());
        vo.setPublishedAt(t.getPublishedAt());
        if (t.getCoverFileId() != null) {
            BlogFileAsset asset = fileAssetMapper.selectById(t.getCoverFileId());
            if (asset != null) {
                vo.setCoverUrl(resolveFileUrl(asset));
            }
        }
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

    private int safeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit, MAX_LIMIT);
    }
}
