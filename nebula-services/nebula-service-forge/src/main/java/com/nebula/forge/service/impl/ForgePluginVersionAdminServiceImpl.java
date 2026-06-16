package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.forge.dto.admin.ForgePluginPermissionBindRequest;
import com.nebula.forge.dto.admin.ForgePluginPermissionItem;
import com.nebula.forge.dto.admin.ForgePluginVersionCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginVersionPageQuery;
import com.nebula.forge.dto.admin.ForgePluginVersionReviewRequest;
import com.nebula.forge.dto.admin.ForgePluginVersionUpdateRequest;
import com.nebula.forge.entity.ForgePlugin;
import com.nebula.forge.entity.ForgePluginPermission;
import com.nebula.forge.entity.ForgePluginVersion;
import com.nebula.forge.mapper.ForgePluginMapper;
import com.nebula.forge.mapper.ForgePluginPermissionMapper;
import com.nebula.forge.mapper.ForgePluginVersionMapper;
import com.nebula.forge.service.ForgePluginVersionAdminService;
import com.nebula.forge.vo.admin.ForgePluginPermissionAdminVO;
import com.nebula.forge.vo.admin.ForgePluginVersionAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 插件版本管理服务实现（管理员端）
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginVersionAdminServiceImpl implements ForgePluginVersionAdminService {

    /** 版本状态：已发布 */
    private static final int STATUS_PUBLISHED = 1;
    /** 审核状态：通过 */
    private static final int REVIEW_PASSED = 1;

    private final ForgePluginVersionMapper versionMapper;
    private final ForgePluginPermissionMapper permissionMapper;
    private final ForgePluginMapper pluginMapper;

    @Override
    public PageResult<ForgePluginVersionAdminVO> page(Long pluginId, ForgePluginVersionPageQuery query) {
        requirePlugin(pluginId);
        ForgePluginVersionPageQuery safe = query == null ? new ForgePluginVersionPageQuery() : query;
        Page<ForgePluginVersion> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<ForgePluginVersion> wrapper = new LambdaQueryWrapper<ForgePluginVersion>()
                .eq(ForgePluginVersion::getPluginId, pluginId)
                .eq(safe.getStatus() != null, ForgePluginVersion::getStatus, safe.getStatus())
                .eq(safe.getReviewStatus() != null, ForgePluginVersion::getReviewStatus, safe.getReviewStatus())
                .eq(StringUtils.hasText(safe.getChannel()), ForgePluginVersion::getChannel, safe.getChannel())
                .orderByDesc(ForgePluginVersion::getCreateTime);
        Page<ForgePluginVersion> result = versionMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ForgePluginVersionAdminVO detail(Long pluginId, Long versionId) {
        return toVO(requireVersion(pluginId, versionId));
    }

    @Override
    public Long create(Long pluginId, ForgePluginVersionCreateRequest req) {
        requirePlugin(pluginId);
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        checkVersionUnique(pluginId, req.getVersion(), null);

        ForgePluginVersion entity = new ForgePluginVersion();
        entity.setPluginId(pluginId);
        entity.setVersion(req.getVersion());
        entity.setChannel(StringUtils.hasText(req.getChannel()) ? req.getChannel() : "stable");
        entity.setManifestJson(req.getManifestJson());
        entity.setPackageFileId(req.getPackageFileId());
        entity.setPackageUrl(req.getPackageUrl());
        entity.setPackageSha256(req.getPackageSha256());
        entity.setPackageSize(req.getPackageSize() != null ? req.getPackageSize() : 0L);
        entity.setSignature(req.getSignature());
        entity.setMinAppVersion(req.getMinAppVersion());
        entity.setMaxAppVersion(req.getMaxAppVersion());
        entity.setChangelog(req.getChangelog());
        entity.setDownloadCount(0L);
        entity.setReviewStatus(0);
        entity.setStatus(req.getStatus() != null ? req.getStatus() : 0);
        entity.setRemark(req.getRemark());
        versionMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long pluginId, Long versionId, ForgePluginVersionUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        ForgePluginVersion existing = requireVersion(pluginId, versionId);
        if (StringUtils.hasText(req.getVersion()) && !req.getVersion().equals(existing.getVersion())) {
            checkVersionUnique(pluginId, req.getVersion(), versionId);
            existing.setVersion(req.getVersion());
        }
        if (StringUtils.hasText(req.getChannel())) existing.setChannel(req.getChannel());
        if (req.getManifestJson() != null) existing.setManifestJson(req.getManifestJson());
        if (req.getPackageFileId() != null) existing.setPackageFileId(req.getPackageFileId());
        if (req.getPackageUrl() != null) existing.setPackageUrl(req.getPackageUrl());
        if (StringUtils.hasText(req.getPackageSha256())) existing.setPackageSha256(req.getPackageSha256());
        if (req.getPackageSize() != null) existing.setPackageSize(req.getPackageSize());
        if (req.getSignature() != null) existing.setSignature(req.getSignature());
        if (req.getMinAppVersion() != null) existing.setMinAppVersion(req.getMinAppVersion());
        if (req.getMaxAppVersion() != null) existing.setMaxAppVersion(req.getMaxAppVersion());
        if (req.getChangelog() != null) existing.setChangelog(req.getChangelog());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        versionMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void review(Long pluginId, Long versionId, ForgePluginVersionReviewRequest req) {
        if (req == null || req.getReviewStatus() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "审核结果不能为空");
        }
        if (req.getReviewStatus() != REVIEW_PASSED && req.getReviewStatus() != 2) {
            throw new BizException(HttpStatus.BAD_REQUEST, "审核结果只能为通过或拒绝");
        }
        ForgePluginVersion existing = requireVersion(pluginId, versionId);
        existing.setReviewStatus(req.getReviewStatus());
        existing.setReviewRemark(req.getReviewRemark());
        versionMapper.updateById(existing);
        // 审核拒绝时，若该版本曾为最新版本需重算
        if (req.getReviewStatus() != REVIEW_PASSED) {
            recomputeLatestVersion(pluginId);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long pluginId, Long versionId, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        ForgePluginVersion existing = requireVersion(pluginId, versionId);
        if (status == STATUS_PUBLISHED) {
            if (existing.getReviewStatus() == null || existing.getReviewStatus() != REVIEW_PASSED) {
                throw new BizException(HttpStatus.BAD_REQUEST, "版本审核通过后才能发布");
            }
            if (existing.getPublishedTime() == null) {
                existing.setPublishedTime(LocalDateTime.now());
            }
        }
        existing.setStatus(status);
        versionMapper.updateById(existing);
        recomputeLatestVersion(pluginId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long pluginId, Long versionId) {
        requireVersion(pluginId, versionId);
        permissionMapper.delete(
                new LambdaQueryWrapper<ForgePluginPermission>().eq(ForgePluginPermission::getVersionId, versionId)
        );
        versionMapper.deleteById(versionId);
        recomputeLatestVersion(pluginId);
    }

    @Override
    public List<ForgePluginPermissionAdminVO> listPermissions(Long pluginId, Long versionId) {
        requireVersion(pluginId, versionId);
        return permissionMapper.selectList(
                        new LambdaQueryWrapper<ForgePluginPermission>()
                                .eq(ForgePluginPermission::getVersionId, versionId)
                                .orderByDesc(ForgePluginPermission::getRiskLevel)
                                .orderByAsc(ForgePluginPermission::getId))
                .stream().map(this::toPermissionVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPermissions(Long pluginId, Long versionId, ForgePluginPermissionBindRequest req) {
        requireVersion(pluginId, versionId);
        permissionMapper.delete(
                new LambdaQueryWrapper<ForgePluginPermission>().eq(ForgePluginPermission::getVersionId, versionId)
        );
        if (req == null || req.getPermissions() == null || req.getPermissions().isEmpty()) {
            return;
        }
        for (ForgePluginPermissionItem item : req.getPermissions()) {
            ForgePluginPermission entity = new ForgePluginPermission();
            entity.setPluginId(pluginId);
            entity.setVersionId(versionId);
            entity.setPermissionCode(item.getPermissionCode());
            entity.setPermissionName(item.getPermissionName());
            entity.setDescription(item.getDescription());
            entity.setRiskLevel(item.getRiskLevel() != null ? item.getRiskLevel() : 1);
            entity.setRequired(item.getRequired() != null ? item.getRequired() : 1);
            permissionMapper.insert(entity);
        }
    }

    // ============ 私有方法 ============

    /**
     * 重算并回写插件的最新版本：取已发布(status=1)且审核通过(reviewStatus=1)中发布时间最新的版本。
     */
    private void recomputeLatestVersion(Long pluginId) {
        ForgePlugin plugin = pluginMapper.selectById(pluginId);
        if (plugin == null) {
            return;
        }
        List<ForgePluginVersion> published = versionMapper.selectList(
                new LambdaQueryWrapper<ForgePluginVersion>()
                        .eq(ForgePluginVersion::getPluginId, pluginId)
                        .eq(ForgePluginVersion::getStatus, STATUS_PUBLISHED)
                        .eq(ForgePluginVersion::getReviewStatus, REVIEW_PASSED)
                        .orderByDesc(ForgePluginVersion::getPublishedTime)
                        .orderByDesc(ForgePluginVersion::getId)
        );
        if (published.isEmpty()) {
            plugin.setLatestVersionId(null);
            plugin.setLatestVersion(null);
        } else {
            ForgePluginVersion latest = published.get(0);
            plugin.setLatestVersionId(latest.getId());
            plugin.setLatestVersion(latest.getVersion());
        }
        pluginMapper.updateById(plugin);
    }

    private ForgePlugin requirePlugin(Long pluginId) {
        ForgePlugin plugin = pluginId == null ? null : pluginMapper.selectById(pluginId);
        if (plugin == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "插件不存在");
        }
        return plugin;
    }

    private ForgePluginVersion requireVersion(Long pluginId, Long versionId) {
        ForgePluginVersion version = versionId == null ? null : versionMapper.selectById(versionId);
        if (version == null || !version.getPluginId().equals(pluginId)) {
            throw new BizException(HttpStatus.NOT_FOUND, "插件版本不存在");
        }
        return version;
    }

    private void checkVersionUnique(Long pluginId, String version, Long excludeId) {
        LambdaQueryWrapper<ForgePluginVersion> wrapper = new LambdaQueryWrapper<ForgePluginVersion>()
                .eq(ForgePluginVersion::getPluginId, pluginId)
                .eq(ForgePluginVersion::getVersion, version);
        if (excludeId != null) {
            wrapper.ne(ForgePluginVersion::getId, excludeId);
        }
        if (versionMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该版本号已存在");
        }
    }

    private ForgePluginVersionAdminVO toVO(ForgePluginVersion v) {
        ForgePluginVersionAdminVO vo = new ForgePluginVersionAdminVO();
        vo.setId(v.getId());
        vo.setPluginId(v.getPluginId());
        vo.setVersion(v.getVersion());
        vo.setChannel(v.getChannel());
        vo.setManifestJson(v.getManifestJson());
        vo.setPackageFileId(v.getPackageFileId());
        vo.setPackageUrl(v.getPackageUrl());
        vo.setPackageSha256(v.getPackageSha256());
        vo.setPackageSize(v.getPackageSize());
        vo.setSignature(v.getSignature());
        vo.setMinAppVersion(v.getMinAppVersion());
        vo.setMaxAppVersion(v.getMaxAppVersion());
        vo.setChangelog(v.getChangelog());
        vo.setDownloadCount(v.getDownloadCount());
        vo.setReviewStatus(v.getReviewStatus());
        vo.setReviewRemark(v.getReviewRemark());
        vo.setPublishedTime(v.getPublishedTime());
        vo.setStatus(v.getStatus());
        vo.setRemark(v.getRemark());
        vo.setCreateTime(v.getCreateTime());
        vo.setUpdateTime(v.getUpdateTime());
        return vo;
    }

    private ForgePluginPermissionAdminVO toPermissionVO(ForgePluginPermission p) {
        ForgePluginPermissionAdminVO vo = new ForgePluginPermissionAdminVO();
        vo.setId(p.getId());
        vo.setPluginId(p.getPluginId());
        vo.setVersionId(p.getVersionId());
        vo.setPermissionCode(p.getPermissionCode());
        vo.setPermissionName(p.getPermissionName());
        vo.setDescription(p.getDescription());
        vo.setRiskLevel(p.getRiskLevel());
        vo.setRequired(p.getRequired());
        vo.setCreateTime(p.getCreateTime());
        return vo;
    }
}
