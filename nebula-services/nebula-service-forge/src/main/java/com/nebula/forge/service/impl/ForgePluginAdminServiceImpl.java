package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.forge.dto.admin.ForgePluginCategoryBindRequest;
import com.nebula.forge.dto.admin.ForgePluginCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginPageQuery;
import com.nebula.forge.dto.admin.ForgePluginUpdateRequest;
import com.nebula.forge.entity.ForgePlugin;
import com.nebula.forge.entity.ForgePluginCategory;
import com.nebula.forge.entity.ForgePluginCategoryRel;
import com.nebula.forge.entity.ForgePluginVersion;
import com.nebula.forge.mapper.ForgePluginCategoryMapper;
import com.nebula.forge.mapper.ForgePluginCategoryRelMapper;
import com.nebula.forge.mapper.ForgePluginMapper;
import com.nebula.forge.mapper.ForgePluginVersionMapper;
import com.nebula.forge.service.ForgePluginAdminService;
import com.nebula.forge.vo.admin.ForgePluginAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 插件管理服务实现（管理员端）
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginAdminServiceImpl implements ForgePluginAdminService {

    private final ForgePluginMapper pluginMapper;
    private final ForgePluginVersionMapper versionMapper;
    private final ForgePluginCategoryMapper categoryMapper;
    private final ForgePluginCategoryRelMapper categoryRelMapper;

    @Override
    public PageResult<ForgePluginAdminVO> page(ForgePluginPageQuery query) {
        ForgePluginPageQuery safe = query == null ? new ForgePluginPageQuery() : query;

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
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(ForgePlugin::getName, safe.getKeyword())
                        .or().like(ForgePlugin::getPluginKey, safe.getKeyword())
                        .or().like(ForgePlugin::getSummary, safe.getKeyword()))
                .eq(safe.getStatus() != null, ForgePlugin::getStatus, safe.getStatus())
                .eq(StringUtils.hasText(safe.getType()), ForgePlugin::getType, safe.getType())
                .eq(safe.getPricingType() != null, ForgePlugin::getPricingType, safe.getPricingType())
                .eq(safe.getIsFeatured() != null, ForgePlugin::getIsFeatured, safe.getIsFeatured())
                .in(pluginIdsByCategory != null, ForgePlugin::getId, pluginIdsByCategory)
                .orderByDesc(ForgePlugin::getIsFeatured)
                .orderByAsc(ForgePlugin::getSortOrder)
                .orderByDesc(ForgePlugin::getCreateTime);
        Page<ForgePlugin> result = pluginMapper.selectPage(page, wrapper);

        List<ForgePlugin> records = result.getRecords();
        Map<Long, List<ForgePluginCategoryRel>> relsByPlugin = loadRels(
                records.stream().map(ForgePlugin::getId).toList());
        Map<Long, String> categoryNameById = loadCategoryNames(relsByPlugin);

        List<ForgePluginAdminVO> rows = records.stream()
                .map(p -> toVO(p, relsByPlugin.getOrDefault(p.getId(), List.of()), categoryNameById))
                .toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ForgePluginAdminVO detail(Long id) {
        ForgePlugin plugin = requirePlugin(id);
        Map<Long, List<ForgePluginCategoryRel>> relsByPlugin = loadRels(List.of(id));
        Map<Long, String> categoryNameById = loadCategoryNames(relsByPlugin);
        return toVO(plugin, relsByPlugin.getOrDefault(id, List.of()), categoryNameById);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(ForgePluginCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        checkPluginKeyUnique(req.getPluginKey(), null);

        ForgePlugin entity = new ForgePlugin();
        entity.setPluginKey(req.getPluginKey());
        entity.setName(req.getName());
        entity.setType(StringUtils.hasText(req.getType()) ? req.getType() : "inline");
        entity.setSummary(req.getSummary());
        entity.setDescription(req.getDescription());
        entity.setKeywords(req.getKeywords());
        entity.setIconFileId(req.getIconFileId());
        entity.setCoverFileId(req.getCoverFileId());
        entity.setAuthorUserId(req.getAuthorUserId());
        entity.setAuthorName(req.getAuthorName());
        entity.setHomepageUrl(req.getHomepageUrl());
        entity.setRepoUrl(req.getRepoUrl());
        entity.setLicense(req.getLicense());
        entity.setPricingType(req.getPricingType() != null ? req.getPricingType() : 1);
        entity.setPrice(req.getPrice());
        entity.setOriginalPrice(req.getOriginalPrice());
        entity.setCurrency(StringUtils.hasText(req.getCurrency()) ? req.getCurrency() : "CNY");
        entity.setPriceText(req.getPriceText());
        entity.setPurchaseUrl(req.getPurchaseUrl());
        entity.setIsFeatured(req.getIsFeatured() != null ? req.getIsFeatured() : 0);
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setStatus(req.getStatus() != null ? req.getStatus() : 0);
        entity.setRemark(req.getRemark());
        pluginMapper.insert(entity);

        if (req.getCategoryIds() != null) {
            rebindCategories(entity.getId(), req.getCategoryIds());
        }
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, ForgePluginUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        ForgePlugin existing = requirePlugin(id);

        if (StringUtils.hasText(req.getPluginKey()) && !req.getPluginKey().equals(existing.getPluginKey())) {
            checkPluginKeyUnique(req.getPluginKey(), id);
            existing.setPluginKey(req.getPluginKey());
        }
        if (StringUtils.hasText(req.getName())) existing.setName(req.getName());
        if (StringUtils.hasText(req.getType())) existing.setType(req.getType());
        if (req.getSummary() != null) existing.setSummary(req.getSummary());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getKeywords() != null) existing.setKeywords(req.getKeywords());
        if (req.getIconFileId() != null) existing.setIconFileId(req.getIconFileId());
        if (req.getCoverFileId() != null) existing.setCoverFileId(req.getCoverFileId());
        if (req.getAuthorUserId() != null) existing.setAuthorUserId(req.getAuthorUserId());
        if (req.getAuthorName() != null) existing.setAuthorName(req.getAuthorName());
        if (req.getHomepageUrl() != null) existing.setHomepageUrl(req.getHomepageUrl());
        if (req.getRepoUrl() != null) existing.setRepoUrl(req.getRepoUrl());
        if (req.getLicense() != null) existing.setLicense(req.getLicense());
        if (req.getPricingType() != null) existing.setPricingType(req.getPricingType());
        if (req.getPrice() != null) existing.setPrice(req.getPrice());
        if (req.getOriginalPrice() != null) existing.setOriginalPrice(req.getOriginalPrice());
        if (StringUtils.hasText(req.getCurrency())) existing.setCurrency(req.getCurrency());
        if (req.getPriceText() != null) existing.setPriceText(req.getPriceText());
        if (req.getPurchaseUrl() != null) existing.setPurchaseUrl(req.getPurchaseUrl());
        if (req.getIsFeatured() != null) existing.setIsFeatured(req.getIsFeatured());
        if (req.getSortOrder() != null) existing.setSortOrder(req.getSortOrder());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        if (req.getRemark() != null) existing.setRemark(req.getRemark());
        pluginMapper.updateById(existing);

        if (req.getCategoryIds() != null) {
            rebindCategories(id, req.getCategoryIds());
        }
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        ForgePlugin existing = requirePlugin(id);
        existing.setStatus(status);
        pluginMapper.updateById(existing);
    }

    @Override
    public void updateFeatured(Long id, Integer isFeatured) {
        if (isFeatured == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "推荐标记不能为空");
        }
        ForgePlugin existing = requirePlugin(id);
        existing.setIsFeatured(isFeatured);
        pluginMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindCategories(Long id, ForgePluginCategoryBindRequest req) {
        requirePlugin(id);
        List<Long> categoryIds = req == null ? null : req.getCategoryIds();
        rebindCategories(id, categoryIds == null ? Collections.emptyList() : categoryIds);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requirePlugin(id);
        long versionCount = versionMapper.selectCount(
                new LambdaQueryWrapper<ForgePluginVersion>().eq(ForgePluginVersion::getPluginId, id)
        );
        if (versionCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该插件存在版本，请先删除版本");
        }
        categoryRelMapper.delete(
                new LambdaQueryWrapper<ForgePluginCategoryRel>().eq(ForgePluginCategoryRel::getPluginId, id)
        );
        pluginMapper.deleteById(id);
    }

    // ============ 私有方法 ============

    private void rebindCategories(Long pluginId, List<Long> categoryIds) {
        categoryRelMapper.delete(
                new LambdaQueryWrapper<ForgePluginCategoryRel>().eq(ForgePluginCategoryRel::getPluginId, pluginId)
        );
        if (categoryIds == null || categoryIds.isEmpty()) {
            return;
        }
        Set<Long> distinctIds = new LinkedHashSet<>(categoryIds);
        long existCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<ForgePluginCategory>().in(ForgePluginCategory::getId, distinctIds)
        );
        if (existCount != distinctIds.size()) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在无效的分类ID");
        }
        for (Long categoryId : distinctIds) {
            ForgePluginCategoryRel rel = new ForgePluginCategoryRel();
            rel.setPluginId(pluginId);
            rel.setCategoryId(categoryId);
            categoryRelMapper.insert(rel);
        }
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

    private ForgePlugin requirePlugin(Long id) {
        ForgePlugin plugin = id == null ? null : pluginMapper.selectById(id);
        if (plugin == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "插件不存在");
        }
        return plugin;
    }

    private void checkPluginKeyUnique(String pluginKey, Long excludeId) {
        LambdaQueryWrapper<ForgePlugin> wrapper = new LambdaQueryWrapper<ForgePlugin>()
                .eq(ForgePlugin::getPluginKey, pluginKey);
        if (excludeId != null) {
            wrapper.ne(ForgePlugin::getId, excludeId);
        }
        if (pluginMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "插件标识已存在");
        }
    }

    private ForgePluginAdminVO toVO(ForgePlugin p,
                                    List<ForgePluginCategoryRel> rels,
                                    Map<Long, String> categoryNameById) {
        ForgePluginAdminVO vo = new ForgePluginAdminVO();
        vo.setId(p.getId());
        vo.setPluginKey(p.getPluginKey());
        vo.setName(p.getName());
        vo.setType(p.getType());
        vo.setSummary(p.getSummary());
        vo.setDescription(p.getDescription());
        vo.setKeywords(p.getKeywords());
        vo.setIconFileId(p.getIconFileId());
        vo.setCoverFileId(p.getCoverFileId());
        vo.setAuthorUserId(p.getAuthorUserId());
        vo.setAuthorName(p.getAuthorName());
        vo.setHomepageUrl(p.getHomepageUrl());
        vo.setRepoUrl(p.getRepoUrl());
        vo.setLicense(p.getLicense());
        vo.setPricingType(p.getPricingType());
        vo.setPrice(p.getPrice());
        vo.setOriginalPrice(p.getOriginalPrice());
        vo.setCurrency(p.getCurrency());
        vo.setPriceText(p.getPriceText());
        vo.setPurchaseUrl(p.getPurchaseUrl());
        vo.setLatestVersionId(p.getLatestVersionId());
        vo.setLatestVersion(p.getLatestVersion());
        vo.setDownloadCount(p.getDownloadCount());
        vo.setInstallCount(p.getInstallCount());
        vo.setFavoriteCount(p.getFavoriteCount());
        vo.setRatingScore(p.getRatingScore());
        vo.setRatingCount(p.getRatingCount());
        vo.setIsFeatured(p.getIsFeatured());
        vo.setSortOrder(p.getSortOrder());
        vo.setStatus(p.getStatus());
        vo.setRemark(p.getRemark());
        vo.setCreateTime(p.getCreateTime());
        vo.setUpdateTime(p.getUpdateTime());
        List<Long> categoryIds = rels.stream().map(ForgePluginCategoryRel::getCategoryId).toList();
        vo.setCategoryIds(categoryIds);
        vo.setCategoryNames(categoryIds.stream()
                .map(categoryNameById::get)
                .filter(java.util.Objects::nonNull)
                .toList());
        return vo;
    }
}
