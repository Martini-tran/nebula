package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.AiRelayProviderRechargePageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderRechargeRequest;
import com.nebula.blog.entity.AiRelayPaymentMethod;
import com.nebula.blog.entity.AiRelayProvider;
import com.nebula.blog.entity.AiRelayProviderPackage;
import com.nebula.blog.entity.AiRelayProviderRecharge;
import com.nebula.blog.mapper.AiRelayPaymentMethodMapper;
import com.nebula.blog.mapper.AiRelayProviderMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageMapper;
import com.nebula.blog.mapper.AiRelayProviderRechargeMapper;
import com.nebula.blog.service.AiRelayProviderRechargeAdminService;
import com.nebula.blog.vo.admin.AiRelayProviderRechargeAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderRechargeStatsVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * AI中转服务商充值记录管理服务实现
 */
@Service
@RequiredArgsConstructor
public class AiRelayProviderRechargeAdminServiceImpl implements AiRelayProviderRechargeAdminService {

    private final AiRelayProviderRechargeMapper rechargeMapper;
    private final AiRelayProviderMapper providerMapper;
    private final AiRelayProviderPackageMapper providerPackageMapper;
    private final AiRelayPaymentMethodMapper paymentMethodMapper;

    @Override
    public PageResult<AiRelayProviderRechargeAdminVO> page(AiRelayProviderRechargePageQuery query) {
        AiRelayProviderRechargePageQuery safe = query == null ? new AiRelayProviderRechargePageQuery() : query;
        Page<AiRelayProviderRecharge> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayProviderRecharge> wrapper = new LambdaQueryWrapper<AiRelayProviderRecharge>()
                .eq(safe.getProviderId() != null, AiRelayProviderRecharge::getProviderId, safe.getProviderId())
                .eq(safe.getPackageId() != null, AiRelayProviderRecharge::getPackageId, safe.getPackageId())
                .eq(safe.getStatus() != null, AiRelayProviderRecharge::getStatus, safe.getStatus())
                .ge(safe.getStartTime() != null, AiRelayProviderRecharge::getRechargeTime, safe.getStartTime())
                .le(safe.getEndTime() != null, AiRelayProviderRecharge::getRechargeTime, safe.getEndTime())
                .orderByDesc(AiRelayProviderRecharge::getRechargeTime);
        Page<AiRelayProviderRecharge> result = rechargeMapper.selectPage(page, wrapper);

        List<AiRelayProviderRecharge> records = result.getRecords();
        Map<Long, String> providerNameMap = loadProviderNames(records.stream()
                .map(AiRelayProviderRecharge::getProviderId).distinct().toList());
        Map<Long, String> packageNameMap = loadPackageNames(records.stream()
                .map(AiRelayProviderRecharge::getPackageId).distinct().toList());
        Map<Long, String> methodNameMap = loadPaymentMethodNames(records.stream()
                .map(AiRelayProviderRecharge::getPaymentMethodId).distinct().toList());

        List<AiRelayProviderRechargeAdminVO> rows = records.stream()
                .map(e -> toVO(e, providerNameMap, packageNameMap, methodNameMap))
                .toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayProviderRechargeAdminVO detail(Long id) {
        AiRelayProviderRecharge entity = requireRecharge(id);
        Map<Long, String> providerNameMap = loadProviderNames(List.of(entity.getProviderId()));
        Map<Long, String> packageNameMap = loadPackageNames(entity.getPackageId() != null
                ? List.of(entity.getPackageId()) : Collections.emptyList());
        Map<Long, String> methodNameMap = loadPaymentMethodNames(entity.getPaymentMethodId() != null
                ? List.of(entity.getPaymentMethodId()) : Collections.emptyList());
        return toVO(entity, providerNameMap, packageNameMap, methodNameMap);
    }

    @Override
    public Long create(AiRelayProviderRechargeRequest req) {
        validateRequest(req);
        requireProvider(req.getProviderId());
        if (req.getPackageId() != null) {
            requirePackage(req.getPackageId(), req.getProviderId());
        }

        AiRelayProviderRecharge entity = new AiRelayProviderRecharge();
        applyRequest(entity, req);
        if (entity.getStatus() == null) entity.setStatus(1);
        if (entity.getCnyAmount() == null) entity.setCnyAmount(computeCnyAmount(entity));
        rechargeMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, AiRelayProviderRechargeRequest req) {
        validateRequest(req);
        AiRelayProviderRecharge existing = requireRecharge(id);
        if (req.getProviderId() != null && !req.getProviderId().equals(existing.getProviderId())) {
            requireProvider(req.getProviderId());
        }
        if (req.getPackageId() != null) {
            requirePackage(req.getPackageId(),
                    req.getProviderId() != null ? req.getProviderId() : existing.getProviderId());
        }
        applyRequest(existing, req);
        if (existing.getCnyAmount() == null) existing.setCnyAmount(computeCnyAmount(existing));
        rechargeMapper.updateById(existing);
    }

    @Override
    public void delete(Long id) {
        requireRecharge(id);
        rechargeMapper.deleteById(id);
    }

    @Override
    public List<AiRelayProviderRechargeStatsVO> statsByProvider(Long providerId) {
        LambdaQueryWrapper<AiRelayProviderRecharge> wrapper = new LambdaQueryWrapper<AiRelayProviderRecharge>()
                .eq(providerId != null, AiRelayProviderRecharge::getProviderId, providerId)
                .eq(AiRelayProviderRecharge::getStatus, 1);
        List<AiRelayProviderRecharge> all = rechargeMapper.selectList(wrapper);
        if (all.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, AiRelayProviderRechargeStatsVO> bucket = new LinkedHashMap<>();
        for (AiRelayProviderRecharge r : all) {
            AiRelayProviderRechargeStatsVO stat = bucket.computeIfAbsent(r.getProviderId(), pid -> {
                AiRelayProviderRechargeStatsVO s = new AiRelayProviderRechargeStatsVO();
                s.setProviderId(pid);
                s.setRechargeCount(0);
                s.setTotalCnyAmount(BigDecimal.ZERO);
                return s;
            });
            stat.setRechargeCount(stat.getRechargeCount() + 1);
            BigDecimal cny = r.getCnyAmount() != null ? r.getCnyAmount() : BigDecimal.ZERO;
            stat.setTotalCnyAmount(stat.getTotalCnyAmount().add(cny));
            if (r.getRechargeTime() != null
                    && (stat.getLastRechargeTime() == null || r.getRechargeTime().isAfter(stat.getLastRechargeTime()))) {
                stat.setLastRechargeTime(r.getRechargeTime());
            }
        }
        Map<Long, String> nameMap = loadProviderNames(new ArrayList<>(bucket.keySet()));
        bucket.values().forEach(s -> s.setProviderName(nameMap.get(s.getProviderId())));
        return new ArrayList<>(bucket.values());
    }

    // ============ 内部工具 ============

    private void validateRequest(AiRelayProviderRechargeRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (req.getProviderId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商ID不能为空");
        }
        if (req.getAmount() == null || req.getAmount().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "充值金额必须 >= 0");
        }
        if (req.getRechargeTime() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "充值时间不能为空");
        }
    }

