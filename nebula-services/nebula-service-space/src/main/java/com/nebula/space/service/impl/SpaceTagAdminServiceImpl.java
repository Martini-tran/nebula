package com.nebula.space.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import com.nebula.space.dto.admin.SpaceTagCreateRequest;
import com.nebula.space.dto.admin.SpaceTagUpdateRequest;
import com.nebula.space.entity.SpaceBookmarkTag;
import com.nebula.space.entity.SpaceTag;
import com.nebula.space.mapper.SpaceBookmarkTagMapper;
import com.nebula.space.mapper.SpaceTagMapper;
import com.nebula.space.service.SpaceTagAdminService;
import com.nebula.space.vo.admin.SpaceTagAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 后台空间标签管理服务实现
 */
@Service
@RequiredArgsConstructor
public class SpaceTagAdminServiceImpl implements SpaceTagAdminService {

    private final SpaceTagMapper tagMapper;
    private final SpaceBookmarkTagMapper bookmarkTagMapper;

    @Override
    public List<SpaceTagAdminVO> list() {
        Long userId = requireUserId();
        return tagMapper.selectList(
                        new LambdaQueryWrapper<SpaceTag>()
                                .eq(SpaceTag::getUserId, userId)
                                .orderByAsc(SpaceTag::getSortOrder)
                                .orderByDesc(SpaceTag::getCreateTime)
                ).stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public SpaceTagAdminVO detail(Long id) {
        return toVO(requireTag(id));
    }

    /**
     * 创建标签（find-or-create 语义）
     * 同名复用，避免给前端再处理重复名异常
     */
    @Override
    public Long create(SpaceTagCreateRequest req) {
        Long userId = requireUserId();
        String name = req.getName().trim();

        SpaceTag existing = tagMapper.selectOne(
                new LambdaQueryWrapper<SpaceTag>()
                        .eq(SpaceTag::getUserId, userId)
                        .eq(SpaceTag::getName, name)
                        .last("limit 1")
        );
        if (existing != null) {
            return existing.getId();
        }

        SpaceTag tag = new SpaceTag();
        tag.setUserId(userId);
        tag.setName(name);
        tag.setColor(req.getColor());
        tag.setSortOrder(req.getSortOrder() == null ? 0 : req.getSortOrder());
        tag.setRemark(req.getRemark());
        tagMapper.insert(tag);
        return tag.getId();
    }

    @Override
    public void update(Long id, SpaceTagUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        SpaceTag tag = requireTag(id);

        if (StringUtils.hasText(req.getName()) && !req.getName().equals(tag.getName())) {
            checkNameUnique(tag.getUserId(), req.getName(), id);
            tag.setName(req.getName().trim());
        }
        if (req.getColor() != null) {
            tag.setColor(req.getColor());
        }
        if (req.getSortOrder() != null) {
            tag.setSortOrder(req.getSortOrder());
        }
        if (req.getRemark() != null) {
            tag.setRemark(req.getRemark());
        }
        tagMapper.updateById(tag);
    }

    /**
     * 删除标签
     * 同步清理 space_bookmark_tag 中所有引用，避免悬挂关联
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        SpaceTag tag = requireTag(id);
        bookmarkTagMapper.delete(
                new LambdaQueryWrapper<SpaceBookmarkTag>().eq(SpaceBookmarkTag::getTagId, tag.getId())
        );
        tagMapper.deleteById(tag.getId());
    }

    // ----------------------------------------------------------------- 内部工具

    private void checkNameUnique(Long userId, String name, Long excludeId) {
        LambdaQueryWrapper<SpaceTag> wrapper = new LambdaQueryWrapper<SpaceTag>()
                .eq(SpaceTag::getUserId, userId)
                .eq(SpaceTag::getName, name.trim());
        if (excludeId != null) {
            wrapper.ne(SpaceTag::getId, excludeId);
        }
        if (tagMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.CONFLICT, "标签名称已存在");
        }
    }

    private SpaceTag requireTag(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "标签ID不能为空");
        }
        SpaceTag tag = tagMapper.selectById(id);
        if (tag == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "标签不存在");
        }
        Long currentUserId = requireUserId();
        if (!UserContext.hasRole("super_admin") && !tag.getUserId().equals(currentUserId)) {
            throw new BizException(HttpStatus.FORBIDDEN, "无权操作该标签");
        }
        return tag;
    }

    private Long requireUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "未获取到当前用户");
        }
        return userId;
    }

    private SpaceTagAdminVO toVO(SpaceTag tag) {
        SpaceTagAdminVO vo = new SpaceTagAdminVO();
        vo.setId(tag.getId());
        vo.setUserId(tag.getUserId());
        vo.setName(tag.getName());
        vo.setColor(tag.getColor());
        vo.setSortOrder(tag.getSortOrder());
        vo.setRemark(tag.getRemark());
        vo.setCreateTime(tag.getCreateTime());
        vo.setUpdateTime(tag.getUpdateTime());
        return vo;
    }
}
