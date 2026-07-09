package com.nebula.common.ai.service;

import com.nebula.common.ai.api.AiAfterCallEvent;
import com.nebula.common.ai.api.AiBeforeCallEvent;
import com.nebula.common.ai.api.AiCallLogger;
import com.nebula.common.ai.api.AiCallback;
import com.nebula.common.ai.api.AiConversationRepository;
import com.nebula.common.ai.api.AiFilter;
import com.nebula.common.ai.api.AiFilterChain;
import com.nebula.common.ai.api.AiProvider;
import com.nebula.common.ai.api.AiProviderResolver;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.exception.AiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 默认AI服务实现
 * 作为Agent调用的统一入口，串联「过滤器链 → Provider解析 → 厂商调用」，并在调用前后发布
 * {@link AiBeforeCallEvent}/{@link AiAfterCallEvent} 事件、记录调用日志、按需落库会话消息。
 *
 * <p>会话仓储与日志记录器均为可选依赖，缺省时对应能力降级为空操作。
 *
 * @author nebula
 */
@Slf4j
public class DefaultAiService implements AiService {

    private final AiProviderResolver resolver;

    private final List<AiFilter> filters;

    private final AiConversationRepository conversationRepository;

    private final AiCallLogger callLogger;

    private final ApplicationEventPublisher eventPublisher;

    private final String defaultProvider;

    public DefaultAiService(AiProviderResolver resolver,
                            List<AiFilter> filters,
                            AiConversationRepository conversationRepository,
                            AiCallLogger callLogger,
                            ApplicationEventPublisher eventPublisher,
                            String defaultProvider) {
        this.resolver = resolver;
        this.filters = filters == null ? List.of() : filters;
        this.conversationRepository = conversationRepository;
        this.callLogger = callLogger;
        this.eventPublisher = eventPublisher;
        this.defaultProvider = defaultProvider;
    }

    @Override
    public Map<String, Object> chat(AiRequest request) {
        if (request == null) {
            throw new AiException("AI请求不能为空");
        }
        applyDefaults(request);
        long start = System.currentTimeMillis();
        publishBefore(request);

        Map<String, Object> response = null;
        Throwable error = null;
        try {
            AiFilterChain chain = new DefaultAiFilterChain(filters, this::invokeProvider);
            response = chain.doFilter(request);
            persistConversation(request, response);
            return response;
        } catch (RuntimeException e) {
            error = e;
            throw e;
        } finally {
            long latencyMs = System.currentTimeMillis() - start;
            publishAfter(request, response, error, latencyMs);
            writeLog(request, response, error, latencyMs);
        }
    }

    @Override
    public CompletableFuture<Map<String, Object>> chatAsync(AiRequest request) {
        return CompletableFuture.supplyAsync(() -> chat(request));
    }

    @Override
    public void stream(AiRequest request, AiCallback callback) {
        if (request == null) {
            throw new AiException("AI请求不能为空");
        }
        applyDefaults(request);
        resolver.resolve(request.getProvider()).stream(request, callback);
    }

    @Override
    public void saveMessage(String conversationId, Map<String, Object> message) {
        if (conversationRepository != null) {
            conversationRepository.saveMessage(conversationId, message);
        }
    }

    @Override
    public List<Map<String, Object>> listMessages(String conversationId) {
        return conversationRepository == null ? List.of() : conversationRepository.listMessages(conversationId);
    }

    @Override
    public void log(Map<String, Object> record) {
        if (callLogger != null) {
            callLogger.log(record);
        }
    }

    @Override
    public void clearConversation(String conversationId) {
        if (conversationRepository != null) {
            conversationRepository.clear(conversationId);
        }
    }

    /**
     * 终端调用：解析Provider并发起厂商对话
     *
     * @param request 请求参数
     * @return 响应结果
     */
    private Map<String, Object> invokeProvider(AiRequest request) {
        AiProvider provider = resolver.resolve(request.getProvider());
        return provider.chat(request);
    }

    /**
     * 补齐请求默认值：未指定服务提供商时使用默认Provider
     *
     * @param request 请求参数
     */
    private void applyDefaults(AiRequest request) {
        if (request.getProvider() == null || request.getProvider().isBlank()) {
            request.setProvider(defaultProvider);
        }
    }

    private void publishBefore(AiRequest request) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new AiBeforeCallEvent(this, request));
        }
    }

    private void publishAfter(AiRequest request, Map<String, Object> response, Throwable error, long latencyMs) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(new AiAfterCallEvent(this, request, response, error, latencyMs));
        }
    }

    /**
     * 记录调用日志，日志记录失败不影响主流程
     */
    private void writeLog(AiRequest request, Map<String, Object> response, Throwable error, long latencyMs) {
        if (callLogger == null) {
            return;
        }
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("agentCode", request.getAgentCode());
        record.put("provider", request.getProvider());
        record.put("model", request.getModel());
        record.put("conversationId", request.getConversationId());
        record.put("latencyMs", latencyMs);
        record.put("success", error == null);
        if (error != null) {
            record.put("error", error.getMessage());
        }
        try {
            if (error != null) {
                callLogger.logError(record, error);
            } else {
                callLogger.log(record);
            }
        } catch (Exception e) {
            log.warn("记录AI调用日志失败: {}", e.getMessage());
        }
    }

    /**
     * 落库会话消息：仅在配置了会话仓储且请求带会话ID时执行，记录用户提示与助手响应。
     */
    private void persistConversation(AiRequest request, Map<String, Object> response) {
        if (conversationRepository == null || request.getConversationId() == null || request.getConversationId().isBlank()) {
            return;
        }
        try {
            if (request.getPrompt() != null && !request.getPrompt().isBlank()) {
                Map<String, Object> userMessage = new LinkedHashMap<>();
                userMessage.put("role", "user");
                userMessage.put("content", request.getPrompt());
                conversationRepository.saveMessage(request.getConversationId(), userMessage);
            }
            if (response != null) {
                Map<String, Object> assistantMessage = new LinkedHashMap<>();
                assistantMessage.put("role", "assistant");
                assistantMessage.put("content", response.getOrDefault("content", ""));
                conversationRepository.saveMessage(request.getConversationId(), assistantMessage);
            }
        } catch (Exception e) {
            log.warn("落库AI会话消息失败: {}", e.getMessage());
        }
    }
}
