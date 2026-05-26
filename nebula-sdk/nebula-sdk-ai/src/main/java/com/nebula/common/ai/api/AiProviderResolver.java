package com.nebula.common.ai.api;

import java.util.List;

/**
 * AI服务提供商解析器接口
 *
 * @author nebula
 */
public interface AiProviderResolver {

    /**
     * 根据服务提供商标识解析调用实现
     *
     * @param provider 服务提供商标识
     * @return AI服务提供商
     */
    AiProvider resolve(String provider);

    /**
     * 获取所有AI服务提供商
     *
     * @return AI服务提供商列表
     */
    List<AiProvider> listProviders();
}
