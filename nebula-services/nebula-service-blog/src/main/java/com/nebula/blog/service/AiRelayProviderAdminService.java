package com.nebula.blog.service;

import com.nebula.blog.dto.admin.AiRelayProviderAdvantageRequest;
import com.nebula.blog.dto.admin.AiRelayProviderCreateRequest;
import com.nebula.blog.dto.admin.AiRelayProviderPageQuery;
import com.nebula.blog.dto.admin.AiRelayProviderPaymentMethodBindRequest;
import com.nebula.blog.dto.admin.AiRelayProviderUpdateRequest;
import com.nebula.blog.vo.admin.AiRelayProviderAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderAdvantageAdminVO;
import com.nebula.blog.vo.admin.AiRelayProviderPaymentMethodAdminVO;
import com.nebula.common.core.domain.PageResult;

import java.util.List;

/**
 * AI中转服务商管理服务接口（管理员端）
 */
public interface AiRelayProviderAdminService {

    /**
     * 分页查询服务商
     */
    PageResult<AiRelayProviderAdminVO> page(AiRelayProviderPageQuery query);

    /**
     * 服务商详情
     */
    AiRelayProviderAdminVO detail(Long id);

    /**
     * 创建服务商
     */
    Long create(AiRelayProviderCreateRequest req);

    /**
     * 更新服务商
     */
    void update(Long id, AiRelayProviderUpdateRequest req);

    /**
     * 删除服务商
     */
    void delete(Long id);

    /**
     * 切换服务商上下线状态
     */
    void updateStatus(Long id, Integer status);

    // ============ 服务商优势（子资源） ============

    /**
     * 获取服务商优势列表
     */
    List<AiRelayProviderAdvantageAdminVO> listAdvantages(Long providerId);

    /**
     * 新增服务商优势
     */
    Long createAdvantage(Long providerId, AiRelayProviderAdvantageRequest req);

    /**
     * 更新服务商优势
     */
    void updateAdvantage(Long providerId, Long advantageId, AiRelayProviderAdvantageRequest req);

    /**
     * 删除服务商优势
     */
    void deleteAdvantage(Long providerId, Long advantageId);

    // ============ 服务商支付方式（子资源） ============

    /**
     * 获取服务商已绑定的支付方式
     */
    List<AiRelayProviderPaymentMethodAdminVO> listPaymentMethods(Long providerId);

    /**
     * 全量绑定服务商支付方式
     */
    void bindPaymentMethods(Long providerId, AiRelayProviderPaymentMethodBindRequest req);
}
