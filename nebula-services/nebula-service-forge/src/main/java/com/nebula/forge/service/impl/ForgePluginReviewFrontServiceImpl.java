package com.nebula.forge.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.forge.dto.front.ForgePluginReviewCreateRequest;
import com.nebula.forge.dto.front.ForgePluginReviewFrontPageQuery;
import com.nebula.forge.entity.ForgePlugin;
import com.nebula.forge.entity.ForgePluginReview;
import com.nebula.forge.mapper.ForgePluginMapper;
import com.nebula.forge.mapper.ForgePluginReviewMapper;
import com.nebula.forge.service.ForgePluginReviewFrontService;
import com.nebula.forge.vo.front.ForgePluginReviewFrontVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 插件评价前台服务实现
 *
 * @author nebula
 */
@Service
@RequiredArgsConstructor
public class ForgePluginReviewFrontServiceImpl implements ForgePluginReviewFrontService {

    /** 插件状态：已上架 */
    private static final int PLUGIN_STATUS_ON = 1;
    /** 评价状态：展示 */
    private static final int REVIEW_STATUS_SHOW = 1;

    private final ForgePluginReviewMapper reviewMapper;
    private final ForgePluginMapper pluginMapper;

    @Override
    public PageResult<ForgePluginReviewFrontVO> page(Long pluginId, ForgePluginReviewFrontPageQuery query) {
        ForgePluginReviewFrontPageQuery safe = query == null ? new ForgePluginReviewFrontPageQuery() : query;
        Page<ForgePluginReview> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<ForgePluginReview> wrapper = new LambdaQueryWrapper<ForgePluginReview>()
                .eq(ForgePluginReview::getPluginId, pluginId)
                .eq(ForgePluginReview::getStatus, REVIEW_STATUS_SHOW);
        applySort(wrapper, safe.getSort());
        Page<ForgePluginReview> result = reviewMapper.selectPage(page, wrapper);
        return PageResult.of(result.getRecords().stream().map(this::toVO).toList(),
                result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForgePluginReviewFrontVO submit(Long pluginId, ForgePluginReviewCreateRequest req) {
        Long userId = requireLoginUserId();
        if (req == null || req.getRating() == null || req.getRating() < 1 || req.getRating() > 5) {
            throw new BizException(HttpStatus.BAD_REQUEST, "评分须为1-5");
        }
        requireOnlinePlugin(pluginId);

        // 唯一键 (plugin_id, user_id)：存在则更新，否则新增
        ForgePluginReview existing = reviewMapper.selectOne(
                new LambdaQueryWrapper<ForgePluginReview>()
                        .eq(ForgePluginReview::getPluginId, pluginId)
                        .eq(ForgePluginReview::getUserId, userId)
                        .last("limit 1"));
        if (existing == null) {
            ForgePluginReview review = new ForgePluginReview();
            review.setPluginId(pluginId);
            review.setUserId(userId);
            review.setVersionId(req.getVersionId());
            review.setRating(req.getRating());
            review.setContent(req.getContent());
            review.setLikeCount(0);
            review.setStatus(REVIEW_STATUS_SHOW);
            reviewMapper.insert(review);
            existing = review;
        } else {
            existing.setVersionId(req.getVersionId());
            existing.setRating(req.getRating());
            existing.setContent(req.getContent());
            existing.setStatus(REVIEW_STATUS_SHOW);
            reviewMapper.updateById(existing);
        }

        recalcRating(pluginId);
        return toVO(reviewMapper.selectById(existing.getId()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteMine(Long pluginId) {
        Long userId = requireLoginUserId();
        int deleted = reviewMapper.delete(
                new LambdaQueryWrapper<ForgePluginReview>()
                        .eq(ForgePluginReview::getPluginId, pluginId)
                        .eq(ForgePluginReview::getUserId, userId));
        if (deleted > 0) {
            recalcRating(pluginId);
        }
    }

    @Override
    public void like(Long reviewId) {
        requireLoginUserId();
        ForgePluginReview review = reviewId == null ? null : reviewMapper.selectById(reviewId);
        if (review == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "评价不存在");
        }
        reviewMapper.update(null, new LambdaUpdateWrapper<ForgePluginReview>()
                .eq(ForgePluginReview::getId, reviewId)
                .setSql("like_count = like_count + 1"));
    }

    // ============ 私有方法 ============

    private void applySort(LambdaQueryWrapper<ForgePluginReview> wrapper, String sort) {
        String key = StringUtils.hasText(sort) ? sort : "new";
        switch (key) {
            case "rating" -> wrapper.orderByDesc(ForgePluginReview::getRating)
                    .orderByDesc(ForgePluginReview::getCreateTime);
            case "like" -> wrapper.orderByDesc(ForgePluginReview::getLikeCount)
                    .orderByDesc(ForgePluginReview::getCreateTime);
            default -> wrapper.orderByDesc(ForgePluginReview::getCreateTime);
        }
    }

    /**
     * 重算插件评分：取该插件所有「展示中」评价，count 为评分人数，avg 为平均分（保留1位）。
     */
    private void recalcRating(Long pluginId) {
        List<ForgePluginReview> shown = reviewMapper.selectList(
                new LambdaQueryWrapper<ForgePluginReview>()
                        .select(ForgePluginReview::getRating)
                        .eq(ForgePluginReview::getPluginId, pluginId)
                        .eq(ForgePluginReview::getStatus, REVIEW_STATUS_SHOW));
        int count = shown.size();
        BigDecimal score = BigDecimal.ZERO;
        if (count > 0) {
            long sum = shown.stream().mapToLong(r -> r.getRating() == null ? 0 : r.getRating()).sum();
            score = BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(count), 1, RoundingMode.HALF_UP);
        }
        ForgePlugin update = new ForgePlugin();
        update.setId(pluginId);
        update.setRatingScore(score);
        update.setRatingCount(count);
        pluginMapper.updateById(update);
    }

    private Long requireLoginUserId() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BizException(HttpStatus.UNAUTHORIZED, "请先登录");
        }
        return userId;
    }

    private void requireOnlinePlugin(Long pluginId) {
        ForgePlugin plugin = pluginId == null ? null : pluginMapper.selectById(pluginId);
        if (plugin == null || plugin.getStatus() == null || plugin.getStatus() != PLUGIN_STATUS_ON) {
            throw new BizException(HttpStatus.NOT_FOUND, "插件不存在或未上架");
        }
    }

    private ForgePluginReviewFrontVO toVO(ForgePluginReview r) {
        ForgePluginReviewFrontVO vo = new ForgePluginReviewFrontVO();
        vo.setId(r.getId());
        vo.setPluginId(r.getPluginId());
        vo.setUserId(r.getUserId());
        vo.setVersionId(r.getVersionId());
        vo.setRating(r.getRating());
        vo.setContent(r.getContent());
        vo.setReplyContent(r.getReplyContent());
        vo.setReplyTime(r.getReplyTime());
        vo.setLikeCount(r.getLikeCount());
        vo.setCreateTime(r.getCreateTime());
        return vo;
    }
}
