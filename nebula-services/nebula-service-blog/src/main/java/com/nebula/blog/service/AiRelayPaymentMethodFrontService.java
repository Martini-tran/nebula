package com.nebula.blog.service;

import com.nebula.blog.vo.front.AiRelayPaymentMethodFrontVO;

import java.util.List;

/**
 * AI 中转支付方式字典前台服务
 */
public interface AiRelayPaymentMethodFrontService {

    /**
     * 列出全部上线状态的支付方式
     */
    List<AiRelayPaymentMethodFrontVO> listPaymentMethods();
}
