package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.blog.dto.admin.TravelCheckinCreateRequest;
import com.nebula.blog.dto.admin.TravelCheckinUpdateRequest;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.entity.TravelCheckin;
import com.nebula.blog.entity.TravelDestination;
import com.nebula.blog.entity.TravelTripDay;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.mapper.TravelCheckinMapper;
import com.nebula.blog.mapper.TravelDestinationMapper;
import com.nebula.blog.mapper.TravelTripDayMapper;
import com.nebula.blog.service.TravelCheckinAdminService;
import com.nebula.blog.vo.admin.TravelCheckinAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 打卡点管理服务实现（管理员端）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelCheckinAdminServiceImpl implements TravelCheckinAdminService {

    private static final int CUSTOM_NAME_MAX_LENGTH = 200;
    private static final int NOTES_MAX_LENGTH = 2000;
    private static final BigDecimal LONGITUDE_MIN = new BigDecimal("-180");
    private static final BigDecimal LONGITUDE_MAX = new BigDecimal("180");
    private static final BigDecimal LATITUDE_MIN = new BigDecimal("-90");
    private static final BigDecimal LATITUDE_MAX = new BigDecimal("90");
    private static final String OSS_STORAGE_TYPE = "oss";
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final TravelCheckinMapper checkinMapper;
    private final TravelTripDayMapper tripDayMapper;
    private final TravelDestinationMapper destinationMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    @Override
    public List<TravelCheckinAdminVO> listByTripDay(Long tripDayId) {
        requireTripDay(tripDayId);
        List<TravelCheckin> rows = checkinMapper.selectList(
                new LambdaQueryWrapper<TravelCheckin>()
                        .eq(TravelCheckin::getTripDayId, tripDayId)
                        .orderByAsc(TravelCheckin::getSortOrder)
                        .orderByAsc(TravelCheckin::getId));
        return enrichWithDestinationNames(rows);
    }

    @Override
    public TravelCheckinAdminVO detail(Long id) {
        TravelCheckin c = requireCheckin(id);
        return enrichWithDestinationNames(List.of(c)).get(0);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TravelCheckinCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (req.getTripDayId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "tripDayId 不能为空");
        }
        requireTripDay(req.getTripDayId());

        if (req.getDestinationId() == null && !StringUtils.hasText(req.getCustomName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "destinationId 与 customName 至少传一个");
        }
        if (req.getDestinationId() != null) {
            validateDestinationExists(req.getDestinationId());
        }
        if (req.getArrivalTime() != null && req.getDepartureTime() != null
                && req.getDepartureTime().isBefore(req.getArrivalTime())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "departureTime 不能早于 arrivalTime");
        }
        checkLength(req.getCustomName(), CUSTOM_NAME_MAX_LENGTH, "customName");
        checkLongitude(req.getCustomLongitude());
        checkLatitude(req.getCustomLatitude());
        checkLength(req.getNotes(), NOTES_MAX_LENGTH, "notes");

        String normalizedPhotos = normalizePhotos(req.getPhotos());

        TravelCheckin entity = new TravelCheckin();
        entity.setTripDayId(req.getTripDayId());
        entity.setDestinationId(req.getDestinationId());
        entity.setCustomName(req.getCustomName());
        entity.setCustomLongitude(req.getCustomLongitude());
        entity.setCustomLatitude(req.getCustomLatitude());
        entity.setArrivalTime(req.getArrivalTime());
        entity.setDepartureTime(req.getDepartureTime());
        entity.setNotes(req.getNotes());
        entity.setRating(req.getRating());
        entity.setPhotos(normalizedPhotos);
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        checkinMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TravelCheckinUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        TravelCheckin existing = requireCheckin(id);

        boolean clearDestination = Boolean.TRUE.equals(req.getClearDestinationId());
        Long nextDestinationId = clearDestination ? null
                : (req.getDestinationId() != null ? req.getDestinationId() : existing.getDestinationId());
        String nextCustomName = req.getCustomName() != null ? req.getCustomName() : existing.getCustomName();
        if (nextDestinationId == null && !StringUtils.hasText(nextCustomName)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "destinationId 与 customName 至少保留一个");
        }
        if (req.getDestinationId() != null && !clearDestination) {
            validateDestinationExists(req.getDestinationId());
            existing.setDestinationId(req.getDestinationId());
        }
        if (req.getCustomName() != null) {
            checkLength(req.getCustomName(), CUSTOM_NAME_MAX_LENGTH, "customName");
            existing.setCustomName(req.getCustomName());
        }
        if (req.getCustomLongitude() != null) {
            checkLongitude(req.getCustomLongitude());
            existing.setCustomLongitude(req.getCustomLongitude());
        }
        if (req.getCustomLatitude() != null) {
            checkLatitude(req.getCustomLatitude());
            existing.setCustomLatitude(req.getCustomLatitude());
        }
        if (req.getArrivalTime() != null) existing.setArrivalTime(req.getArrivalTime());
        if (req.getDepartureTime() != null) existing.setDepartureTime(req.getDepartureTime());
        if (existing.getArrivalTime() != null && existing.getDepartureTime() != null
                && existing.getDepartureTime().isBefore(existing.getArrivalTime())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "departureTime 不能早于 arrivalTime");
        }
        if (req.getNotes() != null) {
            checkLength(req.getNotes(), NOTES_MAX_LENGTH, "notes");
            existing.setNotes(req.getNotes());
        }
        if (req.getRating() != null) existing.setRating(req.getRating());
        if (req.getPhotos() != null) existing.setPhotos(normalizePhotos(req.getPhotos()));
        if (req.getSortOrder() != null) existing.setSortOrder(req.getSortOrder());

        if (clearDestination) {
            existing.setDestinationId(null);
            LambdaUpdateWrapper<TravelCheckin> uw = new LambdaUpdateWrapper<TravelCheckin>()
                    .eq(TravelCheckin::getId, id)
                    .set(TravelCheckin::getDestinationId, null);
            checkinMapper.update(existing, uw);
            return;
        }
        checkinMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireCheckin(id);
        checkinMapper.deleteById(id);
    }

    // -------------------- 工具 --------------------

    private void requireTripDay(Long tripDayId) {
        if (tripDayId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "tripDayId 不能为空");
        }
        TravelTripDay day = tripDayMapper.selectById(tripDayId);
        if (day == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "行程日不存在");
        }
    }

    private TravelCheckin requireCheckin(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "打卡点ID不能为空");
        }
        TravelCheckin c = checkinMapper.selectById(id);
        if (c == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "打卡点不存在");
        }
        return c;
    }

    private void validateDestinationExists(Long destinationId) {
        if (destinationMapper.selectById(destinationId) == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "destinationId 不存在");
        }
    }

    private void checkLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 长度不能超过 " + maxLength);
        }
    }

    private void checkLongitude(BigDecimal value) {
        if (value == null) return;
        if (value.compareTo(LONGITUDE_MIN) < 0 || value.compareTo(LONGITUDE_MAX) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "customLongitude 必须在 -180 到 180 之间");
        }
    }

    private void checkLatitude(BigDecimal value) {
        if (value == null) return;
        if (value.compareTo(LATITUDE_MIN) < 0 || value.compareTo(LATITUDE_MAX) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "customLatitude 必须在 -90 到 90 之间");
        }
    }

    /**
     * 校验并规范化 photos 字段，返回 JSON 数组字符串（如 "[123,456]"）；空值返回 null。
     */
    private String normalizePhotos(String photos) {
        if (!StringUtils.hasText(photos)) {
            return null;
        }
        List<Long> ids = parsePhotoIds(photos);
        if (ids.isEmpty()) {
            return null;
        }
        try {
            return JSON_MAPPER.writeValueAsString(ids);
        } catch (Exception e) {
            throw new BizException(HttpStatus.BAD_REQUEST, "photos 格式非法");
        }
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
                throw new BizException(HttpStatus.BAD_REQUEST, "photos 必须是整数 ID 的 JSON 数组");
            }
        }
        // 兼容逗号分隔旧数据
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

    private List<TravelCheckinAdminVO> enrichWithDestinationNames(List<TravelCheckin> rows) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<Long> destIds = rows.stream()
                .map(TravelCheckin::getDestinationId)
                .filter(id -> id != null)
                .distinct()
                .toList();
        Map<Long, TravelDestination> destMap = destIds.isEmpty() ? Map.of()
                : destinationMapper.selectBatchIds(destIds).stream()
                .collect(Collectors.toMap(TravelDestination::getId, d -> d));

        Set<Long> photoIds = new HashSet<>();
        for (TravelCheckin c : rows) {
            photoIds.addAll(parsePhotoIds(c.getPhotos()));
        }
        Map<Long, BlogFileAsset> photoMap = photoIds.isEmpty() ? Map.of()
                : fileAssetMapper.selectBatchIds(photoIds).stream()
                .collect(Collectors.toMap(BlogFileAsset::getId, a -> a));

        return rows.stream().map(c -> {
            TravelCheckinAdminVO vo = toVO(c);
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
        }).toList();
    }

    private TravelCheckinAdminVO toVO(TravelCheckin c) {
        TravelCheckinAdminVO vo = new TravelCheckinAdminVO();
        vo.setId(c.getId());
        vo.setTripDayId(c.getTripDayId());
        vo.setDestinationId(c.getDestinationId());
        vo.setCustomName(c.getCustomName());
        vo.setCustomLongitude(c.getCustomLongitude());
        vo.setCustomLatitude(c.getCustomLatitude());
        vo.setArrivalTime(c.getArrivalTime());
        vo.setDepartureTime(c.getDepartureTime());
        vo.setNotes(c.getNotes());
        vo.setRating(c.getRating());
        vo.setPhotos(c.getPhotos());
        vo.setSortOrder(c.getSortOrder());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }
}
