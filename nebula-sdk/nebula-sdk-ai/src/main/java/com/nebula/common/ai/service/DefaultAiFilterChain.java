package com.nebula.common.ai.service;

import com.nebula.common.ai.api.AiFilter;
import com.nebula.common.ai.api.AiFilterChain;
import com.nebula.common.ai.domain.AiRequest;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 默认AI过滤器链
 * 依次执行已排序的 {@link AiFilter}，全部执行完毕后调用终端逻辑（真正的厂商调用）。
 * 每次调用应新建实例，因为内部以游标推进过滤器，状态不可复用。
 *
 * @author nebula
 */
public class DefaultAiFilterChain implements AiFilterChain {

    private final List<AiFilter> filters;

    private final Function<AiRequest, Map<String, Object>> terminal;

    private int index = 0;

    public DefaultAiFilterChain(List<AiFilter> filters, Function<AiRequest, Map<String, Object>> terminal) {
        this.filters = filters == null ? List.of() : filters;
        this.terminal = terminal;
    }

    @Override
    public Map<String, Object> doFilter(AiRequest request) {
        if (index < filters.size()) {
            AiFilter filter = filters.get(index++);
            return filter.filter(request, this);
        }
        return terminal.apply(request);
    }
}
