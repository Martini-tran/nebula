package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nebula.blog.dto.front.AiRelayCompareFrontPageQuery;
import com.nebula.blog.entity.AiRelayModel;
import com.nebula.blog.entity.AiRelayPackageModel;
import com.nebula.blog.entity.AiRelayPackageType;
import com.nebula.blog.entity.AiRelayProvider;
import com.nebula.blog.entity.AiRelayProviderPackage;
import com.nebula.blog.entity.AiRelayProviderPackageLimit;
import com.nebula.blog.mapper.AiRelayModelMapper;
import com.nebula.blog.mapper.AiRelayPackageModelMapper;
import com.nebula.blog.mapper.AiRelayPackageTypeMapper;
import com.nebula.blog.mapper.AiRelayProviderMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageLimitMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageMapper;
import com.nebula.blog.service.AiRelayCompareFrontService;
import com.nebula.blog.vo.front.AiRelayCompareRowFrontVO;
import com.nebula.common.core.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 中转比价（前台）服务实现
 *
 * <p>以 ai_relay_provider_package_limit 为主表，每行带上所属 package + provider，以及（可选）
 * 选定模型在该套餐下的输入 / 输出单价（含倍率乘后）。</p>
 *
 * <p>因为限额表和模型表的连接关系比较散（每个 package 可能配多个 limit、多个 model），
 * 这里采用"内存装配 + 二次过滤 + 排序 + 手动分页"的策略；运营数据量级有限，可读性优先。</p>
 */
@Service
@RequiredArgsConstructor
public class AiRelayCompareFrontServiceImpl implements AiRelayCompareFrontService {

    private static final int STATUS_ACTIVE = 1;
    private static final BigDecimal MILLION = BigDecimal.ONE;

    private final AiRelayProviderPackageLimitMapper limitMapper;
    private final AiRelayProviderPackageMapper packageMapper;
    private final AiRelayProviderMapper providerMapper;
    private final AiRelayPackageTypeMapper packageTypeMapper;
    private final AiRelayPackageModelMapper packageModelMapper;
    private final AiRelayModelMapper modelMapper;

