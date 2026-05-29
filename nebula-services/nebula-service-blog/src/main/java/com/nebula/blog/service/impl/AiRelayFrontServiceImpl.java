package com.nebula.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nebula.blog.dto.front.AiRelayModelFrontPageQuery;
import com.nebula.blog.dto.front.AiRelayPackageFrontPageQuery;
import com.nebula.blog.dto.front.AiRelayProviderFrontPageQuery;
import com.nebula.blog.entity.AiRelayModel;
import com.nebula.blog.entity.AiRelayPackageModel;
import com.nebula.blog.entity.AiRelayPackageType;
import com.nebula.blog.entity.AiRelayPaymentMethod;
import com.nebula.blog.entity.AiRelayProvider;
import com.nebula.blog.entity.AiRelayProviderAdvantage;
import com.nebula.blog.entity.AiRelayProviderPackage;
import com.nebula.blog.entity.AiRelayProviderPackageLimit;
import com.nebula.blog.entity.AiRelayProviderPaymentMethod;
import com.nebula.blog.entity.BlogFileAsset;
import com.nebula.blog.mapper.AiRelayModelMapper;
import com.nebula.blog.mapper.AiRelayPackageModelMapper;
import com.nebula.blog.mapper.AiRelayPackageTypeMapper;
import com.nebula.blog.mapper.AiRelayPaymentMethodMapper;
import com.nebula.blog.mapper.AiRelayProviderAdvantageMapper;
import com.nebula.blog.mapper.AiRelayProviderMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageLimitMapper;
import com.nebula.blog.mapper.AiRelayProviderPackageMapper;
import com.nebula.blog.mapper.AiRelayProviderPaymentMethodMapper;
import com.nebula.blog.mapper.BlogFileAssetMapper;
import com.nebula.blog.service.AiRelayModelFrontService;
import com.nebula.blog.service.AiRelayPackageTypeFrontService;
import com.nebula.blog.service.AiRelayPaymentMethodFrontService;
import com.nebula.blog.service.AiRelayProviderFrontService;
import com.nebula.blog.service.AiRelayProviderPackageFrontService;
import com.nebula.blog.vo.front.AiRelayModelFrontVO;
import com.nebula.blog.vo.front.AiRelayOptionVO;
import com.nebula.blog.vo.front.AiRelayPackageFrontVO;
import com.nebula.blog.vo.front.AiRelayPackageLimitFrontVO;
import com.nebula.blog.vo.front.AiRelayPackageModelFrontVO;
import com.nebula.blog.vo.front.AiRelayPackageTypeFrontVO;
import com.nebula.blog.vo.front.AiRelayPaymentMethodFrontVO;
import com.nebula.blog.vo.front.AiRelayProviderAdvantageFrontVO;
import com.nebula.blog.vo.front.AiRelayProviderFrontVO;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.domain.PageResult;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.oss.api.ObjectStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * AI 中转前台服务统一实现。
 *
 * <p>5 个 front service 接口共享同一份聚合数据加载与裁剪逻辑，避免重复 N+1 查询。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiRelayFrontServiceImpl implements
        AiRelayProviderFrontService,
        AiRelayProviderPackageFrontService,
        AiRelayModelFrontService,
        AiRelayPackageTypeFrontService,
        AiRelayPaymentMethodFrontService {

    private static final int STATUS_ACTIVE = 1;
    private static final String OSS_STORAGE_TYPE = "oss";
    private static final int BILLING_MODE_SUBSCRIPTION = 1;
    private static final int BILLING_MODE_USAGE = 2;
    private static final String BILLING_USAGE = "usage";
    private static final String BILLING_SUBSCRIPTION = "subscription";

    private final AiRelayProviderMapper providerMapper;
    private final AiRelayProviderPackageMapper packageMapper;
    private final AiRelayProviderPackageLimitMapper limitMapper;
    private final AiRelayPackageModelMapper packageModelMapper;
    private final AiRelayProviderAdvantageMapper advantageMapper;
    private final AiRelayProviderPaymentMethodMapper providerPaymentMethodMapper;
    private final AiRelayPaymentMethodMapper paymentMethodMapper;
    private final AiRelayPackageTypeMapper packageTypeMapper;
    private final AiRelayModelMapper modelMapper;
    private final BlogFileAssetMapper fileAssetMapper;

    @Qualifier("minioObjectStorageService")
    private final ObjectStorageService ossService;

    @Value("${blog.post.file.presigned-url-expiry-seconds:3600}")
    private int presignedUrlExpirySeconds;

    // ===================================================================
    // Provider
    // ===================================================================

    @Override
    public PageResult<AiRelayProviderFrontVO> pageProviders(AiRelayProviderFrontPageQuery query) {
        AiRelayProviderFrontPageQuery safe = query == null ? new AiRelayProviderFrontPageQuery() : query;

        Page<AiRelayProvider> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayProvider> wrapper = new LambdaQueryWrapper<AiRelayProvider>()
                .eq(AiRelayProvider::getStatus, STATUS_ACTIVE)
                .like(StringUtils.hasText(safe.getKeyword()), AiRelayProvider::getName, safe.getKeyword());
        if (safe.getLastSyncTimeStart() != null) {
            wrapper.ge(AiRelayProvider::getLastSyncTime, safe.getLastSyncTimeStart().atStartOfDay());
        }
        if (safe.getLastSyncTimeEnd() != null) {
            wrapper.lt(AiRelayProvider::getLastSyncTime, safe.getLastSyncTimeEnd().plusDays(1).atStartOfDay());
        }
        applyProviderSort(wrapper, safe.getSortBy());

        Page<AiRelayProvider> result = providerMapper.selectPage(page, wrapper);
        List<AiRelayProviderFrontVO> rows = enrichProviders(result.getRecords());
        rows = applyProviderFilters(rows, safe.getModelVendor(), safe.getBillingMode(), safe.getPackageTypeCode());

        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayProviderFrontVO getProvider(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商ID不能为空");
        }
        AiRelayProvider entity = providerMapper.selectById(id);
        if (entity == null || !Integer.valueOf(STATUS_ACTIVE).equals(entity.getStatus())) {
            throw new BizException(HttpStatus.NOT_FOUND, "服务商不存在");
        }
        List<AiRelayProviderFrontVO> rows = enrichProviders(List.of(entity));
        return rows.isEmpty() ? null : rows.get(0);
    }

    private void applyProviderSort(LambdaQueryWrapper<AiRelayProvider> wrapper, String sortBy) {
        // 价格 / 稳定性当前数据库无对应字段，stub 时仍按 recommend 排序，由前台二次排序
        wrapper.orderByDesc(AiRelayProvider::getRecommendScore)
                .orderByAsc(AiRelayProvider::getSortOrder)
                .orderByDesc(AiRelayProvider::getCreateTime);
    }

    private List<AiRelayProviderFrontVO> applyProviderFilters(List<AiRelayProviderFrontVO> rows,
                                                              String modelVendor,
                                                              String billingMode,
                                                              String packageTypeCode) {
        if (rows.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(p -> {
                    if (StringUtils.hasText(modelVendor)) {
                        boolean matched = p.getVendorTypes() != null
                                && p.getVendorTypes().stream().anyMatch(v -> modelVendor.equalsIgnoreCase(v));
                        if (!matched) return false;
                    }
                    if (StringUtils.hasText(billingMode)) {
                        boolean matched = p.getBillingModes() != null
                                && p.getBillingModes().contains(billingMode);
                        if (!matched) return false;
                    }
                    if (StringUtils.hasText(packageTypeCode)) {
                        boolean matched = p.getPackageTypeCodes() != null
                                && p.getPackageTypeCodes().stream().anyMatch(packageTypeCode::equalsIgnoreCase);
                        if (!matched) return false;
                    }
                    return true;
                })
                .toList();
    }

    /**
     * 加载一批服务商的全部聚合信息（套餐 / 限制 / 模型 / 优势 / 支付方式）。
     */
    private List<AiRelayProviderFrontVO> enrichProviders(List<AiRelayProvider> providers) {
        if (providers == null || providers.isEmpty()) {
            return List.of();
        }
        List<Long> providerIds = providers.stream().map(AiRelayProvider::getId).distinct().toList();

        // ---- 套餐 ----
        List<AiRelayProviderPackage> packages = packageMapper.selectList(
                new LambdaQueryWrapper<AiRelayProviderPackage>()
                        .in(AiRelayProviderPackage::getProviderId, providerIds)
                        .eq(AiRelayProviderPackage::getStatus, STATUS_ACTIVE)
                        .orderByDesc(AiRelayProviderPackage::getIsRecommended)
                        .orderByDesc(AiRelayProviderPackage::getRecommendScore)
                        .orderByAsc(AiRelayProviderPackage::getSortOrder)
                        .orderByAsc(AiRelayProviderPackage::getId));
        Map<Long, List<AiRelayPackageFrontVO>> packagesByProvider = enrichPackages(packages).stream()
                .collect(Collectors.groupingBy(AiRelayPackageFrontVO::getProviderId, LinkedHashMap::new, Collectors.toList()));

        // ---- 优势 ----
        List<AiRelayProviderAdvantage> advantages = advantageMapper.selectList(
                new LambdaQueryWrapper<AiRelayProviderAdvantage>()
                        .in(AiRelayProviderAdvantage::getProviderId, providerIds)
                        .eq(AiRelayProviderAdvantage::getStatus, STATUS_ACTIVE)
                        .orderByDesc(AiRelayProviderAdvantage::getAdvantageType)
                        .orderByAsc(AiRelayProviderAdvantage::getSortOrder)
                        .orderByAsc(AiRelayProviderAdvantage::getId));
        Map<Long, BlogFileAsset> advantageIcons = loadFileAssets(advantages.stream()
                .map(AiRelayProviderAdvantage::getIconFileId).filter(Objects::nonNull).toList());
        Map<Long, List<AiRelayProviderAdvantageFrontVO>> advByProvider = advantages.stream()
                .map(adv -> toAdvantageVO(adv, advantageIcons.get(adv.getIconFileId())))
                .collect(Collectors.groupingBy(AiRelayProviderAdvantageFrontVO::getProviderId,
                        LinkedHashMap::new, Collectors.toList()));

        // ---- 支付方式 ----
        List<AiRelayProviderPaymentMethod> providerPayments = providerPaymentMethodMapper.selectList(
                new LambdaQueryWrapper<AiRelayProviderPaymentMethod>()
                        .in(AiRelayProviderPaymentMethod::getProviderId, providerIds)
                        .eq(AiRelayProviderPaymentMethod::getStatus, STATUS_ACTIVE)
                        .orderByAsc(AiRelayProviderPaymentMethod::getSortOrder)
                        .orderByAsc(AiRelayProviderPaymentMethod::getId));
        List<Long> paymentMethodIds = providerPayments.stream()
                .map(AiRelayProviderPaymentMethod::getPaymentMethodId).filter(Objects::nonNull).distinct().toList();
        Map<Long, AiRelayPaymentMethod> paymentMap = paymentMethodIds.isEmpty() ? new java.util.HashMap<>()
                : paymentMethodMapper.selectBatchIds(paymentMethodIds).stream()
                .filter(m -> Integer.valueOf(STATUS_ACTIVE).equals(m.getStatus()))
                .collect(Collectors.toMap(AiRelayPaymentMethod::getId, Function.identity()));
        Map<Long, BlogFileAsset> paymentIcons = loadFileAssets(paymentMap.values().stream()
                .map(AiRelayPaymentMethod::getIconFileId).filter(Objects::nonNull).toList());
        Map<Long, List<AiRelayPaymentMethodFrontVO>> paymentByProvider = new LinkedHashMap<>();
        for (AiRelayProviderPaymentMethod rel : providerPayments) {
            AiRelayPaymentMethod method = paymentMap.get(rel.getPaymentMethodId());
            if (method == null) continue;
            paymentByProvider
                    .computeIfAbsent(rel.getProviderId(), k -> new ArrayList<>())
                    .add(toPaymentMethodVO(method, paymentIcons.get(method.getIconFileId())));
        }

        // ---- 服务商 logo ----
        Map<Long, BlogFileAsset> providerLogos = loadFileAssets(providers.stream()
                .map(AiRelayProvider::getLogoFileId).filter(Objects::nonNull).toList());

        return providers.stream()
                .map(p -> toProviderVO(p,
                        providerLogos.get(p.getLogoFileId()),
                        packagesByProvider.getOrDefault(p.getId(), List.of()),
                        advByProvider.getOrDefault(p.getId(), List.of()),
                        paymentByProvider.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    // ===================================================================
    // Package
    // ===================================================================

    @Override
    public PageResult<AiRelayPackageFrontVO> pagePackages(AiRelayPackageFrontPageQuery query) {
        AiRelayPackageFrontPageQuery safe = query == null ? new AiRelayPackageFrontPageQuery() : query;

        Long typeIdByCode = null;
        if (StringUtils.hasText(safe.getPackageTypeCode())) {
            AiRelayPackageType type = packageTypeMapper.selectOne(
                    new LambdaQueryWrapper<AiRelayPackageType>()
                            .eq(AiRelayPackageType::getCode, safe.getPackageTypeCode())
                            .eq(AiRelayPackageType::getStatus, STATUS_ACTIVE));
            if (type == null) {
                return PageResult.of(List.of(), 0L, safe.safePageNum(), safe.safePageSize());
            }
            typeIdByCode = type.getId();
        }

        Page<AiRelayProviderPackage> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayProviderPackage> wrapper = new LambdaQueryWrapper<AiRelayProviderPackage>()
                .eq(AiRelayProviderPackage::getStatus, STATUS_ACTIVE)
                .eq(safe.getProviderId() != null, AiRelayProviderPackage::getProviderId, safe.getProviderId())
                .eq(safe.getPackageTypeId() != null, AiRelayProviderPackage::getPackageTypeId, safe.getPackageTypeId())
                .eq(typeIdByCode != null, AiRelayProviderPackage::getPackageTypeId, typeIdByCode)
                .like(StringUtils.hasText(safe.getKeyword()), AiRelayProviderPackage::getName, safe.getKeyword())
                .orderByDesc(AiRelayProviderPackage::getIsRecommended)
                .orderByDesc(AiRelayProviderPackage::getRecommendScore)
                .orderByAsc(AiRelayProviderPackage::getSortOrder)
                .orderByAsc(AiRelayProviderPackage::getId);

        Page<AiRelayProviderPackage> result = packageMapper.selectPage(page, wrapper);
        List<AiRelayPackageFrontVO> rows = enrichPackages(result.getRecords());
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayPackageFrontVO getPackage(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "套餐ID不能为空");
        }
        AiRelayProviderPackage entity = packageMapper.selectById(id);
        if (entity == null || !Integer.valueOf(STATUS_ACTIVE).equals(entity.getStatus())) {
            throw new BizException(HttpStatus.NOT_FOUND, "套餐不存在");
        }
        List<AiRelayPackageFrontVO> rows = enrichPackages(List.of(entity));
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<AiRelayPackageFrontVO> listPackagesByProvider(Long providerId) {
        if (providerId == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "服务商ID不能为空");
        }
        List<AiRelayProviderPackage> records = packageMapper.selectList(
                new LambdaQueryWrapper<AiRelayProviderPackage>()
                        .eq(AiRelayProviderPackage::getProviderId, providerId)
                        .eq(AiRelayProviderPackage::getStatus, STATUS_ACTIVE)
                        .orderByDesc(AiRelayProviderPackage::getIsRecommended)
                        .orderByDesc(AiRelayProviderPackage::getRecommendScore)
                        .orderByAsc(AiRelayProviderPackage::getSortOrder)
                        .orderByAsc(AiRelayProviderPackage::getId));
        return enrichPackages(records);
    }

    /**
     * 批量装配套餐 VO，含类型、限制、模型映射。
     */
    private List<AiRelayPackageFrontVO> enrichPackages(List<AiRelayProviderPackage> records) {
        if (records == null || records.isEmpty()) {
            return List.of();
        }
        List<Long> packageIds = records.stream().map(AiRelayProviderPackage::getId).toList();
        List<Long> providerIds = records.stream()
                .map(AiRelayProviderPackage::getProviderId).filter(Objects::nonNull).distinct().toList();
        List<Long> typeIds = records.stream()
                .map(AiRelayProviderPackage::getPackageTypeId).filter(Objects::nonNull).distinct().toList();

        Map<Long, AiRelayProvider> providerMap = providerIds.isEmpty() ? new java.util.HashMap<>()
                : providerMapper.selectBatchIds(providerIds).stream()
                .collect(Collectors.toMap(AiRelayProvider::getId, Function.identity()));
        Map<Long, AiRelayPackageType> typeMap = typeIds.isEmpty() ? new java.util.HashMap<>()
                : packageTypeMapper.selectBatchIds(typeIds).stream()
                .collect(Collectors.toMap(AiRelayPackageType::getId, Function.identity()));

        // 限制
        List<AiRelayProviderPackageLimit> limits = limitMapper.selectList(
                new LambdaQueryWrapper<AiRelayProviderPackageLimit>()
                        .in(AiRelayProviderPackageLimit::getPackageId, packageIds)
                        .eq(AiRelayProviderPackageLimit::getStatus, STATUS_ACTIVE)
                        .orderByAsc(AiRelayProviderPackageLimit::getLimitType)
                        .orderByAsc(AiRelayProviderPackageLimit::getId));
        Map<Long, List<AiRelayPackageLimitFrontVO>> limitsByPackage = limits.stream()
                .map(this::toLimitVO)
                .collect(Collectors.groupingBy(AiRelayPackageLimitFrontVO::getPackageId,
                        LinkedHashMap::new, Collectors.toList()));

        // 套餐模型映射
        List<AiRelayPackageModel> packageModels = packageModelMapper.selectList(
                new LambdaQueryWrapper<AiRelayPackageModel>()
                        .in(AiRelayPackageModel::getPackageId, packageIds)
                        .eq(AiRelayPackageModel::getStatus, STATUS_ACTIVE)
                        .orderByDesc(AiRelayPackageModel::getIsDefault)
                        .orderByAsc(AiRelayPackageModel::getSortOrder)
                        .orderByAsc(AiRelayPackageModel::getId));
        List<Long> modelIds = packageModels.stream()
                .map(AiRelayPackageModel::getModelId).filter(Objects::nonNull).distinct().toList();
        Map<Long, AiRelayModel> modelMap = modelIds.isEmpty() ? new java.util.HashMap<>()
                : modelMapper.selectBatchIds(modelIds).stream()
                .filter(m -> Integer.valueOf(STATUS_ACTIVE).equals(m.getStatus()))
                .collect(Collectors.toMap(AiRelayModel::getId, Function.identity()));
        Map<Long, List<AiRelayPackageModelFrontVO>> modelsByPackage = new LinkedHashMap<>();
        for (AiRelayPackageModel pm : packageModels) {
            AiRelayModel model = modelMap.get(pm.getModelId());
            if (model == null) continue;
            modelsByPackage.computeIfAbsent(pm.getPackageId(), k -> new ArrayList<>())
                    .add(toPackageModelVO(pm, model));
        }

        return records.stream()
                .map(p -> toPackageVO(p,
                        providerMap.get(p.getProviderId()),
                        typeMap.get(p.getPackageTypeId()),
                        limitsByPackage.getOrDefault(p.getId(), List.of()),
                        modelsByPackage.getOrDefault(p.getId(), List.of())))
                .toList();
    }

    // ===================================================================
    // Model
    // ===================================================================

    @Override
    public PageResult<AiRelayModelFrontVO> pageModels(AiRelayModelFrontPageQuery query) {
        AiRelayModelFrontPageQuery safe = query == null ? new AiRelayModelFrontPageQuery() : query;
        Page<AiRelayModel> page = new Page<>(safe.safePageNum(), safe.safePageSize());
        LambdaQueryWrapper<AiRelayModel> wrapper = new LambdaQueryWrapper<AiRelayModel>()
                .eq(AiRelayModel::getStatus, STATUS_ACTIVE)
                .and(StringUtils.hasText(safe.getKeyword()), w -> w
                        .like(AiRelayModel::getCode, safe.getKeyword())
                        .or().like(AiRelayModel::getName, safe.getKeyword()))
                .eq(StringUtils.hasText(safe.getModelVendor()), AiRelayModel::getModelVendor, safe.getModelVendor())
                .eq(safe.getModelType() != null, AiRelayModel::getModelType, safe.getModelType())
                .orderByAsc(AiRelayModel::getSortOrder)
                .orderByDesc(AiRelayModel::getCreateTime);
        Page<AiRelayModel> result = modelMapper.selectPage(page, wrapper);
        List<AiRelayModelFrontVO> rows = result.getRecords().stream().map(this::toModelVO).toList();
        return PageResult.of(rows, result.getTotal(), result.getCurrent(), result.getSize());
    }

    @Override
    public AiRelayModelFrontVO getModel(Long id) {
        if (id == null) {
            throw new BizException(HttpStatus.BAD_REQUEST, "模型ID不能为空");
        }
        AiRelayModel model = modelMapper.selectById(id);
        if (model == null || !Integer.valueOf(STATUS_ACTIVE).equals(model.getStatus())) {
            throw new BizException(HttpStatus.NOT_FOUND, "模型不存在");
        }
        return toModelVO(model);
    }

    @Override
    public List<AiRelayOptionVO> listVendorOptions() {
        List<AiRelayModel> models = modelMapper.selectList(
                new LambdaQueryWrapper<AiRelayModel>()
                        .eq(AiRelayModel::getStatus, STATUS_ACTIVE)
                        .isNotNull(AiRelayModel::getModelVendor)
                        .orderByAsc(AiRelayModel::getSortOrder)
                        .orderByAsc(AiRelayModel::getId));
        LinkedHashMap<String, String> byValue = new LinkedHashMap<>();
        for (AiRelayModel model : models) {
            String raw = model.getModelVendor();
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String value = normalizeVendor(raw);
            byValue.computeIfAbsent(value, v -> formatVendorLabel(v, raw));
        }
        return byValue.entrySet().stream()
                .map(e -> new AiRelayOptionVO(e.getKey(), e.getValue()))
                .toList();
    }

    // ===================================================================
    // PackageType
    // ===================================================================

    @Override
    public List<AiRelayPackageTypeFrontVO> listPackageTypes() {
        return packageTypeMapper.selectList(
                        new LambdaQueryWrapper<AiRelayPackageType>()
                                .eq(AiRelayPackageType::getStatus, STATUS_ACTIVE)
                                .orderByAsc(AiRelayPackageType::getSortOrder)
                                .orderByAsc(AiRelayPackageType::getId))
                .stream()
                .map(this::toPackageTypeVO)
                .toList();
    }

    // ===================================================================
    // PaymentMethod
    // ===================================================================

    @Override
    public List<AiRelayPaymentMethodFrontVO> listPaymentMethods() {
        List<AiRelayPaymentMethod> methods = paymentMethodMapper.selectList(
                new LambdaQueryWrapper<AiRelayPaymentMethod>()
                        .eq(AiRelayPaymentMethod::getStatus, STATUS_ACTIVE)
                        .orderByAsc(AiRelayPaymentMethod::getSortOrder)
                        .orderByAsc(AiRelayPaymentMethod::getId));
        Map<Long, BlogFileAsset> icons = loadFileAssets(methods.stream()
                .map(AiRelayPaymentMethod::getIconFileId).filter(Objects::nonNull).toList());
        return methods.stream()
                .map(m -> toPaymentMethodVO(m, icons.get(m.getIconFileId())))
                .toList();
    }

    // ===================================================================
    // Mapping
    // ===================================================================

    private AiRelayProviderFrontVO toProviderVO(AiRelayProvider entity,
                                                BlogFileAsset logo,
                                                List<AiRelayPackageFrontVO> packages,
                                                List<AiRelayProviderAdvantageFrontVO> advantages,
                                                List<AiRelayPaymentMethodFrontVO> payments) {
        AiRelayProviderFrontVO vo = new AiRelayProviderFrontVO();
        vo.setId(entity.getId());
        vo.setName(entity.getName());
        vo.setLogoText(buildLogoText(entity.getName()));
        vo.setLogoUrl(resolveFileUrl(logo));
        vo.setWebsiteUrl(entity.getWebsiteUrl());
        vo.setDescription(entity.getDescription());
        vo.setRecommendScore(entity.getRecommendScore());
        vo.setSortOrder(entity.getSortOrder());
        vo.setCreateTime(entity.getCreateTime());
        vo.setLastSyncTime(entity.getLastSyncTime());

        vo.setPackages(packages);
        vo.setAdvantages(advantages);
        vo.setPaymentMethods(payments);

        // 套餐覆盖到的模型聚合（去重）
        LinkedHashMap<String, AiRelayModelFrontVO> modelByCode = new LinkedHashMap<>();
        LinkedHashSet<String> vendorTypes = new LinkedHashSet<>();
        LinkedHashSet<String> billingModes = new LinkedHashSet<>();
        LinkedHashSet<String> packageTypeCodes = new LinkedHashSet<>();
        for (AiRelayPackageFrontVO pkg : packages) {
            if (pkg.getBillingMode() != null) {
                billingModes.add(pkg.getBillingMode());
            }
            if (StringUtils.hasText(pkg.getPackageTypeCode())) {
                packageTypeCodes.add(pkg.getPackageTypeCode());
            }
            if (pkg.getModels() != null) {
                for (AiRelayPackageModelFrontVO m : pkg.getModels()) {
                    if (!StringUtils.hasText(m.getModelCode())) continue;
                    modelByCode.computeIfAbsent(m.getModelCode(), code -> {
                        AiRelayModelFrontVO mv = new AiRelayModelFrontVO();
                        mv.setId(m.getModelId());
                        mv.setCode(m.getModelCode());
                        mv.setName(m.getModelName());
                        mv.setModelVendor(m.getModelVendor());
                        return mv;
                    });
                    if (StringUtils.hasText(m.getModelVendor())) {
                        vendorTypes.add(normalizeVendor(m.getModelVendor()));
                    }
                }
            }
        }
        vo.setModels(new ArrayList<>(modelByCode.values()));
        vo.setVendorTypes(new ArrayList<>(vendorTypes));
        vo.setBillingModes(new ArrayList<>(billingModes));
        vo.setPackageTypeCodes(new ArrayList<>(packageTypeCodes));
        return vo;
    }

    private AiRelayPackageFrontVO toPackageVO(AiRelayProviderPackage entity,
                                              AiRelayProvider provider,
                                              AiRelayPackageType type,
                                              List<AiRelayPackageLimitFrontVO> limits,
                                              List<AiRelayPackageModelFrontVO> models) {
        AiRelayPackageFrontVO vo = new AiRelayPackageFrontVO();
        vo.setId(entity.getId());
        vo.setProviderId(entity.getProviderId());
        if (provider != null) {
            vo.setProviderName(provider.getName());
        }
        vo.setPackageTypeId(entity.getPackageTypeId());
        if (type != null) {
            vo.setPackageTypeCode(type.getCode());
            vo.setPackageTypeName(type.getName());
            vo.setBillingMode(billingModeToString(type.getBillingMode()));
        }
        vo.setName(entity.getName());
        vo.setPrice(entity.getPrice());
        vo.setOriginalPrice(entity.getOriginalPrice());
        vo.setCurrency(entity.getCurrency());
        vo.setRecommended(Integer.valueOf(1).equals(entity.getIsRecommended()));
        vo.setRecommendScore(entity.getRecommendScore());
        vo.setDescription(entity.getDescription());
        vo.setSortOrder(entity.getSortOrder());
        vo.setLimits(limits);
        vo.setModels(models);
        vo.setQuotaSummary(buildQuotaSummary(limits));
        return vo;
    }

    private AiRelayPackageLimitFrontVO toLimitVO(AiRelayProviderPackageLimit entity) {
        AiRelayPackageLimitFrontVO vo = new AiRelayPackageLimitFrontVO();
        vo.setId(entity.getId());
        vo.setPackageId(entity.getPackageId());
        vo.setLimitType(entity.getLimitType());
        vo.setQuotaAmount(entity.getQuotaAmount());
        vo.setQuotaUnit(entity.getQuotaUnit());
        vo.setResetCycle(entity.getResetCycle());
        vo.setOverLimitStrategy(entity.getOverLimitStrategy());
        vo.setDescription(entity.getDescription());
        return vo;
    }

    private AiRelayPackageModelFrontVO toPackageModelVO(AiRelayPackageModel entity, AiRelayModel model) {
        AiRelayPackageModelFrontVO vo = new AiRelayPackageModelFrontVO();
        vo.setId(entity.getId());
        vo.setPackageId(entity.getPackageId());
        vo.setModelId(entity.getModelId());
        vo.setProviderModelCode(entity.getProviderModelCode());
        vo.setConsumeMultiplier(entity.getConsumeMultiplier());
        vo.setMinChargeAmount(entity.getMinChargeAmount());
        vo.setMaxContextTokens(entity.getMaxContextTokens());
        vo.setIsDefault(Integer.valueOf(1).equals(entity.getIsDefault()));
        vo.setModelCode(model.getCode());
        vo.setModelName(model.getName());
        vo.setModelVendor(model.getModelVendor());
        return vo;
    }

    private AiRelayProviderAdvantageFrontVO toAdvantageVO(AiRelayProviderAdvantage entity, BlogFileAsset icon) {
        AiRelayProviderAdvantageFrontVO vo = new AiRelayProviderAdvantageFrontVO();
        vo.setId(entity.getId());
        vo.setProviderId(entity.getProviderId());
        vo.setTitle(entity.getTitle());
        vo.setContent(entity.getContent());
        vo.setAdvantageType(entity.getAdvantageType());
        vo.setIconUrl(resolveFileUrl(icon));
        return vo;
    }

    private AiRelayPaymentMethodFrontVO toPaymentMethodVO(AiRelayPaymentMethod entity, BlogFileAsset icon) {
        AiRelayPaymentMethodFrontVO vo = new AiRelayPaymentMethodFrontVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setDescription(entity.getDescription());
        vo.setIconUrl(resolveFileUrl(icon));
        return vo;
    }

    private AiRelayPackageTypeFrontVO toPackageTypeVO(AiRelayPackageType entity) {
        AiRelayPackageTypeFrontVO vo = new AiRelayPackageTypeFrontVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setBillingMode(billingModeToString(entity.getBillingMode()));
        vo.setDurationValue(entity.getDurationValue());
        vo.setDurationUnit(entity.getDurationUnit());
        vo.setDescription(entity.getDescription());
        return vo;
    }

    private AiRelayModelFrontVO toModelVO(AiRelayModel entity) {
        AiRelayModelFrontVO vo = new AiRelayModelFrontVO();
        vo.setId(entity.getId());
        vo.setCode(entity.getCode());
        vo.setName(entity.getName());
        vo.setModelVendor(entity.getModelVendor());
        vo.setModelType(entity.getModelType());
        vo.setDescription(entity.getDescription());
        return vo;
    }

    // ===================================================================
    // Utilities
    // ===================================================================

    private Map<Long, BlogFileAsset> loadFileAssets(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return new java.util.HashMap<>();
        }
        List<Long> distinct = fileIds.stream().distinct().toList();
        return new java.util.HashMap<>(fileAssetMapper.selectBatchIds(distinct).stream()
                .collect(Collectors.toMap(BlogFileAsset::getId, Function.identity())));
    }

    private String resolveFileUrl(BlogFileAsset asset) {
        if (asset == null) {
            return null;
        }
        if (OSS_STORAGE_TYPE.equals(asset.getStorageType())
                && StringUtils.hasText(asset.getBucket())
                && StringUtils.hasText(asset.getObjectKey())) {
            try {
                return ossService.getPresignedUrl(asset.getBucket(), asset.getObjectKey(),
                        presignedUrlExpirySeconds);
            } catch (Exception e) {
                log.warn("Failed to generate presigned URL, bucket={}, key={}",
                        asset.getBucket(), asset.getObjectKey(), e);
            }
        }
        return asset.getUrl();
    }

    private String billingModeToString(Integer billingMode) {
        if (billingMode == null) return null;
        return switch (billingMode) {
            case BILLING_MODE_USAGE -> BILLING_USAGE;
            case BILLING_MODE_SUBSCRIPTION -> BILLING_SUBSCRIPTION;
            default -> null;
        };
    }

    /**
     * 厂商名称归一化为 vendorTypes 用的小写关键词。
     */
    private String normalizeVendor(String vendor) {
        String v = vendor.trim().toLowerCase();
        if (v.contains("anthropic") || v.contains("claude")) return "claude";
        if (v.contains("openai") || v.contains("gpt")) return "gpt";
        if (v.contains("google") || v.contains("gemini")) return "gemini";
        return v;
    }

    /**
     * 给归一化后的厂商关键词配上面向用户的展示名。
     * 不在白名单内的厂商，回退使用数据库原始字段，避免出现全小写的陌生关键字。
     */
    private static String formatVendorLabel(String value, String rawVendor) {
        return switch (value) {
            case "claude" -> "Claude";
            case "gpt" -> "GPT";
            case "gemini" -> "Gemini";
            default -> StringUtils.hasText(rawVendor) ? rawVendor : value;
        };
    }

    private String buildLogoText(String name) {
        if (!StringUtils.hasText(name)) return "";
        String trimmed = name.trim();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < trimmed.length() && sb.length() < 2; i++) {
            char c = trimmed.charAt(i);
            if (Character.isLetter(c)) {
                sb.append(Character.toUpperCase(c));
            }
        }
        return sb.length() == 0 ? trimmed.substring(0, Math.min(trimmed.length(), 2)) : sb.toString();
    }

    private String buildQuotaSummary(List<AiRelayPackageLimitFrontVO> limits) {
        if (limits == null || limits.isEmpty()) {
            return null;
        }
        AiRelayPackageLimitFrontVO first = limits.get(0);
        if (StringUtils.hasText(first.getDescription())) {
            return first.getDescription();
        }
        BigDecimal qty = first.getQuotaAmount();
        if (qty == null) return null;
        String qtyStr = qty.stripTrailingZeros().setScale(
                qty.scale() < 0 ? 0 : qty.scale(), RoundingMode.UNNECESSARY).toPlainString();
        return qtyStr + (StringUtils.hasText(first.getQuotaUnit()) ? " " + first.getQuotaUnit() : "");
    }
}
