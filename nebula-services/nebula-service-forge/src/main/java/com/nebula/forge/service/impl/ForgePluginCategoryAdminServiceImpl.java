package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.forge.dto.admin.ForgePluginCategoryCreateRequest;
import com.nebula.forge.dto.admin.ForgePluginCategoryPageQuery;
import com.nebula.forge.dto.admin.ForgePluginCategoryUpdateRequest;
import com.nebula.forge.entity.ForgePluginCategory;
import com.nebula.forge.entity.ForgePluginCategoryRel;
import com.nebula.forge.mapper.ForgePluginCategoryMapper;
import com.nebula.forge.mapper.ForgePluginCategoryRelMapper;
import com.nebula.forge.service.ForgePluginCategoryAdminService;
import com.nebula.forge.vo.admin.ForgePluginCategoryAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 插件分类管理服务实现（管理员端）
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginCategoryAdminServiceImpl implements ForgePluginCategoryAdminService {

    private final ForgePluginCategoryMapper categoryMapper;
    private final ForgePluginCategoryRelMapper categoryRelMapper;

    @Override
    public PageResult<ForgePluginCategoryAdminVO> page(ForgePluginCategoryPageQuery query) {
        ForgePluginCategoryPageQuery safe = query == null ? new ForgePluginCategoryPageQuery() : query;
        Page<ForgePluginCategory> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<ForgePluginCategory> wrapper = new LambdaQueryWrapper<ForgePluginCategory>()
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(ForgePluginCategory::getCode, safe.getKeyword())
                        .or()
                        .like(ForgePluginCategory::getName, safe.getKeyword()))
                .eq(safe.getStatus() != null, ForgePluginCategory::getStatus, safe.getStatus())
                .orderByAsc(ForgePluginCategory::getSortOrder)
                .orderByDesc(ForgePluginCategory::getCreateTime);
        Page<ForgePluginCategory> result = categoryMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ForgePluginCategoryAdminVO detail(Long id) {
        return toVO(requireCategory(id));
    }

    @Override
    public Long create(ForgePluginCategoryCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        checkCodeUnique(req.getCode(), null);
        ForgePluginCategory entity = new ForgePluginCategory();
        entity.setCode(req.getCode());
        entity.setName(req.getName());
        entity.setDescription(req.getDescription());
        entity.setIconFileId(req.getIconFileId());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        categoryMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, ForgePluginCategoryUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        ForgePluginCategory existing = requireCategory(id);
        if (StringUtils.hasText(req.getCode()) && !req.getCode().equals(existing.getCode())) {
            checkCodeUnique(req.getCode(), id);
            existing.setCode(req.getCode());
        }
        if (StringUtils.hasText(req.getName())) existing.setName(req.getName());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getIconFileId() != null) existing.setIconFileId(req.getIconFileId());
        if (req.getSortOrder() != null) existing.setSortOrder(req.getSortOrder());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        categoryMapper.updateById(existing);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        ForgePluginCategory existing = requireCategory(id);
        existing.setStatus(status);
        categoryMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        requireCategory(id);
        long relCount = categoryRelMapper.selectCount(
                new LambdaQueryWrapper<ForgePluginCategoryRel>().eq(ForgePluginCategoryRel::getCategoryId, id)
        );
        if (relCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该分类下存在已关联插件，请先解除关联");
        }
        categoryMapper.deleteById(id);
    }

    private ForgePluginCategory requireCategory(Long id) {
        ForgePluginCategory category = id == null ? null : categoryMapper.selectById(id);
        if (category == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "插件分类不存在");
        }
        return category;
    }

    private void checkCodeUnique(String code, Long excludeId) {
        LambdaQueryWrapper<ForgePluginCategory> wrapper = new LambdaQueryWrapper<ForgePluginCategory>()
                .eq(ForgePluginCategory::getCode, code);
        if (excludeId != null) {
            wrapper.ne(ForgePluginCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "分类编码已存在");
        }
    }

    private ForgePluginCategoryAdminVO toVO(ForgePluginCategory c) {
        ForgePluginCategoryAdminVO vo = new ForgePluginCategoryAdminVO();
        vo.setId(c.getId());
        vo.setCode(c.getCode());
        vo.setName(c.getName());
        vo.setDescription(c.getDescription());
        vo.setIconFileId(c.getIconFileId());
        vo.setSortOrder(c.getSortOrder());
        vo.setStatus(c.getStatus());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }
}
