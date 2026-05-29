package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.front.AiRelayRecommendFrontPageQuery;
import com.nebula.blog.entity.AiRelayProvider;
import com.nebula.blog.entity.AiRelayProviderRecharge;
import com.nebula.blog.entity.AiRelayProviderRecommend;
import com.nebula.blog.mapper.AiRelayProviderMapper;
import com.nebula.blog.mapper.AiRelayProviderRechargeMapper;
import com.nebula.blog.mapper.AiRelayProviderRecommendMapper;
import com.nebula.blog.service.AiRelayRecommendFrontService;
import com.nebula.blog.vo.front.AiRelayRecommendFrontVO;
import com.nebula.common.core.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI 中转推荐前台服务实现
 */
@Service
@RequiredArgsConstructor
public class AiRelayRecommendFrontServiceImpl implements AiRelayRecommendFrontService {

    private static final int STATUS_ACTIVE = 1;

    private final AiRelayProviderRecommendMapper recommendMapper;
    private final AiRelayProviderMapper providerMapper;
    private final AiRelayProviderRechargeMapper rechargeMapper;

    @Override
    public PageResult<AiRelayRecommendFrontVO> pageRecommends(AiRelayRecommendFrontPageQuery query) {
        AiRelayRecommendFrontPageQuery safe = query == null ? new AiRelayRecommendFrontPageQuery() : query;

        Page<AiRelayProviderRecommend> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayProviderRecommend> wrapper = new LambdaQueryWrapper<AiRelayProviderRecommend>()
                .eq(AiRelayProviderRecommend::getStatus, STATUS_ACTIVE)
                .eq(safe.getProviderId() != null, AiRelayProviderRecommend::getProviderId, safe.getProviderId())
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiRelayProviderRecommend::getRecommendReason, safe.getKeyword())
                        .or().like(AiRelayProviderRecommend::getReviewContent, safe.getKeyword())
                        .or().like(AiRelayProviderRecommend::getUseScenario, safe.getKeyword()));
        applySort(wrapper, safe.getSortBy());

        Page<AiRelayProviderRecommend> result = recommendMapper.selectPage(page, wrapper);
        List<AiRelayProviderRecommend> records = result.getRecords();

        if (records.isEmpty()) {
            return PageResult.of(Collections.emptyList(), result.getTotal(), result.getCurrent(), result.getSize());
        }

        Map<Long, AiRelayProvider> providerMap = loadProviders(
                records.stream().map(AiRelayProviderRecommend::getProviderId).toList()
        );
        Map<Long, RechargeStat> statMap = loadRechargeStats(providerMap.keySet());

        List<AiRelayRecommendFrontVO> rows = records.stream()
                .map(r -> toVO(r, providerMap.get(r.getProviderId()), statMap.get(r.getProviderId())))
                .filter(java.util.Objects::nonNull)
                .toList();

        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayRecommendFrontVO getRecommend(Long id) {
        if (id == null) return null;
        AiRelayProviderRecommend entity = recommendMapper.selectById(id);
        if (entity == null || entity.getStatus() == null || entity.getStatus() != STATUS_ACTIVE) {
            return null;
        }
        AiRelayProvider provider = providerMapper.selectById(entity.getProviderId());
        if (provider == null || provider.getStatus() == null || provider.getStatus() != STATUS_ACTIVE) {
            return null;
        }
        RechargeStat stat = loadRechargeStats(List.of(entity.getProviderId())).get(entity.getProviderId());
        return toVO(entity, provider, stat);
    }

    @Override
    public AiRelayRecommendFrontVO getByProvider(Long providerId) {
        if (providerId == null) return null;
        AiRelayProviderRecommend entity = recommendMapper.selectOne(
                new LambdaQueryWrapper<AiRelayProviderRecommend>()
                        .eq(AiRelayProviderRecommend::getProviderId, providerId)
                        .eq(AiRelayProviderRecommend::getStatus, STATUS_ACTIVE)
                        .last("LIMIT 1")
        );
        if (entity == null) return null;
        return getRecommend(entity.getId());
    }

    // ============ 内部工具 ============

    private void applySort(LambdaQueryWrapper<AiRelayProviderRecommend> wrapper, String sortBy) {
        String key = sortBy == null ? "score" : sortBy.trim().toLowerCase();
        switch (key) {
            case "time" -> wrapper.orderByDesc(AiRelayProviderRecommend::getRecommendTime);
            case "sort" -> wrapper
                    .orderByAsc(AiRelayProviderRecommend::getSortOrder)
                    .orderByDesc(AiRelayProviderRecommend::getRecommendTime);
            default -> wrapper
                    .orderByDesc(AiRelayProviderRecommend::getReviewScore)
                    .orderByAsc(AiRelayProviderRecommend::getSortOrder)
                    .orderByDesc(AiRelayProviderRecommend::getRecommendTime);
        }
    }

    private Map<Long, AiRelayProvider> loadProviders(List<Long> providerIds) {
        List<Long> ids = providerIds.stream().filter(java.util.Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return providerMapper.selectBatchIds(ids).stream()
                .filter(p -> p.getStatus() != null && p.getStatus() == STATUS_ACTIVE)
                .collect(Collectors.toMap(AiRelayProvider::getId, java.util.function.Function.identity(), (a, b) -> a));
    }

    private Map<Long, RechargeStat> loadRechargeStats(Collection<Long> providerIds) {
        if (providerIds == null || providerIds.isEmpty()) return Map.of();
        List<AiRelayProviderRecharge> all = rechargeMapper.selectList(
                new LambdaQueryWrapper<AiRelayProviderRecharge>()
                        .in(AiRelayProviderRecharge::getProviderId, providerIds)
                        .eq(AiRelayProviderRecharge::getStatus, STATUS_ACTIVE)
        );
        Map<Long, RechargeStat> map = new HashMap<>();
        for (AiRelayProviderRecharge r : all) {
            RechargeStat stat = map.computeIfAbsent(r.getProviderId(), pid -> new RechargeStat());
            stat.count++;
            BigDecimal cny = r.getCnyAmount() != null ? r.getCnyAmount() : BigDecimal.ZERO;
            stat.totalCny = stat.totalCny.add(cny);
            if (r.getRechargeTime() != null
                    && (stat.lastRechargeTime == null || r.getRechargeTime().isAfter(stat.lastRechargeTime))) {
                stat.lastRechargeTime = r.getRechargeTime();
            }
        }
        return map;
    }

    private AiRelayRecommendFrontVO toVO(AiRelayProviderRecommend entity,
                                         AiRelayProvider provider,
                                         RechargeStat stat) {
        if (provider == null) return null;
        AiRelayRecommendFrontVO vo = new AiRelayRecommendFrontVO();
        vo.setId(entity.getId());
        vo.setProviderId(entity.getProviderId());
        vo.setProviderName(provider.getName());
        vo.setProviderLogoText(buildLogoText(provider.getName()));
        vo.setProviderLogoUrl(null);
        vo.setWebsiteUrl(provider.getWebsiteUrl());
        vo.setProviderDescription(provider.getDescription());
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
        if (stat != null) {
            vo.setRechargeCount(stat.count);
            vo.setTotalCnyAmount(stat.totalCny);
            vo.setLastRechargeTime(stat.lastRechargeTime);
        } else {
            vo.setRechargeCount(0);
            vo.setTotalCnyAmount(BigDecimal.ZERO);
        }
        return vo;
    }

    private String buildLogoText(String name) {
        if (!StringUtils.hasText(name)) return "AI";
        String trimmed = name.trim();
        return trimmed.length() <= 2 ? trimmed.toUpperCase() : trimmed.substring(0, 2).toUpperCase();
    }

    private static final class RechargeStat {
        int count;
        BigDecimal totalCny = BigDecimal.ZERO;
        LocalDateTime lastRechargeTime;
    }
}
