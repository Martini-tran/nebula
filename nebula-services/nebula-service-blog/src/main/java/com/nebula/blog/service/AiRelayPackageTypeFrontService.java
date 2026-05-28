package com.nebula.blog.service;

import com.nebula.blog.vo.front.AiRelayOptionVO;
import com.nebula.blog.vo.front.AiRelayPackageTypeFrontVO;

import java.util.List;

/**
 * AI 中转套餐类型字典前台服务
 */
public interface AiRelayPackageTypeFrontService {

    /**
     * 列出全部上线状态的套餐类型，用于前端筛选项
     */
    List<AiRelayPackageTypeFrontVO> listPackageTypes();

    /**
     * 列出全部已上线套餐类型对应的计费模式筛选项（usage/subscription，去重）
     */
    List<AiRelayOptionVO> listBillingModeOptions();
}
