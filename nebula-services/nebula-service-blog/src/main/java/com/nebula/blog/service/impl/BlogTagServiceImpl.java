package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.entity.BlogTag;
import com.nebula.blog.mapper.BlogTagMapper;
import com.nebula.blog.service.BlogTagService;
import com.nebula.blog.vo.front.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 博客标签服务实现
 */
@Service
@RequiredArgsConstructor
public class BlogTagServiceImpl implements BlogTagService {

    private final BlogTagMapper tagMapper;

    /**
     * 获取热门标签（按使用次数排序）
     */
    @Override
    public List<TagVO> getPopularTags(int limit) {
        List<BlogTag> tags = tagMapper.selectList(
                new LambdaQueryWrapper<BlogTag>()
                        .orderByDesc(BlogTag::getUseCount)
                        .last("LIMIT " + limit)
        );
        return tags.stream().map(t -> {
            TagVO vo = new TagVO();
            vo.setId(t.getId());
            vo.setName(t.getName());
            vo.setSlug(t.getSlug());
            vo.setPostCount(t.getUseCount());
            return vo;
        }).collect(Collectors.toList());
    }
}
