package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.AiRelayProviderAdvantageRequest;
import com.nebula.blog.dto.admin.AiRelayProviderCreateRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderPaymentMethodBindRequest;
import com.nebula.blog.dto.admin.AiRelayProviderUpdateRequest;
import com.nebula.blog.entity.AiRelayPaymentMethod;
import com.nebula.blog.entity.AiRelayProvider;
import com.nebula.blog.entity.AiRelayProviderAdvantage;
import com.nebula.blog.entity.AiRelayProviderPackage;
import com.nebula.blog.entity.AiRelayProviderPaymentMethod;
import com.nebula.blog.mapper.AiRelayPaymentMethodMapper;
import com.nebula.blog.mapper.AiRelayProviderAdvantageMapper;
import com.nebula.blog.mapper.AiRelayProviderMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageMapper;
import com.nebula.blog.mapper.AiRelayProviderPaymentMethodMapper;
import com.nebula.blog.service.AiRelayProviderAdminService;
import com.nebula.blog.vo.admin.AiRelayProviderAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderAdvantageAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPaymentMethodAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI中转服务商管理服务实现（管理员端）
 */
@Service
@RequiredArgsConstructor
public class AiRelayProviderAdminServiceImpl implements AiRelayProviderAdminService {

    private final AiRelayProviderMapper providerMapper;
    private final AiRelayProviderAdvantageMapper advantageMapper;
    private final AiRelayProviderPaymentMethodMapper providerPaymentMethodMapper;
    private final AiRelayPaymentMethodMapper paymentMethodMapper;
    private final AiRelayProviderPackageMapper providerPackageMapper;

