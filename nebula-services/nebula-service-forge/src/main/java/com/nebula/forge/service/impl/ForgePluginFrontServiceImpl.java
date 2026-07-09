package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.file.service.SysFileService;
import com.nebula.forge.dto.front.ForgePluginDownloadRequest;
import com.nebula.forge.dto.front.ForgePluginFrontPageQuery;
import com.nebula.forge.entity.ForgePlugin;
import com.nebula.forge.entity.ForgePluginCategory;
import com.nebula.forge.entity.ForgePluginCategoryRel;
import com.nebula.forge.entity.ForgePluginDownloadLog;
import com.nebula.forge.entity.ForgePluginPermission;
import com.nebula.forge.entity.ForgePluginVersion;
import com.nebula.forge.mapper.ForgePluginCategoryMapper;
import com.nebula.forge.mapper.ForgePluginCategoryRelMapper;
import com.nebula.forge.mapper.ForgePluginDownloadLogMapper;
import com.nebula.forge.mapper.ForgePluginMapper;
import com.nebula.forge.mapper.ForgePluginPermissionMapper;
import com.nebula.forge.mapper.ForgePluginVersionMapper;
import com.nebula.forge.service.ForgePluginFrontService;
import com.nebula.forge.vo.front.ForgePluginDetailFrontVO;
import com.nebula.forge.vo.front.ForgePluginDownloadResultVO;
import com.nebula.forge.vo.front.ForgePluginFrontVO;
import com.nebula.forge.vo.front.ForgePluginPermissionFrontVO;
import com.nebula.forge.vo.front.ForgePluginVersionFrontVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件商城前台服务实现（只读 + 下载）
 * <p>仅暴露已上架插件与已发布、审核通过的版本。</p>
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginFrontServiceImpl implements ForgePluginFrontService {

    /** 插件状态：已上架 */
    private static final int PLUGIN_STATUS_ON = 1;
    /** 版本状态：已发布 */
    private static final int VERSION_STATUS_PUBLISHED = 1;
    /** 版本审核状态：已通过 */
    private static final int VERSION_REVIEW_PASSED = 1;
    /** 下载结果：成功 */
    private static final int DOWNLOAD_SUCCESS = 1;
    /** 下载结果：失败 */
    private static final int DOWNLOAD_FAIL = 0;

    private final ForgePluginMapper pluginMapper;
    private final ForgePluginVersionMapper versionMapper;
    private final ForgePluginPermissionMapper permissionMapper;
    private final ForgePluginCategoryMapper categoryMapper;
    private final ForgePluginCategoryRelMapper categoryRelMapper;
    private final ForgePluginDownloadLogMapper downloadLogMapper;
    private final SysFileService sysFileService;

    @Override
    public PageResult<ForgePluginFrontVO> page(ForgePluginFrontPageQuery query) {
        ForgePluginFrontPageQuery safe = query == null ? new ForgePluginFrontPageQuery() : query;

        // 按分类过滤：先取该分类下的插件ID集合
        Set<Long> pluginIdsByCategory = null;
        if (safe.getCategoryId() != null) {
            pluginIdsByCategory = categoryRelMapper.selectList(
                            new LambdaQueryWrapper<ForgePluginCategoryRel>()
                                    .eq(ForgePluginCategoryRel::getCategoryId, safe.getCategoryId()))
                    .stream().map(ForgePluginCategoryRel::getPluginId).collect(Collectors.toSet());
            if (pluginIdsByCategory.isEmpty()) {
                return PageResult.empty(safe.safePageNum(), safe.safePageSize());
            }
        }

        Page<ForgePlugin> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<ForgePlugin> wrapper = new LambdaQueryWrapper<ForgePlugin>()
                .eq(ForgePlugin::getStatus, PLUGIN_STATUS_ON)
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(ForgePlugin::getName, safe.getKeyword())
                        .or().like(ForgePlugin::getPluginKey, safe.getKeyword())
                        .or().like(ForgePlugin::getSummary, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getType()), ForgePlugin::getType, safe.getType())
                .eq(safe.getPricingType() != null, ForgePlugin::getPricingType, safe.getPricingType())
                .in(pluginIdsByCategory != null, ForgePlugin::getId, pluginIdsByCategory);
        applySort(wrapper, safe.getSort());
        Page<ForgePlugin> result = pluginMapper.selectPage(page, wrapper);

        List<ForgePlugin> records = result.getRecords();
        Map<Long, List<ForgePluginCategoryRel>> relsByPlugin = loadRels(
                records.stream().map(ForgePlugin::getId).toList());
        Map<Long, String> categoryNameById = loadCategoryNames(relsByPlugin);

        List<ForgePluginFrontVO> rows = records.stream()
                .map(p -> toListVO(p, relsByPlugin.getOrDefault(p.getId(), List.of()), categoryNameById))
                .toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ForgePluginDetailFrontVO detail(Long pluginId) {
        ForgePlugin plugin = requireOnlinePlugin(pluginId);
        Map<Long, List<ForgePluginCategoryRel>> relsByPlugin = loadRels(List.of(pluginId));
        Map<Long, String> categoryNameById = loadCategoryNames(relsByPlugin);
        return toDetailVO(plugin, relsByPlugin.getOrDefault(pluginId, List.of()), categoryNameById);
    }

    @Override
    public List<ForgePluginVersionFrontVO> listVersions(Long pluginId) {
        requireOnlinePlugin(pluginId);
        List<ForgePluginVersion> versions = versionMapper.selectList(
                new LambdaQueryWrapper<ForgePluginVersion>()
                        .eq(ForgePluginVersion::getPluginId, pluginId)
                        .eq(ForgePluginVersion::getStatus, VERSION_STATUS_PUBLISHED)
                        .eq(ForgePluginVersion::getReviewStatus, VERSION_REVIEW_PASSED)
                        .orderByDesc(ForgePluginVersion::getPublishedTime)
                        .orderByDesc(ForgePluginVersion::getCreateTime));
        return versions.stream().map(this::toVersionVO).toList();
    }

    @Override
    public List<ForgePluginPermissionFrontVO> listPermissions(Long pluginId, Long versionId) {
        requireOnlinePlugin(pluginId);
        requireVisibleVersion(pluginId, versionId);
        List<ForgePluginPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<ForgePluginPermission>()
                        .eq(ForgePluginPermission::getVersionId, versionId)
                        .orderByDesc(ForgePluginPermission::getRiskLevel)
                        .orderByAsc(ForgePluginPermission::getId));
        return permissions.stream().map(this::toPermissionVO).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForgePluginDownloadResultVO download(Long pluginId, Long versionId,
                                                ForgePluginDownloadRequest req, String ip, String userAgent) {
        ForgePlugin plugin = requireOnlinePlugin(pluginId);

        // 确定下载版本：未指定则取插件最新版本
        Long targetVersionId = versionId;
        if (targetVersionId == null) {
            targetVersionId = plugin.getLatestVersionId();
            if (targetVersionId == null) {
                throw new BizException(HttpStatus.NOT_FOUND, "该插件暂无可用版本");
            }
        }
        ForgePluginVersion version = requireVisibleVersion(pluginId, targetVersionId);

        // 解析下载地址：外链优先，其次由文件服务解析
        String downloadUrl = resolveDownloadUrl(version);
        if (!StringUtils.hasText(downloadUrl)) {
            writeDownloadLog(plugin.getId(), version.getId(), req, ip, userAgent,
                    DOWNLOAD_FAIL, "安装包不存在");
            throw new BizException(HttpStatus.NOT_FOUND, "安装包不存在或不可下载");
        }

        // 记录成功日志并累加下载计数
        writeDownloadLog(plugin.getId(), version.getId(), req, ip, userAgent, DOWNLOAD_SUCCESS, null);
        incrementDownloadCount(plugin.getId(), version.getId());

        return toDownloadResultVO(version, downloadUrl);
    }

    @Override
    public List<ForgePluginFrontVO> toFrontVOByIds(java.util.Collection<Long> pluginIds) {
        if (pluginIds == null || pluginIds.isEmpty()) {
            return List.of();
        }
        // 仅返回已上架插件
        List<ForgePlugin> plugins = pluginMapper.selectList(
                new LambdaQueryWrapper<ForgePlugin>()
                        .in(ForgePlugin::getId, pluginIds)
                        .eq(ForgePlugin::getStatus, PLUGIN_STATUS_ON));
        if (plugins.isEmpty()) {
            return List.of();
        }
        Map<Long, ForgePlugin> byId = plugins.stream()
                .collect(Collectors.toMap(ForgePlugin::getId, p -> p));
        Map<Long, List<ForgePluginCategoryRel>> relsByPlugin = loadRels(
                plugins.stream().map(ForgePlugin::getId).toList());
        Map<Long, String> categoryNameById = loadCategoryNames(relsByPlugin);
        // 按传入ID顺序输出，过滤掉未上架/不存在的
        return pluginIds.stream()
                .map(byId::get)
                .filter(Objects::nonNull)
                .map(p -> toListVO(p, relsByPlugin.getOrDefault(p.getId(), List.of()), categoryNameById))
                .toList();
    }

    // ============ 私有方法 ============

    private void applySort(LambdaQueryWrapper<ForgePlugin> wrapper, String sort) {
        String key = StringUtils.hasText(sort) ? sort : "featured";
        switch (key) {
            case "new" -> wrapper.orderByDesc(ForgePlugin::getCreateTime);
            case "hot" -> wrapper.orderByDesc(ForgePlugin::getDownloadCount)
                    .orderByDesc(ForgePlugin::getCreateTime);
            case "rating" -> wrapper.orderByDesc(ForgePlugin::getRatingScore)
                    .orderByDesc(ForgePlugin::getRatingCount);
            default -> wrapper.orderByDesc(ForgePlugin::getIsFeatured)
                    .orderByAsc(ForgePlugin::getSortOrder)
                    .orderByDesc(ForgePlugin::getCreateTime);
        }
    }

    /**
     * 将文件ID解析为可访问地址；ID为空或解析失败时返回 null，不影响主流程。
     */
    private String safeFileUrl(Long fileId) {
        if (fileId == null) {
            return null;
        }
        try {
            return sysFileService.getAccessUrl(fileId);
        } catch (Exception ex) {
            return null;
        }
    }

    private String resolveDownloadUrl(ForgePluginVersion version) {
        if (StringUtils.hasText(version.getPackageUrl())) {
            return version.getPackageUrl();
        }
        if (version.getPackageFileId() != null) {
            return sysFileService.getAccessUrl(version.getPackageFileId());
        }
        return null;
    }

    private void writeDownloadLog(Long pluginId, Long versionId, ForgePluginDownloadRequest req,
                                  String ip, String userAgent, Integer result, String errorMsg) {
        ForgePluginDownloadLog log = new ForgePluginDownloadLog();
        log.setUserId(UserContext.getUserId());
        log.setPluginId(pluginId);
        log.setVersionId(versionId);
        if (req != null) {
            log.setClientVersion(req.getClientVersion());
            log.setClientOs(req.getClientOs());
        }
        log.setIp(ip);
        log.setUserAgent(userAgent);
        log.setResult(result);
        log.setErrorMsg(errorMsg);
        downloadLogMapper.insert(log);
    }

    private void incrementDownloadCount(Long pluginId, Long versionId) {
        versionMapper.update(null, new LambdaUpdateWrapper<ForgePluginVersion>()
                .eq(ForgePluginVersion::getId, versionId)
                .setSql("download_count = download_count + 1"));
        pluginMapper.update(null, new LambdaUpdateWrapper<ForgePlugin>()
                .eq(ForgePlugin::getId, pluginId)
                .setSql("download_count = download_count + 1"));
    }

    private ForgePlugin requireOnlinePlugin(Long pluginId) {
        ForgePlugin plugin = pluginId == null ? null : pluginMapper.selectById(pluginId);
        if (plugin == null || plugin.getStatus() == null || plugin.getStatus() != PLUGIN_STATUS_ON) {
            throw new BizException(HttpStatus.NOT_FOUND, "插件不存在或未上架");
        }
        return plugin;
    }

    private ForgePluginVersion requireVisibleVersion(Long pluginId, Long versionId) {
        ForgePluginVersion version = versionId == null ? null : versionMapper.selectById(versionId);
        if (version == null
                || !Objects.equals(version.getPluginId(), pluginId)
                || version.getStatus() == null || version.getStatus() != VERSION_STATUS_PUBLISHED
                || version.getReviewStatus() == null || version.getReviewStatus() != VERSION_REVIEW_PASSED) {
            throw new BizException(HttpStatus.NOT_FOUND, "版本不存在或不可用");
        }
        return version;
    }

    private Map<Long, List<ForgePluginCategoryRel>> loadRels(List<Long> pluginIds) {
        if (pluginIds == null || pluginIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryRelMapper.selectList(
                        new LambdaQueryWrapper<ForgePluginCategoryRel>()
                                .in(ForgePluginCategoryRel::getPluginId, pluginIds))
                .stream().collect(Collectors.groupingBy(ForgePluginCategoryRel::getPluginId));
    }

    private Map<Long, String> loadCategoryNames(Map<Long, List<ForgePluginCategoryRel>> relsByPlugin) {
        Set<Long> categoryIds = relsByPlugin.values().stream()
                .flatMap(List::stream)
                .map(ForgePluginCategoryRel::getCategoryId)
                .collect(Collectors.toSet());
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectList(
                        new LambdaQueryWrapper<ForgePluginCategory>().in(ForgePluginCategory::getId, categoryIds))
                .stream().collect(Collectors.toMap(ForgePluginCategory::getId, ForgePluginCategory::getName));
    }

    private void fillCommon(ForgePluginFrontVO vo, ForgePlugin p,
                            List<ForgePluginCategoryRel> rels, Map<Long, String> categoryNameById) {
        vo.setId(p.getId());
        vo.setPluginKey(p.getPluginKey());
        vo.setName(p.getName());
        vo.setSummary(p.getSummary());
        vo.setType(p.getType());
        vo.setIconFileId(p.getIconFileId());
        vo.setIconUrl(safeFileUrl(p.getIconFileId()));
        vo.setCoverFileId(p.getCoverFileId());
        vo.setCoverUrl(safeFileUrl(p.getCoverFileId()));
        vo.setAuthorName(p.getAuthorName());
        vo.setPricingType(p.getPricingType());
        vo.setPrice(p.getPrice());
        vo.setOriginalPrice(p.getOriginalPrice());
        vo.setCurrency(p.getCurrency());
        vo.setPriceText(p.getPriceText());
        vo.setLatestVersion(p.getLatestVersion());
        vo.setDownloadCount(p.getDownloadCount());
        vo.setRatingScore(p.getRatingScore());
        vo.setRatingCount(p.getRatingCount());
        vo.setIsFeatured(p.getIsFeatured());
        vo.setCategoryNames(rels.stream()
                .map(ForgePluginCategoryRel::getCategoryId)
                .map(categoryNameById::get)
                .filter(Objects::nonNull)
                .toList());
    }

    private ForgePluginFrontVO toListVO(ForgePlugin p, List<ForgePluginCategoryRel> rels,
                                        Map<Long, String> categoryNameById) {
        ForgePluginFrontVO vo = new ForgePluginFrontVO();
        fillCommon(vo, p, rels, categoryNameById);
        return vo;
    }

    private ForgePluginDetailFrontVO toDetailVO(ForgePlugin p, List<ForgePluginCategoryRel> rels,
                                                Map<Long, String> categoryNameById) {
        ForgePluginDetailFrontVO vo = new ForgePluginDetailFrontVO();
        fillCommon(vo, p, rels, categoryNameById);
        vo.setDescription(p.getDescription());
        vo.setKeywords(p.getKeywords());
        vo.setHomepageUrl(p.getHomepageUrl());
        vo.setRepoUrl(p.getRepoUrl());
        vo.setLicense(p.getLicense());
        vo.setPurchaseUrl(p.getPurchaseUrl());
        vo.setLatestVersionId(p.getLatestVersionId());
        vo.setInstallCount(p.getInstallCount());
        vo.setFavoriteCount(p.getFavoriteCount());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        vo.setCategoryIds(rels.stream().map(ForgePluginCategoryRel::getCategoryId).toList());
        return vo;
    }

    private ForgePluginVersionFrontVO toVersionVO(ForgePluginVersion v) {
        ForgePluginVersionFrontVO vo = new ForgePluginVersionFrontVO();
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
        vo.setPublishedTime(v.getPublishedTime());
        return vo;
    }

    private ForgePluginPermissionFrontVO toPermissionVO(ForgePluginPermission p) {
        ForgePluginPermissionFrontVO vo = new ForgePluginPermissionFrontVO();
        vo.setPermissionCode(p.getPermissionCode());
        vo.setPermissionName(p.getPermissionName());
        vo.setDescription(p.getDescription());
        vo.setRiskLevel(p.getRiskLevel());
        vo.setRequired(p.getRequired());
        return vo;
    }

    private ForgePluginDownloadResultVO toDownloadResultVO(ForgePluginVersion v, String downloadUrl) {
        ForgePluginDownloadResultVO vo = new ForgePluginDownloadResultVO();
        vo.setPluginId(v.getPluginId());
        vo.setVersionId(v.getId());
        vo.setVersion(v.getVersion());
        vo.setPackageFileId(v.getPackageFileId());
        vo.setPackageUrl(downloadUrl);
        vo.setPackageSha256(v.getPackageSha256());
        vo.setPackageSize(v.getPackageSize());
        vo.setSignature(v.getSignature());
        return vo;
    }
}
