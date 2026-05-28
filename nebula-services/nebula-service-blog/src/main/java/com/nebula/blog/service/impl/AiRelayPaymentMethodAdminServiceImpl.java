package com.nebula.blog.service.impl;

import com.nebula.blog.dto.admin.AiRelayPaymentMethodRequest;
import com.nebula.blog.mapper.AiRelayPaymentMethodMapper;
import com.nebula.blog.service.AiRelayPaymentMethodAdminService;
import com.nebula.blog.vo.admin.AiRelayPaymentMethodAdminVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

/**
 * AI中转支付方式 管理服务实现（字典）
 */
@Service
@RequiredArgsConstructor
public class AiRelayPaymentMethodAdminServiceImpl implements AiRelayPaymentMethodAdminService {

    private final AiRelayPaymentMethodMapper paymentMethodMapper;

    @Override
    public List<AiRelayPaymentMethodAdminVO> list(Integer status) {
        // TODO: 支付方式列表
        return Collections.emptyList();
    }

    @Override
    public AiRelayPaymentMethodAdminVO detail(Long id) {
        // TODO: 支付方式详情
        return null;
    }

    @Override
    public Long create(AiRelayPaymentMethodRequest req) {
        // TODO: 创建支付方式，校验 code 唯一
        return null;
    }

    @Override
    public void update(Long id, AiRelayPaymentMethodRequest req) {
        // TODO: 更新支付方式
    }

    @Override
    public void delete(Long id) {
        // TODO: 删除支付方式，需校验是否被服务商绑定
    }

    @Override
    public void updateStatus(Long id, Integer status) {
        // TODO: 切换支付方式状态
    }
}