    @Override
    public PageResult<AiRelayCompareRowFrontVO> pageCompare(AiRelayCompareFrontPageQuery query) {
        AiRelayCompareFrontPageQuery safe = query == null ? new AiRelayCompareFrontPageQuery() : query;
        int pageNum = safe.safePageNum();
        int pageSize = safe.safePageSize();

        // 1) 先按筛选条件取一批候选 packages（status=active，可加 providerId / 类型 / 关键词）
        LambdaQueryWrapper<AiRelayProviderPackage> pkgWrapper = new LambdaQueryWrapper<AiRelayProviderPackage>()
                .eq(AiRelayProviderPackage::getStatus, STATUS_ACTIVE);
        List<Long> filterProviderIds = safe.getProviderIds();
        if (filterProviderIds != null && !filterProviderIds.isEmpty()) {
            pkgWrapper.in(AiRelayProviderPackage::getProviderId, filterProviderIds);
        } else if (safe.getProviderId() != null) {
            pkgWrapper.eq(AiRelayProviderPackage::getProviderId, safe.getProviderId());
        }
        if (StringUtils.hasText(safe.getKeyword())) {
            pkgWrapper.like(AiRelayProviderPackage::getName, safe.getKeyword());
        }
        List<AiRelayProviderPackage> packages = packageMapper.selectList(pkgWrapper);
        if (packages.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 套餐类型过滤（按 code 去掉非匹配的 packages）
        Map<Long, AiRelayPackageType> typeMap = loadPackageTypes(packages);
        if (StringUtils.hasText(safe.getPackageTypeCode())) {
            String wanted = safe.getPackageTypeCode().trim().toLowerCase();
            packages = packages.stream()
                    .filter(p -> {
                        AiRelayPackageType t = typeMap.get(p.getPackageTypeId());
                        return t != null && wanted.equalsIgnoreCase(t.getCode());
                    })
                    .toList();
            if (packages.isEmpty()) {
                return PageResult.empty(pageNum, pageSize);
            }
        }

        // 2) 选定模型时，仅保留绑定了该模型且 active 的 packages，并准备 packageId -> packageModel 映射
        Map<Long, AiRelayPackageModel> packageModelByPkg = Collections.emptyMap();
        AiRelayModel selectedModel = null;
        if (safe.getModelId() != null) {
            selectedModel = modelMapper.selectById(safe.getModelId());
            if (selectedModel == null || !Integer.valueOf(STATUS_ACTIVE).equals(selectedModel.getStatus())) {
                return PageResult.empty(pageNum, pageSize);
            }
            List<Long> pkgIds = packages.stream().map(AiRelayProviderPackage::getId).toList();
            List<AiRelayPackageModel> packageModels = packageModelMapper.selectList(
                    new LambdaQueryWrapper<AiRelayPackageModel>()
                            .in(AiRelayPackageModel::getPackageId, pkgIds)
                            .eq(AiRelayPackageModel::getModelId, safe.getModelId())
                            .eq(AiRelayPackageModel::getStatus, STATUS_ACTIVE));
            packageModelByPkg = packageModels.stream()
                    .collect(Collectors.toMap(AiRelayPackageModel::getPackageId, Function.identity(), (a, b) -> a));
            // 仅保留绑定了该模型的 package
            final Map<Long, AiRelayPackageModel> finalMap = packageModelByPkg;
            packages = packages.stream().filter(p -> finalMap.containsKey(p.getId())).toList();
            if (packages.isEmpty()) {
                return PageResult.empty(pageNum, pageSize);
            }
        }

        // 3) 取候选 packages 的全部 active limit
        List<Long> finalPackageIds = packages.stream().map(AiRelayProviderPackage::getId).toList();
        LambdaQueryWrapper<AiRelayProviderPackageLimit> limitWrapper = new LambdaQueryWrapper<AiRelayProviderPackageLimit>()
                .in(AiRelayProviderPackageLimit::getPackageId, finalPackageIds)
                .eq(AiRelayProviderPackageLimit::getStatus, STATUS_ACTIVE);
        if (safe.getLimitType() != null) {
            limitWrapper.eq(AiRelayProviderPackageLimit::getLimitType, safe.getLimitType());
        }
        List<AiRelayProviderPackageLimit> limits = limitMapper.selectList(limitWrapper);
        if (limits.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 4) 关键词二次匹配 provider name（套餐名已在 SQL 处过滤；这里允许命中提供商名）
        Map<Long, AiRelayProviderPackage> packageMap = packages.stream()
                .collect(Collectors.toMap(AiRelayProviderPackage::getId, Function.identity()));
        List<Long> providerIds = packages.stream()
                .map(AiRelayProviderPackage::getProviderId).filter(Objects::nonNull).distinct().toList();
        Map<Long, AiRelayProvider> providerMap = providerIds.isEmpty()
                ? Collections.emptyMap()
                : providerMapper.selectBatchIds(providerIds).stream()
                .filter(p -> Integer.valueOf(STATUS_ACTIVE).equals(p.getStatus()))
                .collect(Collectors.toMap(AiRelayProvider::getId, Function.identity()));

        // provider 必须 active：过滤 limit
        limits = limits.stream()
                .filter(l -> {
                    AiRelayProviderPackage p = packageMap.get(l.getPackageId());
                    return p != null && providerMap.containsKey(p.getProviderId());
                })
                .toList();

        if (StringUtils.hasText(safe.getKeyword())) {
            String kw = safe.getKeyword().trim().toLowerCase();
            limits = limits.stream()
                    .filter(l -> {
                        AiRelayProviderPackage p = packageMap.get(l.getPackageId());
                        if (p == null) return false;
                        AiRelayProvider provider = providerMap.get(p.getProviderId());
                        boolean hitPkg = p.getName() != null && p.getName().toLowerCase().contains(kw);
                        boolean hitProvider = provider != null && provider.getName() != null
                                && provider.getName().toLowerCase().contains(kw);
                        return hitPkg || hitProvider;
                    })
                    .toList();
        }

        if (limits.isEmpty()) {
            return PageResult.empty(pageNum, pageSize);
        }

        // 5) 装配 VO
        AiRelayModel selected = selectedModel;
        Map<Long, AiRelayPackageModel> pmMap = packageModelByPkg;
        List<AiRelayCompareRowFrontVO> rows = new ArrayList<>(limits.size());
        for (AiRelayProviderPackageLimit limit : limits) {
            AiRelayProviderPackage pkg = packageMap.get(limit.getPackageId());
            if (pkg == null) continue;
            AiRelayProvider provider = providerMap.get(pkg.getProviderId());
            if (provider == null) continue;
            AiRelayPackageType type = typeMap.get(pkg.getPackageTypeId());
            AiRelayPackageModel pm = pmMap.get(pkg.getId());
            rows.add(toRow(limit, pkg, provider, type, pm, selected));
        }

        // 6) 排序
        rows.sort(buildComparator(safe.getSortBy()));

        // 7) 手动分页
        long total = rows.size();
        int from = Math.min((pageNum - 1) * pageSize, rows.size());
        int to = Math.min(from + pageSize, rows.size());
        List<AiRelayCompareRowFrontVO> sliced = rows.subList(from, to);
        return PageResult.of(new ArrayList<>(sliced), total, pageNum, pageSize);
    }

    // ============ helper ============

    private Map<Long, AiRelayPackageType> loadPackageTypes(List<AiRelayProviderPackage> packages) {
        List<Long> typeIds = packages.stream()
                .map(AiRelayProviderPackage::getPackageTypeId).filter(Objects::nonNull).distinct().toList();
        if (typeIds.isEmpty()) return Collections.emptyMap();
        return packageTypeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(AiRelayPackageType::getId, Function.identity()));
    }

