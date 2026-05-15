package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.entity.BlogCategory;
import com.nebula.blog.mapper.BlogCategoryMapper;
import com.nebula.blog.service.BlogCategoryService;
import com.nebula.blog.vo.front.CategoryTreeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogCategoryServiceImpl implements BlogCategoryService {

    private final BlogCategoryMapper categoryMapper;

    @Override
    public List<CategoryTreeVO> getCategoryTree(Long parentId) {
        List<BlogCategory> all = categoryMapper.selectList(
                new LambdaQueryWrapper<BlogCategory>().orderByAsc(BlogCategory::getSortOrder)
        );

        // group by parentId
        Map<Long, List<BlogCategory>> byParent = all.stream()
                .collect(Collectors.groupingBy(c -> c.getParentId() == null ? 0L : c.getParentId()));

        long rootKey = parentId != null ? parentId : 0L;
        return buildTree(byParent, rootKey);
    }

    private List<CategoryTreeVO> buildTree(Map<Long, List<BlogCategory>> byParent, long parentId) {
        List<BlogCategory> children = byParent.getOrDefault(parentId, List.of());
        List<CategoryTreeVO> result = new ArrayList<>();
        for (BlogCategory c : children) {
            CategoryTreeVO vo = new CategoryTreeVO();
            vo.setId(c.getId());
            vo.setName(c.getName());
            vo.setSlug(c.getSlug());
            vo.setChildren(buildTree(byParent, c.getId()));
            result.add(vo);
        }
        return result;
    }
}
