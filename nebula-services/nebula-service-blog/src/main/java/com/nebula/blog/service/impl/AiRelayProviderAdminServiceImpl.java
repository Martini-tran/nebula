package com.nebula.blog.service.impl;

import com.nebula.blog.dto.admin.AiRelayProviderAdvantageRequest;
import com.nebula.blog.dto.admin.AiRelayProviderCreateRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderPaymentMethodBindRequest;
import com.nebula.blog.dto.admin.AiRelayProviderUpdateRequest;
import com.nebula.blog.mapper.AiRelayProviderAdvantageMapper;
import com.nebula.blog.mapper.AiRelayProviderMapper;
import com.nebula.blog.mapper.AiRelayProviderPaymentMethodMapper;
import com.nebula.blog.service.AiRelayProviderAdminService;
import com.nebula.blog.vo.admin.AiRelayProviderAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderAdvantageAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPaymentMethodAdminVO;
import com.nebula.common.core.domain.PageResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * AI中转服务商管理服务实现（管理员端）
 */
@Service
@RequiredArgsConstructor
public class AiRelayProviderAdminServiceImpl implements AiRelayProviderAdminService {

    private final AiRelayProviderMapper providerMapper;
    private final AiRelayProviderAdvantageMapper advantageMapper;
    private final AiRelayProviderPaymentMethodMapper providerPaymentMethodMapper;

    @Override
    public PageResult<AiRelayProviderAdminVO> page(AiRelayProviderPageQuery query) {
        // TODO: 分页查询服务商
        return PageResult.empty(query.safePageNum(), query.safePageSize());
    }

    @Override
    public AiRelayProviderAdminVO detail(Long id) {
        // TODO: 服务商详情
        return null;
    }

    @Override
    public Long create(AiRelayProviderCreateRequest req) {
        // TODO: 创建服务商
        return null;
    }

    @Override
    public void update(Long id, AiRelayProviderUpdateRequest req) {
        // TODO: 更新服务商
    }

    @Override
    public void delete(Long id) {
        // TODO: 删除服务商，级联清理优势/支付方式/套餐
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        // TODO: 切换服务商状态
    }

    @Override
    public List<AiRelayProviderAdvantageAdminVO> listAdvantages(Long providerId) {
        // TODO: 服务商优势列表
        return Collections.emptyList();
    }

    @Override
    public Long createAdvantage(Long providerId, AiRelayProviderAdvantageRequest req) {
        // TODO: 新增优势
        return null;
    }

    @Override
    public void updateAdvantage(Long providerId, Long advantageId, AiRelayProviderAdvantageRequest req) {
        // TODO: 更新优势
    }

    @Override
    public void deleteAdvantage(Long providerId, Long advantageId) {
        // TODO: 删除优势
    }

    @Override
    public List<AiRelayProviderPaymentMethodAdminVO> listPaymentMethods(Long providerId) {
        // TODO: 服务商已绑定支付方式
        return Collections.emptyList();
    }

    @Override
    public void bindPaymentMethods(Long providerId, AiRelayProviderPaymentMethodBindRequest req) {
        // TODO: 全量覆盖绑定服务商支付方式
    }
}
