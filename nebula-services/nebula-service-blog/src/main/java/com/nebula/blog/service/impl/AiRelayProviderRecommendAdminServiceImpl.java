package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.AiRelayProviderRecommendPageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderRecommendRequest;
import com.nebula.blog.entity.AiRelayProvider;
import com.nebula.blog.entity.AiRelayProviderRecommend;
import com.nebula.blog.mapper.AiRelayProviderMapper;
import com.nebula.blog.mapper.AiRelayProviderRecommendMapper;
import com.nebula.blog.service.AiRelayProviderRecommendAdminService;
import com.nebula.blog.vo.admin.AiRelayProviderRecommendAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI中转服务商推荐管理服务实现
 */
@Service
@RequiredArgsConstructor
public class AiRelayProviderRecommendAdminServiceImpl implements AiRelayProviderRecommendAdminService {

    private final AiRelayProviderRecommendMapper recommendMapper;
    private final AiRelayProviderMapper providerMapper;

    @Override
    public PageResult<AiRelayProviderRecommendAdminVO> page(AiRelayProviderRecommendPageQuery query) {
        AiRelayProviderRecommendPageQuery safe = query == null ? new AiRelayProviderRecommendPageQuery() : query;
        Page<AiRelayProviderRecommend> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayProviderRecommend> wrapper = new LambdaQueryWrapper<AiRelayProviderRecommend>()
                .eq(safe.getProviderId() != null, AiRelayProviderRecommend::getProviderId, safe.getProviderId())
                .eq(safe.getStatus() != null, AiRelayProviderRecommend::getStatus, safe.getStatus())
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiRelayProviderRecommend::getRecommendReason, safe.getKeyword())
                        .or().like(AiRelayProviderRecommend::getReviewContent, safe.getKeyword()))
                .orderByAsc(AiRelayProviderRecommend::getSortOrder)
                .orderByDesc(AiRelayProviderRecommend::getRecommendTime);
        Page<AiRelayProviderRecommend> result = recommendMapper.selectPage(page, wrapper);
        List<AiRelayProviderRecommend> records = result.getRecords();
        Map<Long, String> providerNameMap = loadProviderNames(records.stream()
                .map(AiRelayProviderRecommend::getProviderId).distinct().toList());
        List<AiRelayProviderRecommendAdminVO> rows = records.stream()
                .map(e -> toVO(e, providerNameMap.get(e.getProviderId())))
                .toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayProviderRecommendAdminVO detail(Long id) {
        AiRelayProviderRecommend entity = requireRecommend(id);
        return toVO(entity, loadProviderName(entity.getProviderId()));
    }

    @Override
    public AiRelayProviderRecommendAdminVO detailByProviderId(Long providerId) {
        if (providerId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商ID不能为空");
        }
        AiRelayProviderRecommend entity = recommendMapper.selectOne(
                new LambdaQueryWrapper<AiRelayProviderRecommend>()
                        .eq(AiRelayProviderRecommend::getProviderId, providerId)
                        .last("LIMIT 1")
        );
        if (entity == null) {
            return null;
        }
        return toVO(entity, loadProviderName(providerId));
    }

    @Override
    public Long create(AiRelayProviderRecommendRequest req) {
        validateRequest(req);
        requireProvider(req.getProviderId());
        checkProviderUnique(req.getProviderId(), null);

        AiRelayProviderRecommend entity = new AiRelayProviderRecommend();
        applyRequest(entity, req);
        if (entity.getRecommendTime() == null) entity.setRecommendTime(LocalDateTime.now());
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        if (entity.getStatus() == null) entity.setStatus(1);
        recommendMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, AiRelayProviderRecommendRequest req) {
        validateRequest(req);
        AiRelayProviderRecommend existing = requireRecommend(id);
        if (req.getProviderId() != null && !req.getProviderId().equals(existing.getProviderId())) {
            requireProvider(req.getProviderId());
            checkProviderUnique(req.getProviderId(), id);
        }
        applyRequest(existing, req);
        recommendMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        requireRecommend(id);
        recommendMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        AiRelayProviderRecommend existing = requireRecommend(id);
        existing.setStatus(status);
        recommendMapper.updateById(existing);
    }

    // ============ 内部工具 ============

    private void validateRequest(AiRelayProviderRecommendRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (req.getProviderId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商ID不能为空");
        }
        if (!StringUtils.hasText(req.getRecommendReason())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "推荐原因不能为空");
        }
    }

    private AiRelayProviderRecommend requireRecommend(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "推荐ID不能为空");
        }
        AiRelayProviderRecommend entity = recommendMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "推荐不存在");
        }
        return entity;
    }

    private void requireProvider(Long providerId) {
        if (providerMapper.selectById(providerId) == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "服务商不存在");
        }
    }

    private void checkProviderUnique(Long providerId, Long excludeId) {
        LambdaQueryWrapper<AiRelayProviderRecommend> wrapper = new LambdaQueryWrapper<AiRelayProviderRecommend>()
                .eq(AiRelayProviderRecommend::getProviderId, providerId);
        if (excludeId != null) {
            wrapper.ne(AiRelayProviderRecommend::getId, excludeId);
        }
        if (recommendMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该服务商已存在推荐记录");
        }
    }

    private void applyRequest(AiRelayProviderRecommend entity, AiRelayProviderRecommendRequest req) {
        entity.setProviderId(req.getProviderId());
        entity.setRecommendReason(req.getRecommendReason());
        entity.setReviewContent(req.getReviewContent());
        entity.setReviewScore(req.getReviewScore());
        entity.setPros(req.getPros());
        entity.setCons(req.getCons());
        entity.setUseScenario(req.getUseScenario());
        entity.setFirstUseTime(req.getFirstUseTime());
        entity.setReviewTime(req.getReviewTime());
        if (req.getRecommendTime() != null) entity.setRecommendTime(req.getRecommendTime());
        if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
        if (req.getStatus() != null) entity.setStatus(req.getStatus());
    }

    private Map<Long, String> loadProviderNames(List<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) {
            return Map.of();
        }
        return providerMapper.selectBatchIds(providerIds).stream()
                .collect(Collectors.toMap(AiRelayProvider::getId, AiRelayProvider::getName, (a, b) -> a));
    }

    private String loadProviderName(Long providerId) {
        if (providerId == null) return null;
        AiRelayProvider provider = providerMapper.selectById(providerId);
        return provider != null ? provider.getName() : null;
    }

    private AiRelayProviderRecommendAdminVO toVO(AiRelayProviderRecommend entity, String providerName) {
        AiRelayProviderRecommendAdminVO vo = new AiRelayProviderRecommendAdminVO();
        vo.setId(entity.getId());
        vo.setProviderId(entity.getProviderId());
        vo.setProviderName(providerName);
        vo.setRecommendReason(entity.getRecommendReason());
        vo.setReviewContent(entity.getReviewContent());
        vo.setReviewScore(entity.getReviewScore());
        vo.setPros(entity.getPros());
        vo.setCons(entity.getCons());
        vo.setUseScenario(entity.getUseScenario());
        vo.setFirstUseTime(entity.getFirstUseTime());
        vo.setReviewTime(entity.getReviewTime());
        vo.setRecommendTime(entity.getRecommendTime());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
