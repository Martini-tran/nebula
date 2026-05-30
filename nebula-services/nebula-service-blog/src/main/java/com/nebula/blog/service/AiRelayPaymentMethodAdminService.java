package com.nebula.blog.service;

import com.nebula.blog.dto.admin.AiRelayPaymentMethodRequest;
import com.nebula.blog.vo.admin.AiRelayPaymentMethodAdminVO;

import java.util.List;

/**
 * AI中转支付方式 管理服务接口（字典）
 */
public interface AiRelayPaymentMethodAdminService {

    /**
     * 列表查询（不分页）
     */
    List<AiRelayPaymentMethodAdminVO> list(Integer status);

    /**
     * 详情
     */
    AiRelayPaymentMethodAdminVO detail(Long id);

    /**
     * 创建
     */
    Long create(AiRelayPaymentMethodRequest req);

    /**
     * 更新
     */
    void update(Long id, AiRelayPaymentMethodRequest req);

    /**
     * 删除
     */
    void delete(Long id);

    /**
     * 切换状态
     */
    void updateStatus(Long id, Integer status);
}
