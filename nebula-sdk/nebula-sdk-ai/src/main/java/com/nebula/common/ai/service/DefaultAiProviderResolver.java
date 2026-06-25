package com.nebula.common.ai.service;

import com.nebula.common.ai.api.AiProvider;
import com.nebula.common.ai.api.AiProviderResolver;
import com.nebula.common.ai.exception.AiException;

import java.util.List;

/**
 * 默认AI服务提供商解析器
 * 按标识匹配Provider；标识为空时回退默认Provider，仍无匹配则回退到首个可用Provider。
 *
 * @author nebula
 */
public class DefaultAiProviderResolver implements AiProviderResolver {

    private final List<AiProvider> providers;

    private final String defaultProvider;

    public DefaultAiProviderResolver(List<AiProvider> providers, String defaultProvider) {
        this.providers = providers == null ? List.of() : providers;
        this.defaultProvider = defaultProvider;
    }

    @Override
    public AiProvider resolve(String provider) {
        String target = provider == null || provider.isBlank() ? defaultProvider : provider;

        AiProvider matched = findBySupports(target);
        if (matched != null) {
            return matched;
        }
        if (target != null && !target.equals(defaultProvider)) {
            matched = findBySupports(defaultProvider);
            if (matched != null) {
                return matched;
            }
        }
        if (!providers.isEmpty()) {
            return providers.get(0);
        }
        throw new AiException("未找到可用的AI服务提供商: " + provider);
    }

    @Override
    public List<AiProvider> listProviders() {
        return providers;
    }

    private AiProvider findBySupports(String provider) {
        if (provider == null) {
            return null;
        }
        for (AiProvider candidate : providers) {
            if (candidate.supports(provider)) {
                return candidate;
            }
        }
        return null;
    }
}
