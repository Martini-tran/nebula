package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.forge.dto.admin.ForgePluginReviewAuditRequest;
import com.nebula.forge.dto.admin.ForgePluginReviewPageQuery;
import com.nebula.forge.dto.admin.ForgePluginReviewReplyRequest;
import com.nebula.forge.entity.ForgePluginReview;
import com.nebula.forge.mapper.ForgePluginReviewMapper;
import com.nebula.forge.service.ForgePluginReviewAdminService;
import com.nebula.forge.vo.admin.ForgePluginReviewAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 插件评价管理服务实现（管理员端）
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginReviewAdminServiceImpl implements ForgePluginReviewAdminService {

    private final ForgePluginReviewMapper reviewMapper;

    @Override
    public PageResult<ForgePluginReviewAdminVO> page(ForgePluginReviewPageQuery query) {
        ForgePluginReviewPageQuery safe = query == null ? new ForgePluginReviewPageQuery() : query;
        Page<ForgePluginReview> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<ForgePluginReview> wrapper = new LambdaQueryWrapper<ForgePluginReview>()
                .eq(safe.getPluginId() != null, ForgePluginReview::getPluginId, safe.getPluginId())
                .eq(safe.getUserId() != null, ForgePluginReview::getUserId, safe.getUserId())
                .eq(safe.getStatus() != null, ForgePluginReview::getStatus, safe.getStatus())
                .eq(safe.getRating() != null, ForgePluginReview::getRating, safe.getRating())
                .orderByDesc(ForgePluginReview::getCreateTime);
        Page<ForgePluginReview> result = reviewMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public ForgePluginReviewAdminVO detail(Long id) {
        return toVO(requireReview(id));
    }

    @Override
    public void audit(Long id, ForgePluginReviewAuditRequest req) {
        if (req == null || req.getStatus() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        ForgePluginReview existing = requireReview(id);
        existing.setStatus(req.getStatus());
        existing.setAuditRemark(req.getAuditRemark());
        reviewMapper.updateById(existing);
    }

    @Override
    public void reply(Long id, ForgePluginReviewReplyRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        ForgePluginReview existing = requireReview(id);
        existing.setReplyContent(req.getReplyContent());
        existing.setReplyBy(UserContext.getUserId());
        existing.setReplyTime(LocalDateTime.now());
        reviewMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        requireReview(id);
        reviewMapper.deleteById(id);
    }

    private ForgePluginReview requireReview(Long id) {
        ForgePluginReview review = id == null ? null : reviewMapper.selectById(id);
        if (review == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "评价不存在");
        }
        return review;
    }

    private ForgePluginReviewAdminVO toVO(ForgePluginReview r) {
        ForgePluginReviewAdminVO vo = new ForgePluginReviewAdminVO();
        vo.setId(r.getId());
        vo.setPluginId(r.getPluginId());
        vo.setVersionId(r.getVersionId());
        vo.setUserId(r.getUserId());
        vo.setRating(r.getRating());
        vo.setContent(r.getContent());
        vo.setReplyContent(r.getReplyContent());
        vo.setReplyBy(r.getReplyBy());
        vo.setReplyTime(r.getReplyTime());
        vo.setLikeCount(r.getLikeCount());
        vo.setStatus(r.getStatus());
        vo.setAuditRemark(r.getAuditRemark());
        vo.setCreateTime(r.getCreateTime());
        vo.setUpdateTime(r.getUpdateTime());
        return vo;
    }
}