    private AiRelayProviderRecharge requireRecharge(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "充值记录ID不能为空");
        }
        AiRelayProviderRecharge entity = rechargeMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "充值记录不存在");
        }
        return entity;
    }

    private void requireProvider(Long providerId) {
        AiRelayProvider provider = providerMapper.selectById(providerId);
        if (provider == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "服务商不存在");
        }
    }

    private void requirePackage(Long packageId, Long providerId) {
        AiRelayProviderPackage pkg = providerPackageMapper.selectById(packageId);
        if (pkg == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "套餐不存在");
        }
        if (providerId != null && !providerId.equals(pkg.getProviderId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐与服务商不匹配");
        }
    }

    private void applyRequest(AiRelayProviderRecharge entity, AiRelayProviderRechargeRequest req) {
        entity.setProviderId(req.getProviderId());
        entity.setPackageId(req.getPackageId());
        entity.setAmount(req.getAmount());
        entity.setCurrency(req.getCurrency() != null ? req.getCurrency() : "CNY");
        entity.setExchangeRate(req.getExchangeRate());
        entity.setCnyAmount(req.getCnyAmount());
        entity.setPaymentMethodId(req.getPaymentMethodId());
        entity.setRechargeTime(req.getRechargeTime());
        entity.setOrderNo(req.getOrderNo());
        entity.setVoucherFileId(req.getVoucherFileId());
        entity.setRemark(req.getRemark());
        if (req.getStatus() != null) entity.setStatus(req.getStatus());
    }

    private BigDecimal computeCnyAmount(AiRelayProviderRecharge entity) {
        if (entity.getAmount() == null) return BigDecimal.ZERO;
        if ("CNY".equalsIgnoreCase(entity.getCurrency()) || entity.getCurrency() == null) {
            return entity.getAmount();
        }
        if (entity.getExchangeRate() != null) {
            return entity.getAmount().multiply(entity.getExchangeRate()).setScale(2, java.math.RoundingMode.HALF_UP);
        }
        return null;
    }

    private Map<Long, String> loadProviderNames(List<Long> providerIds) {
        List<Long> ids = providerIds.stream().filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        return providerMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AiRelayProvider::getId, AiRelayProvider::getName, (a, b) -> a));
    }

    private Map<Long, String> loadPackageNames(List<Long> packageIds) {
        List<Long> ids = packageIds.stream().filter(java.util.Objects::nonNull).toList();
        if (ids.isEmpty()) return Map.of();
        return providerPackageMapper.selectBatchIds(ids).stream()
                .collect(Collectors.toMap(AiRelayProviderPackage::getId, AiRelayProviderPackage::getName, (a, b) -> a));
    }

    private Map<Long, String> loadPaymentMethodNames(List<Long> ids) {
        List<Long> validIds = ids.stream().filter(java.util.Objects::nonNull).toList();
        if (validIds.isEmpty()) return Map.of();
        return paymentMethodMapper.selectBatchIds(validIds).stream()
                .collect(Collectors.toMap(AiRelayPaymentMethod::getId, AiRelayPaymentMethod::getName, (a, b) -> a));
    }

    private AiRelayProviderRechargeAdminVO toVO(AiRelayProviderRecharge entity,
                                                Map<Long, String> providerNameMap,
                                                Map<Long, String> packageNameMap,
                                                Map<Long, String> methodNameMap) {
        AiRelayProviderRechargeAdminVO vo = new AiRelayProviderRechargeAdminVO();
        vo.setId(entity.getId());
        vo.setProviderId(entity.getProviderId());
        vo.setProviderName(providerNameMap.get(entity.getProviderId()));
        vo.setPackageId(entity.getPackageId());
        vo.setPackageName(entity.getPackageId() != null ? packageNameMap.get(entity.getPackageId()) : null);
        vo.setAmount(entity.getAmount());
        vo.setCurrency(entity.getCurrency());
        vo.setExchangeRate(entity.getExchangeRate());
        vo.setCnyAmount(entity.getCnyAmount());
        vo.setPaymentMethodId(entity.getPaymentMethodId());
        vo.setPaymentMethodName(entity.getPaymentMethodId() != null ? methodNameMap.get(entity.getPaymentMethodId()) : null);
        vo.setRechargeTime(entity.getRechargeTime());
        vo.setOrderNo(entity.getOrderNo());
        vo.setVoucherFileId(entity.getVoucherFileId());
        vo.setRemark(entity.getRemark());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