    @Override
    public PageResult<AiRelayProviderAdminVO> page(AiRelayProviderPageQuery query) {
        AiRelayProviderPageQuery safe = query == null ? new AiRelayProviderPageQuery() : query;
        Page<AiRelayProvider> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayProvider> wrapper = new LambdaQueryWrapper<AiRelayProvider>()
                .like(StringUtils.hasText(safe.getKeyword()), AiRelayProvider::getName, safe.getKeyword())
                .eq(safe.getStatus() != null, AiRelayProvider::getStatus, safe.getStatus())
                .orderByDesc(AiRelayProvider::getRecommendScore)
                .orderByAsc(AiRelayProvider::getSortOrder)
                .orderByDesc(AiRelayProvider::getCreateTime);
        Page<AiRelayProvider> result = providerMapper.selectPage(page, wrapper);
        List<AiRelayProviderAdminVO> rows = result.getRecords().stream().map(this::toVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayProviderAdminVO detail(Long id) {
        return toVO(requireProvider(id));
    }

    @Override
    public Long create(AiRelayProviderCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商名称不能为空");
        }
        checkNameUnique(req.getName(), null);

        AiRelayProvider entity = new AiRelayProvider();
        entity.setName(req.getName());
        entity.setWebsiteUrl(req.getWebsiteUrl());
        entity.setLogoFileId(req.getLogoFileId());
        entity.setDescription(req.getDescription());
        entity.setRecommendScore(req.getRecommendScore() != null ? req.getRecommendScore() : BigDecimal.ZERO);
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        providerMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, AiRelayProviderUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        AiRelayProvider existing = requireProvider(id);

        if (StringUtils.hasText(req.getName()) && !req.getName().equals(existing.getName())) {
            checkNameUnique(req.getName(), id);
            existing.setName(req.getName());
        }
        if (req.getWebsiteUrl() != null) existing.setWebsiteUrl(req.getWebsiteUrl());
        if (req.getLogoFileId() != null) existing.setLogoFileId(req.getLogoFileId());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getRecommendScore() != null) existing.setRecommendScore(req.getRecommendScore());
        if (req.getSortOrder() != null) existing.setSortOrder(req.getSortOrder());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        providerMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireProvider(id);
        long packageCount = providerPackageMapper.selectCount(
                new LambdaQueryWrapper<AiRelayProviderPackage>().eq(AiRelayProviderPackage::getProviderId, id)
        );
        if (packageCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "存在该服务商的套餐，请先删除套餐");
        }
        advantageMapper.delete(
                new LambdaQueryWrapper<AiRelayProviderAdvantage>().eq(AiRelayProviderAdvantage::getProviderId, id)
        );
        providerPaymentMethodMapper.delete(
                new LambdaQueryWrapper<AiRelayProviderPaymentMethod>().eq(AiRelayProviderPaymentMethod::getProviderId, id)
        );
        providerMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        AiRelayProvider existing = requireProvider(id);
        existing.setStatus(status);
        providerMapper.updateById(existing);
    }

    // ============ 服务商优势 ============

    @Override
    public List<AiRelayProviderAdvantageAdminVO> listAdvantages(Long providerId) {
        requireProvider(providerId);
        return advantageMapper.selectList(
                        new LambdaQueryWrapper<AiRelayProviderAdvantage>()
                                .eq(AiRelayProviderAdvantage::getProviderId, providerId)
                                .orderByAsc(AiRelayProviderAdvantage::getSortOrder)
                                .orderByDesc(AiRelayProviderAdvantage::getCreateTime)
                ).stream()
                .map(this::toAdvantageVO)
                .toList();
    }

    @Override
    public Long createAdvantage(Long providerId, AiRelayProviderAdvantageRequest req) {
        requireProvider(providerId);
        validateAdvantageRequest(req);
        AiRelayProviderAdvantage entity = new AiRelayProviderAdvantage();
        entity.setProviderId(providerId);
        applyAdvantageRequest(entity, req);
        if (entity.getAdvantageType() == null) entity.setAdvantageType(1);
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        if (entity.getStatus() == null) entity.setStatus(1);
        advantageMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateAdvantage(Long providerId, Long advantageId, AiRelayProviderAdvantageRequest req) {
        validateAdvantageRequest(req);
        AiRelayProviderAdvantage existing = requireAdvantage(providerId, advantageId);
        applyAdvantageRequest(existing, req);
        advantageMapper.updateById(existing);
    }

    @Override
    public void deleteAdvantage(Long providerId, Long advantageId) {
        requireAdvantage(providerId, advantageId);
        advantageMapper.deleteById(advantageId);
    }

    // ============ 服务商支付方式 ============

    @Override
    public List<AiRelayProviderPaymentMethodAdminVO> listPaymentMethods(Long providerId) {
        requireProvider(providerId);
        List<AiRelayProviderPaymentMethod> relations = providerPaymentMethodMapper.selectList(
                new LambdaQueryWrapper<AiRelayProviderPaymentMethod>()
                        .eq(AiRelayProviderPaymentMethod::getProviderId, providerId)
                        .orderByAsc(AiRelayProviderPaymentMethod::getSortOrder)
                        .orderByDesc(AiRelayProviderPaymentMethod::getCreateTime)
        );
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> methodIds = relations.stream()
                .map(AiRelayProviderPaymentMethod::getPaymentMethodId)
                .distinct()
                .toList();
        Map<Long, AiRelayPaymentMethod> methodMap = paymentMethodMapper.selectBatchIds(methodIds).stream()
                .collect(Collectors.toMap(AiRelayPaymentMethod::getId, Function.identity()));
        return relations.stream()
                .map(rel -> toProviderPaymentMethodVO(rel, methodMap.get(rel.getPaymentMethodId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindPaymentMethods(Long providerId, AiRelayProviderPaymentMethodBindRequest req) {
        requireProvider(providerId);
        if (req == null || req.getPaymentMethodIds() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "支付方式列表不能为空");
        }
        Set<Long> targetIds = new LinkedHashSet<>();
        for (Long pid : req.getPaymentMethodIds()) {
            if (pid != null && pid > 0) {
                targetIds.add(pid);
            }
        }
        if (!targetIds.isEmpty()) {
            long existsCount = paymentMethodMapper.selectCount(
                    new LambdaQueryWrapper<AiRelayPaymentMethod>().in(AiRelayPaymentMethod::getId, targetIds)
            );
            if (existsCount != targetIds.size()) {
                throw new BizException(HttpStatus.BAD_REQUEST, "存在不存在的支付方式");
            }
        }

        List<AiRelayProviderPaymentMethod> existingList = providerPaymentMethodMapper.selectList(
                new LambdaQueryWrapper<AiRelayProviderPaymentMethod>()
                        .eq(AiRelayProviderPaymentMethod::getProviderId, providerId)
        );
        Set<Long> existingIds = existingList.stream()
                .map(AiRelayProviderPaymentMethod::getPaymentMethodId)
                .collect(Collectors.toSet());

        Set<Long> toRemove = new HashSet<>(existingIds);
        toRemove.removeAll(targetIds);
        Set<Long> toAdd = new LinkedHashSet<>(targetIds);
        toAdd.removeAll(existingIds);

        if (!toRemove.isEmpty()) {
            providerPaymentMethodMapper.delete(
                    new LambdaQueryWrapper<AiRelayProviderPaymentMethod>()
                            .eq(AiRelayProviderPaymentMethod::getProviderId, providerId)
                            .in(AiRelayProviderPaymentMethod::getPaymentMethodId, toRemove)
            );
        }
        for (Long methodId : toAdd) {
            AiRelayProviderPaymentMethod relation = new AiRelayProviderPaymentMethod();
            relation.setProviderId(providerId);
            relation.setPaymentMethodId(methodId);
            relation.setSortOrder(0);
            relation.setStatus(1);
            providerPaymentMethodMapper.insert(relation);
        }
    }

    // ============ 内部工具 ============

    private AiRelayProvider requireProvider(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商ID不能为空");
        }
        AiRelayProvider entity = providerMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "服务商不存在");
        }
        return entity;
    }

    private void checkNameUnique(String name, Long excludeId) {
        LambdaQueryWrapper<AiRelayProvider> wrapper = new LambdaQueryWrapper<AiRelayProvider>()
                .eq(AiRelayProvider::getName, name);
        if (excludeId != null) {
            wrapper.ne(AiRelayProvider::getId, excludeId);
        }
        if (providerMapper.selectCount(wrapper) > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商名称已存在");
        }
    }

    private void validateAdvantageRequest(AiRelayProviderAdvantageRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (!StringUtils.hasText(req.getTitle())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "优势标题不能为空");
        }
    }

    private AiRelayProviderAdvantage requireAdvantage(Long providerId, Long advantageId) {
        if (advantageId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "优势ID不能为空");
        }
        requireProvider(providerId);
        AiRelayProviderAdvantage entity = advantageMapper.selectById(advantageId);
        if (entity == null || !providerId.equals(entity.getProviderId())) {
            throw new BizException(HttpStatus.NOT_FOUND, "优势不存在");
        }
        return entity;
    }

    private void applyAdvantageRequest(AiRelayProviderAdvantage entity, AiRelayProviderAdvantageRequest req) {
        entity.setTitle(req.getTitle());
        entity.setContent(req.getContent());
        if (req.getAdvantageType() != null) entity.setAdvantageType(req.getAdvantageType());
        entity.setIconFileId(req.getIconFileId());
        if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
        if (req.getStatus() != null) entity.setStatus(req.getStatus());
    }

    private AiRelayProviderAdminVO toVO(AiRelayProvider entity) {
        AiRelayProviderAdminVO vo = new AiRelayProviderAdminVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setWebsiteUrl(entity.getWebsiteUrl());
        vo.setLogoFileId(entity.getLogoFileId());
        vo.setDescription(entity.getDescription());
        vo.setRecommendScore(entity.getRecommendScore());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private AiRelayProviderAdvantageAdminVO toAdvantageVO(AiRelayProviderAdvantage entity) {
        AiRelayProviderAdvantageAdminVO vo = new AiRelayProviderAdvantageAdminVO();
        vo.setId(entity.getId());
        vo.setProviderId(entity.getProviderId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setAdvantageType(entity.getAdvantageType());
        vo.setIconFileId(entity.getIconFileId());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private AiRelayProviderPaymentMethodAdminVO toProviderPaymentMethodVO(AiRelayProviderPaymentMethod relation,
                                                                         AiRelayPaymentMethod method) {
        AiRelayProviderPaymentMethodAdminVO vo = new AiRelayProviderPaymentMethodAdminVO();
        vo.setId(relation.getId());
        vo.setProviderId(relation.getProviderId());
        vo.setPaymentMethodId(relation.getPaymentMethodId());
        vo.setRemark(relation.getRemark());
        vo.setSortOrder(relation.getSortOrder());
        vo.setStatus(relation.getStatus());
        vo.setCreateTime(relation.getCreateTime());
        if (method != null) {
            vo.setPaymentMethodCode(method.getCode());
            vo.setPaymentMethodName(method.getName());
        }
        return vo;
    }
}
