package com.nebula.blog.service.impl;

import com.nebula.agent.blog.domain.BlogSeriesRequest;
import com.nebula.blog.config.BlogAgentConfiguration;
import com.nebula.blog.dto.admin.SeriesGenerateRequest;
import com.nebula.blog.service.BlogSeriesGenerationAdminService;
import com.nebula.common.ai.orchestration.OrchestrationAgent;
import com.nebula.common.ai.orchestration.OrchestrationContext;
import com.nebula.common.core.constant.HttpStatus;
import com.nebula.common.core.context.UserContext;
import com.nebula.common.core.exception.BizException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 博客系列主题生成服务实现（管理员端）
 * 将「调用AI生成大纲 → 落库」交由系列生成编排器（{@link OrchestrationAgent}）驱动：本服务只负责
 * 组装编排上下文（请求参数、当前用户）并取回结果系列ID。编排流程的节点编排、503软依赖与落库事务
 * 均在编排器（{@link BlogAgentConfiguration#blogSeriesOrchestrator}）侧定义；编排器执行完成后会自动
 * 落一条情景记忆，为后续「参考历史系列」铺好数据。
 *
 * @author nebula
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogSeriesGenerationAdminServiceImpl implements BlogSeriesGenerationAdminService {

    private final OrchestrationAgent blogSeriesOrchestrator;

    @Override
    public Long generate(SeriesGenerateRequest req) {
        if (req == null || !StringUtils.hasText(req.getTopic())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "主题不能为空");
        }

        OrchestrationContext ctx = new OrchestrationContext();
        Long userId = UserContext.getUserId();
        String userIdStr = userId == null ? null : String.valueOf(userId);
        ctx.setUserId(userIdStr);
        ctx.put(BlogAgentConfiguration.CTX_REQUEST, toAgentRequest(req, userIdStr));
        ctx.put(BlogAgentConfiguration.CTX_TOPIC, req.getTopic());

        OrchestrationContext result = blogSeriesOrchestrator.run(ctx);
        return result.get(BlogAgentConfiguration.CTX_SERIES_ID, Long.class);
    }

    /**
     * 将服务入参转换为Agent生成请求
     *
     * @param req 系列主题生成请求
     * @return Agent系列生成请求
     */
    private BlogSeriesRequest toAgentRequest(SeriesGenerateRequest req, String userId) {
        return new BlogSeriesRequest()
                .setUserId(userId)
                .setTopic(req.getTopic())
                .setArticleCount(req.getArticleCount() == null ? 0 : req.getArticleCount())
                .setAudience(req.getAudience())
                .setStyle(req.getStyle())
                .setLanguage(req.getLanguage());
    }
}
