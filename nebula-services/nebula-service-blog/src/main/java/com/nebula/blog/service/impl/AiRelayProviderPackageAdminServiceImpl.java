package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.admin.AiRelayPackageModelRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackageCreateRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackageLimitRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPackagePageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderPackageUpdateRequest;
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
import com.nebula.blog.service.AiRelayProviderPackageAdminService;
import com.nebula.blog.vo.admin.AiRelayPackageModelAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPackageAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPackageLimitAdminVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI中转服务商套餐管理服务实现（管理员端）
 */
@Service
@RequiredArgsConstructor
public class AiRelayProviderPackageAdminServiceImpl implements AiRelayProviderPackageAdminService {

    private static final BigDecimal DEFAULT_CONSUME_MULTIPLIER = new BigDecimal("1.0000");

    private final AiRelayProviderPackageMapper packageMapper;
    private final AiRelayProviderPackageLimitMapper limitMapper;
    private final AiRelayPackageModelMapper packageModelMapper;
    private final AiRelayProviderMapper providerMapper;
    private final AiRelayPackageTypeMapper packageTypeMapper;
    private final AiRelayModelMapper modelMapper;

    @Override
    public PageResult<AiRelayProviderPackageAdminVO> page(AiRelayProviderPackagePageQuery query) {
        AiRelayProviderPackagePageQuery safe = query == null ? new AiRelayProviderPackagePageQuery() : query;
        Page<AiRelayProviderPackage> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayProviderPackage> wrapper = new LambdaQueryWrapper<AiRelayProviderPackage>()
                .eq(safe.getProviderId() != null, AiRelayProviderPackage::getProviderId, safe.getProviderId())
                .eq(safe.getPackageTypeId() != null, AiRelayProviderPackage::getPackageTypeId, safe.getPackageTypeId())
                .like(StringUtils.hasText(safe.getKeyword()), AiRelayProviderPackage::getName, safe.getKeyword())
                .eq(safe.getStatus() != null, AiRelayProviderPackage::getStatus, safe.getStatus())
                .orderByDesc(AiRelayProviderPackage::getRecommendScore)
                .orderByAsc(AiRelayProviderPackage::getSortOrder)
                .orderByDesc(AiRelayProviderPackage::getCreateTime);
        Page<AiRelayProviderPackage> result = packageMapper.selectPage(page, wrapper);
        List<AiRelayProviderPackage> records = result.getRecords();
        List<AiRelayProviderPackageAdminVO> rows = enrichPackages(records);
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayProviderPackageAdminVO detail(Long id) {
        AiRelayProviderPackage entity = requirePackage(id);
        return enrichPackages(List.of(entity)).get(0);
    }

    @Override
    public Long create(AiRelayProviderPackageCreateRequest req) {
        validateCreateRequest(req);
        requireProvider(req.getProviderId());
        requirePackageType(req.getPackageTypeId());

        AiRelayProviderPackage entity = new AiRelayProviderPackage();
        entity.setProviderId(req.getProviderId());
        entity.setPackageTypeId(req.getPackageTypeId());
        entity.setName(req.getName());
        entity.setPrice(req.getPrice());
        entity.setOriginalPrice(req.getOriginalPrice());
        entity.setCurrency(StringUtils.hasText(req.getCurrency()) ? req.getCurrency() : "CNY");
        entity.setIsRecommended(req.getIsRecommended() != null ? req.getIsRecommended() : 0);
        entity.setRecommendScore(req.getRecommendScore() != null ? req.getRecommendScore() : BigDecimal.ZERO);
        entity.setDescription(req.getDescription());
        entity.setSortOrder(req.getSortOrder() != null ? req.getSortOrder() : 0);
        entity.setStatus(req.getStatus() != null ? req.getStatus() : 1);
        packageMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void update(Long id, AiRelayProviderPackageUpdateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        AiRelayProviderPackage existing = requirePackage(id);

        if (req.getPackageTypeId() != null && !req.getPackageTypeId().equals(existing.getPackageTypeId())) {
            requirePackageType(req.getPackageTypeId());
            existing.setPackageTypeId(req.getPackageTypeId());
        }
        if (StringUtils.hasText(req.getName())) existing.setName(req.getName());
        if (req.getPrice() != null) existing.setPrice(req.getPrice());
        if (req.getOriginalPrice() != null) existing.setOriginalPrice(req.getOriginalPrice());
        if (StringUtils.hasText(req.getCurrency())) existing.setCurrency(req.getCurrency());
        if (req.getIsRecommended() != null) existing.setIsRecommended(req.getIsRecommended());
        if (req.getRecommendScore() != null) existing.setRecommendScore(req.getRecommendScore());
        if (req.getDescription() != null) existing.setDescription(req.getDescription());
        if (req.getSortOrder() != null) existing.setSortOrder(req.getSortOrder());
        if (req.getStatus() != null) existing.setStatus(req.getStatus());
        packageMapper.updateById(existing);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requirePackage(id);
        limitMapper.delete(
                new LambdaQueryWrapper<AiRelayProviderPackageLimit>().eq(AiRelayProviderPackageLimit::getPackageId, id)
        );
        packageModelMapper.delete(
                new LambdaQueryWrapper<AiRelayPackageModel>().eq(AiRelayPackageModel::getPackageId, id)
        );
        packageMapper.deleteById(id);
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        if (status == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "状态不能为空");
        }
        AiRelayProviderPackage existing = requirePackage(id);
        existing.setStatus(status);
        packageMapper.updateById(existing);
    }

    // ============ 套餐限制 ============

    @Override
    public List<AiRelayProviderPackageLimitAdminVO> listLimits(Long packageId) {
        requirePackage(packageId);
        return limitMapper.selectList(
                        new LambdaQueryWrapper<AiRelayProviderPackageLimit>()
                                .eq(AiRelayProviderPackageLimit::getPackageId, packageId)
                                .orderByAsc(AiRelayProviderPackageLimit::getLimitType)
                                .orderByDesc(AiRelayProviderPackageLimit::getCreateTime)
                ).stream()
                .map(this::toLimitVO)
                .toList();
    }

    @Override
    public Long createLimit(Long packageId, AiRelayProviderPackageLimitRequest req) {
        requirePackage(packageId);
        validateLimitRequest(req);
        AiRelayProviderPackageLimit entity = new AiRelayProviderPackageLimit();
        entity.setPackageId(packageId);
        applyLimitRequest(entity, req);
        if (entity.getResetCycle() == null) entity.setResetCycle(0);
        if (entity.getOverLimitStrategy() == null) entity.setOverLimitStrategy(1);
        if (entity.getStatus() == null) entity.setStatus(1);
        limitMapper.insert(entity);
        return entity.getId();
    }

    @Override
    public void updateLimit(Long packageId, Long limitId, AiRelayProviderPackageLimitRequest req) {
        validateLimitRequest(req);
        AiRelayProviderPackageLimit existing = requireLimit(packageId, limitId);
        applyLimitRequest(existing, req);
        limitMapper.updateById(existing);
    }

    @Override
    public void deleteLimit(Long packageId, Long limitId) {
        requireLimit(packageId, limitId);
        limitMapper.deleteById(limitId);
    }

    // ============ 套餐模型 ============

    @Override
    public List<AiRelayPackageModelAdminVO> listModels(Long packageId) {
        requirePackage(packageId);
        List<AiRelayPackageModel> relations = packageModelMapper.selectList(
                new LambdaQueryWrapper<AiRelayPackageModel>()
                        .eq(AiRelayPackageModel::getPackageId, packageId)
                        .orderByDesc(AiRelayPackageModel::getIsDefault)
                        .orderByAsc(AiRelayPackageModel::getSortOrder)
                        .orderByDesc(AiRelayPackageModel::getCreateTime)
        );
        if (relations.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> modelIds = relations.stream().map(AiRelayPackageModel::getModelId).distinct().toList();
        Map<Long, AiRelayModel> modelMap = modelMapper.selectBatchIds(modelIds).stream()
                .collect(Collectors.toMap(AiRelayModel::getId, Function.identity()));
        return relations.stream()
                .map(rel -> toPackageModelVO(rel, modelMap.get(rel.getModelId())))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createModel(Long packageId, AiRelayPackageModelRequest req) {
        requirePackage(packageId);
        validatePackageModelRequest(req);
        if (modelMapper.selectById(req.getModelId()) == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模型不存在");
        }
        long dupCount = packageModelMapper.selectCount(
                new LambdaQueryWrapper<AiRelayPackageModel>()
                        .eq(AiRelayPackageModel::getPackageId, packageId)
                        .eq(AiRelayPackageModel::getModelId, req.getModelId())
        );
        if (dupCount > 0) {
            throw new BizException(HttpStatus.BAD_REQUEST, "该模型已绑定到当前套餐");
        }

        AiRelayPackageModel entity = new AiRelayPackageModel();
        entity.setPackageId(packageId);
        entity.setModelId(req.getModelId());
        applyPackageModelRequest(entity, req);
        if (entity.getConsumeMultiplier() == null) entity.setConsumeMultiplier(DEFAULT_CONSUME_MULTIPLIER);
        if (entity.getIsDefault() == null) entity.setIsDefault(0);
        if (entity.getSortOrder() == null) entity.setSortOrder(0);
        if (entity.getStatus() == null) entity.setStatus(1);

        if (Integer.valueOf(1).equals(entity.getIsDefault())) {
            clearOtherDefaults(packageId, null);
        }
        packageModelMapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateModel(Long packageId, Long packageModelId, AiRelayPackageModelRequest req) {
        validatePackageModelRequest(req);
        AiRelayPackageModel existing = requirePackageModel(packageId, packageModelId);

        if (!req.getModelId().equals(existing.getModelId())) {
            if (modelMapper.selectById(req.getModelId()) == null) {
                throw new BizException(HttpStatus.BAD_REQUEST, "模型不存在");
            }
            long dupCount = packageModelMapper.selectCount(
                    new LambdaQueryWrapper<AiRelayPackageModel>()
                            .eq(AiRelayPackageModel::getPackageId, packageId)
                            .eq(AiRelayPackageModel::getModelId, req.getModelId())
                            .ne(AiRelayPackageModel::getId, packageModelId)
            );
            if (dupCount > 0) {
                throw new BizException(HttpStatus.BAD_REQUEST, "该模型已绑定到当前套餐");
            }
            existing.setModelId(req.getModelId());
        }
        applyPackageModelRequest(existing, req);

        if (Integer.valueOf(1).equals(existing.getIsDefault())) {
            clearOtherDefaults(packageId, packageModelId);
        }
        packageModelMapper.updateById(existing);
    }

    @Override
    public void deleteModel(Long packageId, Long packageModelId) {
        requirePackageModel(packageId, packageModelId);
        packageModelMapper.deleteById(packageModelId);
    }

    // ============ 内部工具 ============

    private AiRelayProviderPackage requirePackage(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐ID不能为空");
        }
        AiRelayProviderPackage entity = packageMapper.selectById(id);
        if (entity == null) {
            throw new BizException(HttpStatus.NOT_FOUND, "套餐不存在");
        }
        return entity;
    }

    private void requireProvider(Long providerId) {
        if (providerMapper.selectById(providerId) == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商不存在");
        }
    }

    private void requirePackageType(Long packageTypeId) {
        if (packageTypeMapper.selectById(packageTypeId) == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐类型不存在");
        }
    }

    private void validateCreateRequest(AiRelayProviderPackageCreateRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (req.getProviderId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商ID不能为空");
        }
        if (req.getPackageTypeId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐类型ID不能为空");
        }
        if (!StringUtils.hasText(req.getName())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐名称不能为空");
        }
        if (req.getPrice() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐价格不能为空");
        }
    }

    private void validateLimitRequest(AiRelayProviderPackageLimitRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (req.getLimitType() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "限制类型不能为空");
        }
        if (req.getQuotaAmount() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "额度数量不能为空");
        }
        if (!StringUtils.hasText(req.getQuotaUnit())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "额度单位不能为空");
        }
    }

