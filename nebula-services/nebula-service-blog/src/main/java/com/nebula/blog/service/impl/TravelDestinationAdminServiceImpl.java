package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.nebula.blog.dto.admin.TravelDestinationCreateRequest;
import com.nebula.blog.dto.admin.TravelDestinationUpdateRequest;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.entity.TravelDestination;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.mapper.TravelDestinationMapper;
import com.nebula.blog.service.TravelDestinationAdminService;
import com.nebula.blog.vo.admin.TravelDestinationAdminVO;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 旅游目的地管理服务实现（管理员端）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TravelDestinationAdminServiceImpl implements TravelDestinationAdminService {

    private static final int TYPE_COUNTRY = 0;
    private static final int TYPE_REGION = 1;
    private static final int TYPE_CITY = 2;
    private static final int TYPE_POI = 3;
    private static final Set<Integer> ALL_TYPES = Set.of(TYPE_COUNTRY, TYPE_REGION, TYPE_CITY, TYPE_POI);

    private static final int STATUS_DISABLED = 0;
    private static final int STATUS_ENABLED = 1;
    private static final Set<Integer> ALL_STATUSES = Set.of(STATUS_DISABLED, STATUS_ENABLED);

    private static final String OSS_STORAGE_TYPE = "oss";

    private static final int NAME_MAX_LENGTH = 100;
    private static final int SLUG_MAX_LENGTH = 120;
    private static final int DESCRIPTION_MAX_LENGTH = 500;
    private static final int ADDRESS_MAX_LENGTH = 200;

    private final TravelDestinationMapper destinationMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    @Override
    public List<TravelDestinationAdminVO> tree() {
        List<TravelDestination> all = destinationMapper.selectList(
                new LambdaQueryWrapper<TravelDestination>()
                        .orderByAsc(TravelDestination::getSortOrder)
                        .orderByAsc(TravelDestination::getId));
        Map<Long, List<TravelDestination>> byParent = all.stream()
                .collect(Collectors.groupingBy(d -> d.getParentId() == null ? 0L : d.getParentId()));
        return buildTree(byParent, 0L);
    }

    @Override
    public TravelDestinationAdminVO detail(Long id) {
        return toVO(requireDestination(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TravelDestinationCreateRequest req) {
        validateCreate(req);
        checkSlugUnique(req.getSlug(), null);

        Integer type = req.getType();
        TravelDestination parent = null;
        if (req.getParentId() != null && req.getParentId() > 0) {
            parent = destinationMapper.selectById(req.getParentId());
            if (parent == null) {
                throw new BizException(HttpStatus.BAD_REQUEST, "父级目的地不存在");
            }
            // 子节点 type 必须严格大于父节点 type，避免出现 国家→国家 等错误结构
            if (parent.getType() != null && type <= parent.getType()) {
                throw new BizException(HttpStatus.BAD_REQUEST, "子级 type 必须大于父级 type");
            }
        } else if (type != TYPE_COUNTRY) {
            throw new BizException(HttpStatus.BAD_REQUEST, "顶层目的地 type 必须为 0（国家）");
        }

        if (req.getCoverFileId() != null) {
            validateFileExists(req.getCoverFileId());
        }

        TravelDestination entity = new TravelDestination();
        entity.setParentId(parent == null ? null : parent.getId());
        entity.setName(req.getName());
        entity.setSlug(req.getSlug());
        entity.setType(type);
        entity.setDescription(req.getDescription());
        entity.setCoverFileId(req.getCoverFileId());
        entity.setLongitude(req.getLongitude());
        entity.setLatitude(req.getLatitude());
        entity.setAddress(req.getAddress());
        entity.setVisitCount(0);
        entity.setStatus(normalizeStatus(req.getStatus(), STATUS_ENABLED));
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        destinationMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, TravelDestinationUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        TravelDestination existing = requireDestination(id);

        if (StringUtils.hasText(req.getName())) {
            checkLength(req.getName(), NAME_MAX_LENGTH, "name");
            existing.setName(req.getName());
        }
        if (StringUtils.hasText(req.getSlug()) && !req.getSlug().equals(existing.getSlug())) {
            checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
            checkSlugUnique(req.getSlug(), id);
            existing.setSlug(req.getSlug());
        }
        if (req.getType() != null) {
            if (!ALL_TYPES.contains(req.getType())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "type 非法");
            }
            existing.setType(req.getType());
        }
        if (req.getDescription() != null) {
            checkLength(req.getDescription(), DESCRIPTION_MAX_LENGTH, "description");
            existing.setDescription(req.getDescription());
        }
        if (req.getLongitude() != null) {
            existing.setLongitude(req.getLongitude());
        }
        if (req.getLatitude() != null) {
            existing.setLatitude(req.getLatitude());
        }
        if (req.getAddress() != null) {
            checkLength(req.getAddress(), ADDRESS_MAX_LENGTH, "address");
            existing.setAddress(req.getAddress());
        }
        if (req.getStatus() != null) {
            if (!ALL_STATUSES.contains(req.getStatus())) {
                throw new BizException(HttpStatus.BAD_REQUEST, "status 非法");
            }
            existing.setStatus(req.getStatus());
        }
        if (req.getSortOrder() != null) {
            existing.setSortOrder(req.getSortOrder());
        }

        // 处理父级变更（含成环检查）
        if (req.getParentId() != null) {
            Long oldParentId = existing.getParentId();
            Long newParentId = req.getParentId() == 0L ? null : req.getParentId();
            if (!Objects.equals(oldParentId, newParentId)) {
                if (newParentId != null) {
                    if (newParentId.equals(id)) {
                        throw new BizException(HttpStatus.BAD_REQUEST, "不能将自身作为父级");
                    }
                    TravelDestination newParent = destinationMapper.selectById(newParentId);
                    if (newParent == null) {
                        throw new BizException(HttpStatus.BAD_REQUEST, "目标父级不存在");
                    }
                    if (isDescendant(id, newParent)) {
                        throw new BizException(HttpStatus.BAD_REQUEST, "不能挂到自身的后代下");
                    }
                    if (newParent.getType() != null && existing.getType() != null
                            && existing.getType() <= newParent.getType()) {
                        throw new BizException(HttpStatus.BAD_REQUEST, "子级 type 必须大于父级 type");
                    }
                }
                existing.setParentId(newParentId);
            }
        }

        // 封面：clearCoverFileId 优先；否则若传了非空才更新
        boolean clearCover = Boolean.TRUE.equals(req.getClearCoverFileId());
        if (clearCover) {
            existing.setCoverFileId(null);
            LambdaUpdateWrapper<TravelDestination> uw = new LambdaUpdateWrapper<TravelDestination>()
                    .eq(TravelDestination::getId, id)
                    .set(TravelDestination::getCoverFileId, null);
            destinationMapper.update(existing, uw);
            return;
        } else if (req.getCoverFileId() != null) {
            validateFileExists(req.getCoverFileId());
            existing.setCoverFileId(req.getCoverFileId());
        }
        destinationMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireDestination(id);
        long childCount = destinationMapper.selectCount(
                new LambdaQueryWrapper<TravelDestination>().eq(TravelDestination::getParentId, id));
        if (childCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在子目的地，请先删除子节点");
        }
        destinationMapper.deleteById(id);
    }

    // -------------------- 校验工具 --------------------

    private void validateCreate(TravelDestinationCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目的地名称不能为空");
        }
        if (!StringUtils.hasText(req.getSlug())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目的地 slug 不能为空");
        }
        if (req.getType() == null || !ALL_TYPES.contains(req.getType())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "type 非法");
        }
        checkLength(req.getName(), NAME_MAX_LENGTH, "name");
        checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
        checkLength(req.getDescription(), DESCRIPTION_MAX_LENGTH, "description");
        checkLength(req.getAddress(), ADDRESS_MAX_LENGTH, "address");
    }

    private TravelDestination requireDestination(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "目的地ID不能为空");
        }
        TravelDestination d = destinationMapper.selectById(id);
        if (d == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "目的地不存在");
        }
        return d;
    }

    private void checkSlugUnique(String slug, Long excludeId) {
        LambdaQueryWrapper<TravelDestination> wrapper = new LambdaQueryWrapper<TravelDestination>()
                .eq(TravelDestination::getSlug, slug);
        if (excludeId != null) {
            wrapper.ne(TravelDestination::getId, excludeId);
        }
        if (destinationMapper.selectCount(wrapper) > 0) {
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

    private Integer normalizeStatus(Integer value, int fallback) {
        Integer v = value == null ? fallback : value;
        if (!ALL_STATUSES.contains(v)) {
            throw new BizException(HttpStatus.BAD_REQUEST, "status 非法");
        }
        return v;
    }

    /**
     * 判断 candidateAncestorOrSelf 是否是 nodeId 的后代（含 nodeId 本身）。
     * 用 path 字段不可行（实体未维护 path），改为递归向上查 candidate 的祖先链。
     */
    private boolean isDescendant(Long nodeId, TravelDestination candidate) {
        TravelDestination cursor = candidate;
        int safety = 64;
        while (cursor != null && safety-- > 0) {
            if (Objects.equals(cursor.getId(), nodeId)) {
                return true;
            }
            if (cursor.getParentId() == null) {
                return false;
            }
            cursor = destinationMapper.selectById(cursor.getParentId());
        }
        return false;
    }

    // -------------------- 树构建 / VO 转换 --------------------

    private List<TravelDestinationAdminVO> buildTree(Map<Long, List<TravelDestination>> byParent, long parentId) {
        List<TravelDestination> children = byParent.getOrDefault(parentId, List.of()).stream()
                .sorted(Comparator.comparing((TravelDestination d) -> d.getSortOrder() == null ? 0 : d.getSortOrder())
                        .thenComparing(TravelDestination::getId))
                .toList();
        List<TravelDestinationAdminVO> result = new ArrayList<>();
        for (TravelDestination c : children) {
            TravelDestinationAdminVO vo = toVO(c);
            vo.setChildren(buildTree(byParent, c.getId()));
            result.add(vo);
        }
        return result;
    }

    private TravelDestinationAdminVO toVO(TravelDestination d) {
        TravelDestinationAdminVO vo = new TravelDestinationAdminVO();
        vo.setId(d.getId());
        vo.setParentId(d.getParentId());
        vo.setName(d.getName());
        vo.setSlug(d.getSlug());
        vo.setType(d.getType());
        vo.setDescription(d.getDescription());
        vo.setCoverFileId(d.getCoverFileId());
        vo.setLongitude(d.getLongitude());
        vo.setLatitude(d.getLatitude());
        vo.setAddress(d.getAddress());
        vo.setVisitCount(d.getVisitCount());
        vo.setRating(d.getRating());
        vo.setStatus(d.getStatus());
        vo.setSortOrder(d.getSortOrder());
        vo.setCreateTime(d.getCreateTime());
        vo.setUpdateTime(d.getUpdateTime());
        if (d.getCoverFileId() != null) {
            BlogFileAsset asset = fileAssetMapper.selectById(d.getCoverFileId());
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