    private AiRelayCompareRowFrontVO toRow(AiRelayProviderPackageLimit limit,
                                           AiRelayProviderPackage pkg,
                                           AiRelayProvider provider,
                                           AiRelayPackageType type,
                                           AiRelayPackageModel pm,
                                           AiRelayModel model) {
        AiRelayCompareRowFrontVO vo = new AiRelayCompareRowFrontVO();
        vo.setLimitId(limit.getId());
        vo.setLimitType(limit.getLimitType());
        vo.setLimitTypeText(limitTypeText(limit.getLimitType()));
        vo.setQuotaAmount(limit.getQuotaAmount());
        vo.setQuotaUnit(limit.getQuotaUnit());
        vo.setResetCycle(limit.getResetCycle());
        vo.setResetCycleText(resetCycleText(limit.getResetCycle()));
        vo.setOverLimitStrategy(limit.getOverLimitStrategy());
        vo.setOverLimitStrategyText(overLimitStrategyText(limit.getOverLimitStrategy()));
        vo.setLimitDescription(limit.getDescription());

        vo.setPackageId(pkg.getId());
        vo.setPackageName(pkg.getName());
        vo.setPackagePrice(pkg.getPrice());
        vo.setPackageOriginalPrice(pkg.getOriginalPrice());
        vo.setPackageCurrency(pkg.getCurrency());
        vo.setPackageDescription(pkg.getDescription());
        vo.setPackageRecommended(pkg.getIsRecommended());
        vo.setPackageRecommendScore(pkg.getRecommendScore());
        if (type != null) {
            vo.setPackageTypeCode(type.getCode());
            vo.setPackageTypeName(type.getName());
        }

        vo.setProviderId(provider.getId());
        vo.setProviderName(provider.getName());
        vo.setProviderLogoText(buildLogoText(provider.getName()));
        vo.setProviderWebsiteUrl(provider.getWebsiteUrl());
        vo.setProviderRecommendScore(provider.getRecommendScore());

        if (pm != null && model != null) {
            vo.setModelId(model.getId());
            vo.setModelCode(model.getCode());
            vo.setModelName(model.getName());
            vo.setModelVendor(model.getModelVendor());
            vo.setProviderModelCode(pm.getProviderModelCode());
            vo.setConsumeMultiplier(pm.getConsumeMultiplier());
            vo.setMaxContextTokens(pm.getMaxContextTokens());
            vo.setInputPricePerMillionTokens(pm.getInputPricePerMillionTokens());
            vo.setOutputPricePerMillionTokens(pm.getOutputPricePerMillionTokens());
            vo.setEffectiveInputPricePerMillionTokens(effective(pm.getInputPricePerMillionTokens(), pm.getConsumeMultiplier()));
            vo.setEffectiveOutputPricePerMillionTokens(effective(pm.getOutputPricePerMillionTokens(), pm.getConsumeMultiplier()));
        }
        return vo;
    }

