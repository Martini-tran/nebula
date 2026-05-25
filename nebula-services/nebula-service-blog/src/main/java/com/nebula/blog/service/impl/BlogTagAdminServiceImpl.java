package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.admin.TagCreateRequest;
import com.nebula.blog.dto.admin.TagUpdateRequest;
import com.nebula.blog.entity.BlogPostTag;
import com.nebula.blog.entity.BlogTag;
import com.nebula.blog.mapper.BlogPostTagMapper;
import com.nebula.blog.mapper.BlogTagMapper;
import com.nebula.blog.service.BlogTagAdminService;
import com.nebula.blog.vo.admin.TagAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后台标签管理服务实现
 */
@Service
@RequiredArgsConstructor
public class BlogTagAdminServiceImpl implements BlogTagAdminService {

    private static final int NAME_MAX_LENGTH = 100;
    private static final int SLUG_MAX_LENGTH = 120;

    private final BlogTagMapper tagMapper;
    private final BlogPostTagMapper postTagMapper;

    /**
     * 查询标签列表
     * 按使用次数倒序，方便文章编辑页优先选到常用标签。
     */
    @Override
    public List<TagAdminVO> list() {
        return tagMapper.selectList(
                        new LambdaQueryWrapper<BlogTag>()
                                .orderByDesc(BlogTag::getUseCount)
                                .orderByDesc(BlogTag::getCreateTime)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    /**
     * 查询标签详情
     */
    @Override
    public TagAdminVO detail(Long id) {
        return toVO(requireTag(id));
    }

    /**
     * 创建标签（find-or-create 语义）。
     * <p>
     * 若同名标签已存在，直接返回其 ID，不重复创建也不报错。
     * 这样前端在文章编辑页输入已有标签名并回车时，能正常复用已有标签。
     */
    @Override
    public Long create(TagCreateRequest req) {
        validateRequest(req);

        // 同名已存在 → 直接复用，不抛错
        BlogTag existing = tagMapper.selectOne(
                new LambdaQueryWrapper<BlogTag>().eq(BlogTag::getName, req.getName())
        );
        if (existing != null) {
            return existing.getId();
        }

        // slug 唯一校验（名称不重复时才校验 slug）
        checkSlugUnique(req.getSlug(), null);

        BlogTag tag = new BlogTag();
        tag.setName(req.getName());
        tag.setSlug(req.getSlug());
        tag.setUseCount(0);
        tagMapper.insert(tag);
        return tag.getId();
    }

    /**
     * 更新标签
     */
    @Override
    public void update(Long id, TagUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        BlogTag tag = requireTag(id);

        if (StringUtils.hasText(req.getName()) && !req.getName().equals(tag.getName())) {
            checkNameUnique(req.getName(), id);
            checkLength(req.getName(), NAME_MAX_LENGTH, "name");
            tag.setName(req.getName());
        }
        if (StringUtils.hasText(req.getSlug()) && !req.getSlug().equals(tag.getSlug())) {
            checkSlugUnique(req.getSlug(), id);
            checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
            tag.setSlug(req.getSlug());
        }
        tagMapper.updateById(tag);
    }

    /**
     * 删除标签
     * 删除前先清理文章-标签关系，避免保留脏关联数据。
     */
    @Override
    public void delete(Long id) {
        requireTag(id);
        postTagMapper.delete(new LambdaQueryWrapper<BlogPostTag>().eq(BlogPostTag::getTagId, id));
        tagMapper.deleteById(id);
    }

    /**
     * 请求参数校验
     */
    private void validateRequest(TagCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标签名称不能为空");
        }
        if (!StringUtils.hasText(req.getSlug())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标签 slug 不能为空");
        }
        checkLength(req.getName(), NAME_MAX_LENGTH, "name");
        checkLength(req.getSlug(), SLUG_MAX_LENGTH, "slug");
    }

    /**
     * 校验标签是否存在
     */
    private BlogTag requireTag(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标签ID不能为空");
        }
        BlogTag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "标签不存在");
        }
        return tag;
    }

    /**
     * 校验名称唯一
     */
    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<BlogTag> wrapper = new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getName, name);
        if (excludeId != null) {
            wrapper.ne(BlogTag::getId, excludeId);
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标签名称已存在");
        }
    }

    /**
     * 校验 slug 唯一
     */
    private void checkSlugUnique(String slug, Long excludeId) {
        LambdaQueryWrapper<BlogTag> wrapper = new LambdaQueryWrapper<BlogTag>()
                .eq(BlogTag::getSlug, slug);
        if (excludeId != null) {
            wrapper.ne(BlogTag::getId, excludeId);
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "slug 已存在");
        }
    }

    /**
     * 校验文本长度
     */
    private void checkLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BizException(HttpStatus.BAD_REQUEST, fieldName + " 长度不能超过 " + maxLength);
        }
    }

    /**
     * 实体转 VO
     */
    private TagAdminVO toVO(BlogTag tag) {
        TagAdminVO vo = new TagAdminVO();
        vo.setId(tag.getId());
        vo.setName(tag.getName());
        vo.setSlug(tag.getSlug());
        vo.setUseCount(tag.getUseCount());
        vo.setCreateTime(tag.getCreateTime());
        vo.setUpdateTime(tag.getUpdateTime());
        return vo;
    }
}