    private void validatePackageModelRequest(AiRelayPackageModelRequest req) {
        if (req == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "请求参数不能为空");
        }
        if (req.getModelId() == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模型ID不能为空");
        }
    }

    private AiRelayProviderPackageLimit requireLimit(Long packageId, Long limitId) {
        if (limitId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐限制ID不能为空");
        }
        requirePackage(packageId);
        AiRelayProviderPackageLimit entity = limitMapper.selectById(limitId);
        if (entity == null || !packageId.equals(entity.getPackageId())) {
            throw new BizException(HttpStatus.NOT_FOUND, "套餐限制不存在");
        }
        return entity;
    }

    private AiRelayPackageModel requirePackageModel(Long packageId, Long packageModelId) {
        if (packageModelId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐模型ID不能为空");
        }
        requirePackage(packageId);
        AiRelayPackageModel entity = packageModelMapper.selectById(packageModelId);
        if (entity == null || !packageId.equals(entity.getPackageId())) {
            throw new BizException(HttpStatus.NOT_FOUND, "套餐模型不存在");
        }
        return entity;
    }

    private void applyLimitRequest(AiRelayProviderPackageLimit entity, AiRelayProviderPackageLimitRequest req) {
        entity.setLimitType(req.getLimitType());
        entity.setQuotaAmount(req.getQuotaAmount());
        entity.setQuotaUnit(req.getQuotaUnit());
        if (req.getResetCycle() != null) entity.setResetCycle(req.getResetCycle());
        if (req.getOverLimitStrategy() != null) entity.setOverLimitStrategy(req.getOverLimitStrategy());
        entity.setDescription(req.getDescription());
        if (req.getStatus() != null) entity.setStatus(req.getStatus());
    }

    private void applyPackageModelRequest(AiRelayPackageModel entity, AiRelayPackageModelRequest req) {
        entity.setProviderModelCode(req.getProviderModelCode());
        if (req.getConsumeMultiplier() != null) {
            entity.setConsumeMultiplier(req.getConsumeMultiplier());
        }
        entity.setMinChargeAmount(req.getMinChargeAmount());
        entity.setMaxContextTokens(req.getMaxContextTokens());
        entity.setInputPricePerMillionTokens(req.getInputPricePerMillionTokens());
        entity.setOutputPricePerMillionTokens(req.getOutputPricePerMillionTokens());
        if (req.getIsDefault() != null) entity.setIsDefault(req.getIsDefault());
        if (req.getSortOrder() != null) entity.setSortOrder(req.getSortOrder());
        if (req.getStatus() != null) entity.setStatus(req.getStatus());
    }

    private void clearOtherDefaults(Long packageId, Long excludeId) {
        List<AiRelayPackageModel> defaults = packageModelMapper.selectList(
                new LambdaQueryWrapper<AiRelayPackageModel>()
                        .eq(AiRelayPackageModel::getPackageId, packageId)
                        .eq(AiRelayPackageModel::getIsDefault, 1)
        );
        for (AiRelayPackageModel d : defaults) {
            if (excludeId != null && excludeId.equals(d.getId())) {
                continue;
            }
            d.setIsDefault(0);
            packageModelMapper.updateById(d);
        }
    }

    private List<AiRelayProviderPackageAdminVO> enrichPackages(List<AiRelayProviderPackage> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> providerIds = records.stream()
                .map(AiRelayProviderPackage::getProviderId)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        List<Long> typeIds = records.stream()
                .map(AiRelayProviderPackage::getPackageTypeId)
                .filter(java.util.Objects::nonNull)
                .distinct().toList();
        Map<Long, AiRelayProvider> providerMap = providerIds.isEmpty()
                ? Map.of()
                : providerMapper.selectBatchIds(providerIds).stream()
                .collect(Collectors.toMap(AiRelayProvider::getId, Function.identity()));
        Map<Long, AiRelayPackageType> typeMap = typeIds.isEmpty()
                ? Map.of()
                : packageTypeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(AiRelayPackageType::getId, Function.identity()));
        return records.stream()
                .map(r -> toVO(r, providerMap.get(r.getProviderId()), typeMap.get(r.getPackageTypeId())))
                .toList();
    }

    private AiRelayProviderPackageAdminVO toVO(AiRelayProviderPackage entity,
                                               AiRelayProvider provider,
                                               AiRelayPackageType type) {
        AiRelayProviderPackageAdminVO vo = new AiRelayProviderPackageAdminVO();
        vo.setId(entity.getId());
        vo.setProviderId(entity.getProviderId());
        vo.setPackageTypeId(entity.getPackageTypeId());
        vo.setName(entity.getName());
        vo.setPrice(entity.getPrice());
        vo.setOriginalPrice(entity.getOriginalPrice());
        vo.setCurrency(entity.getCurrency());
        vo.setIsRecommended(entity.getIsRecommended());
        vo.setRecommendScore(entity.getRecommendScore());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        if (provider != null) vo.setProviderName(provider.getName());
        if (type != null) {
            vo.setPackageTypeCode(type.getCode());
            vo.setPackageTypeName(type.getName());
        }
        return vo;
    }

    private AiRelayProviderPackageLimitAdminVO toLimitVO(AiRelayProviderPackageLimit entity) {
        AiRelayProviderPackageLimitAdminVO vo = new AiRelayProviderPackageLimitAdminVO();
        vo.setId(entity.getId());
        vo.setPackageId(entity.getPackageId());
        vo.setLimitType(entity.getLimitType());
        vo.setQuotaAmount(entity.getQuotaAmount());
        vo.setQuotaUnit(entity.getQuotaUnit());
        vo.setResetCycle(entity.getResetCycle());
        vo.setOverLimitStrategy(entity.getOverLimitStrategy());
        vo.setDescription(entity.getDescription());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }

    private AiRelayPackageModelAdminVO toPackageModelVO(AiRelayPackageModel entity, AiRelayModel model) {
        AiRelayPackageModelAdminVO vo = new AiRelayPackageModelAdminVO();
        vo.setId(entity.getId());
        vo.setPackageId(entity.getPackageId());
        vo.setModelId(entity.getModelId());
        vo.setProviderModelCode(entity.getProviderModelCode());
        vo.setConsumeMultiplier(entity.getConsumeMultiplier());
        vo.setMinChargeAmount(entity.getMinChargeAmount());
        vo.setMaxContextTokens(entity.getMaxContextTokens());
        vo.setInputPricePerMillionTokens(entity.getInputPricePerMillionTokens());
        vo.setOutputPricePerMillionTokens(entity.getOutputPricePerMillionTokens());
        vo.setIsDefault(entity.getIsDefault());
        vo.setSortOrder(entity.getSortOrder());
        vo.setStatus(entity.getStatus());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        if (model != null) {
            vo.setModelCode(model.getCode());
            vo.setModelName(model.getName());
            vo.setModelVendor(model.getModelVendor());
        }
        return vo;
    }
}