    private BigDecimal effective(BigDecimal price, BigDecimal multiplier) {
        if (price == null) return null;
        BigDecimal mult = multiplier == null ? MILLION : multiplier;
        return price.multiply(mult).setScale(4, RoundingMode.HALF_UP);
    }

    private String buildLogoText(String name) {
        if (!StringUtils.hasText(name)) return "AI";
        String trimmed = name.trim();
        return trimmed.length() <= 2 ? trimmed : trimmed.substring(0, 2);
    }

    private String limitTypeText(Integer type) {
        if (type == null) return null;
        return switch (type) {
            case 1 -> "总额度";
            case 2 -> "每日额度";
            case 3 -> "每周额度";
            case 4 -> "每月额度";
            case 5 -> "单次额度";
            default -> "限额";
        };
    }

    private String resetCycleText(Integer cycle) {
        if (cycle == null) return null;
        return switch (cycle) {
            case 0 -> "不重置";
            case 1 -> "每日重置";
            case 2 -> "每周重置";
            case 3 -> "每月重置";
            case 4 -> "套餐周期重置";
            default -> null;
        };
    }

    private String overLimitStrategyText(Integer s) {
        if (s == null) return null;
        return switch (s) {
            case 1 -> "禁止使用";
            case 2 -> "按量计费";
            case 3 -> "限速";
            default -> null;
        };
    }

    private Comparator<AiRelayCompareRowFrontVO> buildComparator(String sortBy) {
        Comparator<AiRelayCompareRowFrontVO> cmp;
        if ("input_price".equalsIgnoreCase(sortBy)) {
            cmp = Comparator.comparing(AiRelayCompareRowFrontVO::getEffectiveInputPricePerMillionTokens,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("output_price".equalsIgnoreCase(sortBy)) {
            cmp = Comparator.comparing(AiRelayCompareRowFrontVO::getEffectiveOutputPricePerMillionTokens,
                    Comparator.nullsLast(Comparator.naturalOrder()));
        } else if ("quota".equalsIgnoreCase(sortBy)) {
            cmp = Comparator.comparing(AiRelayCompareRowFrontVO::getQuotaAmount,
                    Comparator.nullsLast(Comparator.reverseOrder()));
        } else {
            // recommend：provider 推荐分 desc, package 推荐分 desc, package 名 asc
            cmp = Comparator.comparing(AiRelayCompareRowFrontVO::getProviderRecommendScore,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(AiRelayCompareRowFrontVO::getPackageRecommendScore,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(AiRelayCompareRowFrontVO::getPackageName,
                            Comparator.nullsLast(Comparator.naturalOrder()));
        }
        // tie-breaker：limit id 升序，保证稳定输出
        return cmp.thenComparing(AiRelayCompareRowFrontVO::getLimitId,
                Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
