package com.nebula.agent.blog;

import com.nebula.agent.blog.domain.BlogArticle;
import com.nebula.agent.blog.domain.BlogSeries;
import com.nebula.agent.blog.domain.BlogSeriesRequest;
import com.nebula.common.ai.agent.AbstractAgent;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.memory.AgentMemory;
import com.nebula.common.ai.util.AiTemplateUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 博客Agent默认实现
 * 当前先搭建可编译可测的调用骨架：组装提示词→调用AI→封装结果。
 * 结构化解析（把模型输出拆分为多篇文章及其分类、标签）留作后续，
 * 计划采用「JSON schema 提示词 + 解析」补全。
 *
 * @author nebula
 */
public class DefaultBlogAgent extends AbstractAgent implements BlogAgent {

    /**
     * Agent功能编码
     */
    public static final String AGENT_CODE = "blog";

    /**
     * 系列文章生成提示词模板
     */
    private static final String SERIES_PROMPT_TEMPLATE = """
            你是一名资深博客作者。请围绕主题「#{topic}」创作一个系列博客，共 #{articleCount} 篇。
            目标读者：#{audience}；文风：#{style}；语言：#{language}。
            要求：每篇包含标题、正文、分类与若干标签，且整体围绕主题层层递进。""";

    public DefaultBlogAgent(AgentMemory memory, AiService aiService) {
        super(AGENT_CODE, memory, aiService);
    }

    @Override
    public BlogSeries generateSeries(BlogSeriesRequest request) {
        Map<String, Object> variables = new HashMap<>();
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        variables.put("topic", request.getTopic());
        variables.put("articleCount", request.getArticleCount());
        variables.put("audience", request.getAudience());
        variables.put("style", request.getStyle());
        variables.put("language", request.getLanguage());

        String prompt = AiTemplateUtils.render(SERIES_PROMPT_TEMPLATE, variables);
        AiRequest aiRequest = new AiRequest(prompt).setVariables(variables);

        Map<String, Object> response = chat(aiRequest);

        // TODO 结构化解析：将模型输出按文章拆分并提取分类、标签。
        //  当前先把整段响应作为一篇占位文章封装，保证链路可编译可测。
        BlogArticle article = new BlogArticle()
                .setTitle(request.getTopic())
                .setContent(extractContent(response));
        return new BlogSeries()
                .setTitle(request.getTopic())
                .setArticles(List.of(article));
    }

    /**
     * 从AI响应中提取文本内容
     *
     * @param response AI响应
     * @return 文本内容
     */
    private String extractContent(Map<String, Object> response) {
        if (response == null) {
            return "";
        }
        Object content = response.get("content");
        return content == null ? "" : String.valueOf(content);
    }
}
