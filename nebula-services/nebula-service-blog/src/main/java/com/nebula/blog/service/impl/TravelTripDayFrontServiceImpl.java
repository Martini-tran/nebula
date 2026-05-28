package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.nebula.blog.service.TravelCheckinFrontService;
import com.nebula.blog.service.TravelTripDayFrontService;
import com.nebula.blog.vo.front.TravelCheckinVO;
import com.nebula.blog.vo.front.TravelTripDayVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 行程日 + 打卡点 前台服务统一实现。
 *
 * <p>仅暴露已发布且公开的游记下的行程日与打卡点。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelTripDayFrontServiceImpl implements TravelTripDayFrontService, TravelCheckinFrontService {

    private static final String STATUS_PUBLISHED = "published";
    private static final String VISIBILITY_PUBLIC = "public";
    private static final String OSS_STORAGE_TYPE = "oss";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final TravelTripMapper tripMapper;
    private final TravelTripDayMapper tripDayMapper;
    private final TravelCheckinMapper checkinMapper;
    private final TravelDestinationMapper destinationMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    // ===================================================================
    // TripDay
    // ===================================================================

    @Override
    public List<TravelTripDayVO> listByTrip(Long tripId) {
        if (tripId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "游记ID不能为空");
        }
        TravelTrip trip = tripMapper.selectById(tripId);
        if (!isPublic(trip)) {
            return List.of();
        }
        List<TravelTripDay> days = tripDayMapper.selectList(new LambdaQueryWrapper<TravelTripDay>()
                .eq(TravelTripDay::getTripId, tripId)
                .orderByAsc(TravelTripDay::getDayNumber)
                .orderByAsc(TravelTripDay::getSortOrder));
        if (days.isEmpty()) {
            return List.of();
        }
        return enrichDays(days);
    }

    @Override
    public TravelTripDayVO getDay(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "行程日ID不能为空");
        }
        TravelTripDay day = tripDayMapper.selectById(id);
        if (day == null) {
            return null;
        }
        TravelTrip trip = tripMapper.selectById(day.getTripId());
        if (!isPublic(trip)) {
            return null;
        }
        List<TravelTripDayVO> rows = enrichDays(List.of(day));
        return rows.isEmpty() ? null : rows.get(0);
    }

    // ===================================================================
    // Checkin
    // ===================================================================

    @Override
    public List<TravelCheckinVO> listByTripDay(Long tripDayId) {
        if (tripDayId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "行程日ID不能为空");
        }
        TravelTripDay day = tripDayMapper.selectById(tripDayId);
        if (day == null) {
            return List.of();
        }
        TravelTrip trip = tripMapper.selectById(day.getTripId());
        if (!isPublic(trip)) {
            return List.of();
        }
        List<TravelCheckin> checkins = checkinMapper.selectList(new LambdaQueryWrapper<TravelCheckin>()
                .eq(TravelCheckin::getTripDayId, tripDayId)
                .orderByAsc(TravelCheckin::getSortOrder)
                .orderByAsc(TravelCheckin::getId));
        return buildCheckinVOs(checkins);
    }

    @Override
    public TravelCheckinVO getCheckin(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "打卡点ID不能为空");
        }
        TravelCheckin checkin = checkinMapper.selectById(id);
        if (checkin == null) {
            return null;
        }
        TravelTripDay day = tripDayMapper.selectById(checkin.getTripDayId());
        if (day == null) {
            return null;
        }
        TravelTrip trip = tripMapper.selectById(day.getTripId());
        if (!isPublic(trip)) {
            return null;
        }
        List<TravelCheckinVO> vos = buildCheckinVOs(List.of(checkin));
        return vos.isEmpty() ? null : vos.get(0);
    }

    // ===================================================================
    // Internal
    // ===================================================================

    private boolean isPublic(TravelTrip trip) {
        return trip != null
                && STATUS_PUBLISHED.equals(trip.getStatus())
                && VISIBILITY_PUBLIC.equals(trip.getVisibility());
    }

    private List<TravelTripDayVO> enrichDays(List<TravelTripDay> days) {
        List<Long> dayIds = days.stream().map(TravelTripDay::getId).toList();
        List<TravelCheckin> checkins = dayIds.isEmpty() ? List.of()
                : checkinMapper.selectList(new LambdaQueryWrapper<TravelCheckin>()
                .in(TravelCheckin::getTripDayId, dayIds)
                .orderByAsc(TravelCheckin::getSortOrder)
                .orderByAsc(TravelCheckin::getId));

        Map<Long, List<TravelCheckin>> rawByDay = checkins.stream()
                .collect(Collectors.groupingBy(TravelCheckin::getTripDayId, LinkedHashMap::new, Collectors.toList()));
        Map<Long, List<TravelCheckinVO>> checkinByDay = new LinkedHashMap<>();
        for (Map.Entry<Long, List<TravelCheckin>> e : rawByDay.entrySet()) {
            checkinByDay.put(e.getKey(), buildCheckinVOs(e.getValue()));
        }

        return days.stream().map(d -> {
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
    }

    private List<TravelCheckinVO> buildCheckinVOs(List<TravelCheckin> checkins) {
        if (checkins == null || checkins.isEmpty()) {
            return List.of();
        }
        List<Long> destIds = checkins.stream()
                .map(TravelCheckin::getDestinationId).filter(Objects::nonNull).distinct().toList();
        Map<Long, TravelDestination> destMap = destIds.isEmpty() ? new java.util.HashMap<>()
                : destinationMapper.selectBatchIds(destIds).stream()
                .collect(Collectors.toMap(TravelDestination::getId, Function.identity()));

        // 收集 photo id 并预加载文件资产
        java.util.HashSet<Long> photoIds = new java.util.HashSet<>();
        for (TravelCheckin c : checkins) {
            photoIds.addAll(parsePhotoIds(c.getPhotos()));
        }
        Map<Long, BlogFileAsset> photoMap = photoIds.isEmpty() ? new java.util.HashMap<>()
                : fileAssetMapper.selectBatchIds(photoIds).stream()
                .collect(Collectors.toMap(BlogFileAsset::getId, Function.identity()));

        List<TravelCheckinVO> result = new ArrayList<>(checkins.size());
        for (TravelCheckin c : checkins) {
            result.add(toCheckinVO(c, destMap, photoMap));
        }
        return result;
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
            TravelDestination dest = destMap.get(c.getDestinationId());
            if (dest != null) {
                vo.setDestinationName(dest.getName());
            }
        }
        List<String> urls = parsePhotoIds(c.getPhotos()).stream()
                .map(photoMap::get)
                .filter(Objects::nonNull)
                .map(this::resolveFileUrl)
                .filter(StringUtils::hasText)
                .toList();
        vo.setPhotoUrls(urls);
        return vo;
    }

    private List<Long> parsePhotoIds(String photos) {
        if (!StringUtils.hasText(photos)) {
            return List.of();
        }
        String trimmed = photos.trim();
        if (trimmed.startsWith("[")) {
            try {
                List<?> raw = JSON_MAPPER.readValue(trimmed, List.class);
                List<Long> ids = new ArrayList<>(raw.size());
                for (Object item : raw) {
                    if (item == null) continue;
                    try {
                        ids.add(Long.parseLong(item.toString().trim()));
                    } catch (NumberFormatException ignore) {
                        // skip
                    }
                }
                return ids;
            } catch (Exception e) {
                log.warn("Failed to parse photos JSON: {}", trimmed, e);
                return List.of();
            }
        }
        List<Long> ids = new ArrayList<>();
        for (String s : trimmed.split(",")) {
            String part = s.trim();
            if (part.isEmpty()) continue;
            try {
                ids.add(Long.parseLong(part));
            } catch (NumberFormatException ignore) {
                // skip
            }
        }
        return ids;
    }

    private String resolveFileUrl(BlogFileAsset asset) {
        if (asset == null) return null;
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
