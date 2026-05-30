package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.CategoryCreateRequest;
import com.nebula.blog.dto.admin.CategoryUpdateRequest;
import com.nebula.blog.entity.BlogCategory;
import com.nebula.blog.mapper.BlogCategoryMapper;
import com.nebula.blog.service.BlogCategoryAdminService;
import com.nebula.blog.vo.admin.CategoryAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 博客分类管理服务实现（管理员端）
 */
@Service
@RequiredArgsConstructor
public class BlogCategoryAdminServiceImpl implements BlogCategoryAdminService {

    private final BlogCategoryMapper categoryMapper;

    /**
     * 获取管理员端分类树
     */
    @Override
    public List<CategoryAdminVO> getAdminTree() {
        List<BlogCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<BlogCategory>().orderByAsc(BlogCategory::getSortOrder)
        );
        Map<Long, List<BlogCategory>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));
        return buildTree(byParent, 0L);
    }

    /**
     * 创建分类
     */
    @Override
    public Long create(CategoryCreateRequest req) {
        checkSlugUnique(req.getSlug(), null);
        BlogCategory category = new BlogCategory();
        category.setName(req.getName());
        category.setSlug(req.getSlug());
        category.setDescription(req.getDescription());
        category.setParentId(req.getParentId());
        category.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        categoryMapper.insert(category);
        return category.getId();
    }

    /**
     * 更新分类
     */
    @Override
    public void update(Long id, CategoryUpdateRequest req) {
        BlogCategory existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "分类不存在");
        }
        if (StringUtils.hasText(req.getSlug()) && !req.getSlug().equals(existing.getSlug())) {
            checkSlugUnique(req.getSlug(), id);
        }
        if (StringUtils.hasText(req.getName())) existing.setName(req.getName());
        if (StringUtils.hasText(req.getSlug())) existing.setSlug(req.getSlug());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getParentId() != null) existing.setParentId(req.getParentId());
        if (req.getSortOrder() != null) existing.setSortOrder(req.getSortOrder());
        categoryMapper.updateById(existing);
    }

    /**
     * 删除分类
     */
    @Override
    public void delete(Long id) {
        long childCount = categoryMapper.selectCount(
                new LambdaQueryWrapper<BlogCategory>().eq(BlogCategory::getParentId, id)
        );
        if (childCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在子分类，请先删除子分类");
        }
        categoryMapper.deleteById(id);
    }

    /**
     * 校验slug唯一性
     *
     * @param slug     分类别名
     * @param excludeId 排除的分类ID（更新时使用）
     */
    private void checkSlugUnique(String slug, Long excludeId) {
        LambdaQueryWrapper<BlogCategory> wrapper = new LambdaQueryWrapper<BlogCategory>()
                .eq(BlogCategory::getSlug, slug);
        if (excludeId != null) {
            wrapper.ne(BlogCategory::getId, excludeId);
        }
        if (categoryMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "slug 已存在");
        }
    }

    /**
     * 递归构建分类树
     *
     * @param byParent 按父ID分组的分类Map
     * @param parentId 父分类ID
     * @return 分类树列表
     */
    private List<CategoryAdminVO> buildTree(Map<Long, List<BlogCategory>> byParent, long parentId) {
        List<BlogCategory> children = byParent.getOrDefault(parentId, List.of());
        List<CategoryAdminVO> result = new ArrayList<>();
        for (BlogCategory c : children) {
            CategoryAdminVO vo = toVO(c);
            vo.setChildren(buildTree(byParent, c.getId()));
            result.add(vo);
        }
        return result;
    }

    /**
     * 实体转VO
     *
     * @param c 分类实体
     * @return 分类管理VO
     */
    private CategoryAdminVO toVO(BlogCategory c) {
        CategoryAdminVO vo = new CategoryAdminVO();
        vo.setId(c.getId());
        vo.setParentId(c.getParentId());
        vo.setName(c.getName());
        vo.setSlug(c.getSlug());
        vo.setDescription(c.getDescription());
        vo.setSortOrder(c.getSortOrder());
        vo.setCreateTime(c.getCreateTime());
        vo.setUpdateTime(c.getUpdateTime());
        return vo;
    }
}
