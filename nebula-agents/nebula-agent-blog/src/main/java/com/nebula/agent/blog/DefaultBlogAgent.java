package com.nebula.agent.blog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nebula.agent.blog.domain.BlogArticle;
import com.nebula.agent.blog.domain.BlogSeries;
import com.nebula.agent.blog.domain.BlogSeriesOutline;
import com.nebula.agent.blog.domain.BlogSeriesRequest;
import com.nebula.agent.blog.domain.BlogTopic;
import com.nebula.common.ai.agent.AbstractAgent;
import com.nebula.common.ai.api.AiService;
import com.nebula.common.ai.domain.AiRequest;
import com.nebula.common.ai.domain.MemoryQuery;
import com.nebula.common.ai.domain.MemoryRecord;
import com.nebula.common.ai.domain.MemoryType;
import com.nebula.common.ai.memory.AgentMemory;
import com.nebula.common.ai.util.AiTemplateUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 博客Agent默认实现
 * 博客创作分多步推进，当前已落地第一步「生成系列主题」：先从长期记忆召回该用户的历史系列摘要并注入
 * 提示词（避免选题重复、保持脉络连续）→ 组装JSON schema提示词 → 调用AI → 解析为系列选题大纲 →
 * 将本次系列摘要写回长期记忆，形成「越写越懂该用户」的闭环。后续逐篇正文生成（{@link #generateSeries}）
 * 仍为占位骨架。
 *
 * @author nebula
 */
public class DefaultBlogAgent extends AbstractAgent implements BlogAgent {

    /**
     * Agent功能编码
     */
    public static final String AGENT_CODE = "blog";

    /**
     * 召回历史系列的最大条数
     */
    private static final int HISTORY_TOP_K = 5;

    /**
     * 无历史可参考时的占位文案（确保 #{history} 占位符总被替换）
     */
    private static final String NO_HISTORY = "（暂无历史系列，可自由规划。）";

    /**
     * 系列主题（选题大纲）生成提示词模板
     * 约束模型只输出JSON，便于结构化解析；不要求正文，仅规划选题。{@code #{history}} 注入历史系列参考。
     */
    private static final String TOPICS_PROMPT_TEMPLATE = """
            你是一名资深博客主编。请围绕主题「#{topic}」规划一个博客系列，共 #{articleCount} 篇选题。
            目标读者：#{audience}；文风：#{style}；语言：#{language}。
            #{history}
            要求：各篇选题层层递进、互不重复，只做选题规划，不要撰写正文。
            请严格只输出如下结构的JSON，不要包含任何额外说明或Markdown代码块标记：
            {
              "title": "系列标题",
              "summary": "系列简介，概述整个系列的目标与脉络",
              "topics": [
                {
                  "order": 1,
                  "title": "选题标题",
                  "summary": "选题简介，说明该篇将写什么",
                  "category": "分类",
                  "tags": ["标签1", "标签2"]
                }
              ]
            }""";

    /**
     * 系列文章生成提示词模板
     */
    private static final String SERIES_PROMPT_TEMPLATE = """
            你是一名资深博客作者。请围绕主题「#{topic}」创作一个系列博客，共 #{articleCount} 篇。
            目标读者：#{audience}；文风：#{style}；语言：#{language}。
            要求：每篇包含标题、正文、分类与若干标签，且整体围绕主题层层递进。""";

    /**
     * JSON解析器，线程安全可复用
     */
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public DefaultBlogAgent(AgentMemory memory, AiService aiService) {
        super(AGENT_CODE, memory, aiService);
    }

    @Override
    public BlogSeriesOutline generateSeriesTopics(BlogSeriesRequest request) {
        Map<String, Object> variables = buildVariables(request);
        variables.put("history", recallHistory(request));

        String prompt = AiTemplateUtils.render(TOPICS_PROMPT_TEMPLATE, variables);
        AiRequest aiRequest = new AiRequest(prompt)
                .setVariables(variables)
                .setUserId(request.getUserId());

        Map<String, Object> response = chat(aiRequest);
        BlogSeriesOutline outline = parseOutline(extractContent(response), request.getTopic());
        rememberSeries(request, outline);
        return outline;
    }

    @Override
    public BlogSeries generateSeries(BlogSeriesRequest request) {
        Map<String, Object> variables = buildVariables(request);

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
     * 从长期记忆召回该用户的历史系列摘要，拼成提示词可注入的参考文本
     * 记忆门面缺省（未配置记忆）或无历史时返回占位文案，保证提示词渲染与链路不受影响。
     *
     * @param request 系列生成请求
     * @return 历史系列参考文本
     */
    private String recallHistory(BlogSeriesRequest request) {
        AgentMemory memory = memory();
        if (memory == null) {
            return NO_HISTORY;
        }
        MemoryQuery query = new MemoryQuery()
                .setUserId(request.getUserId())
                .setType(MemoryType.SEMANTIC)
                .setTopK(HISTORY_TOP_K);
        List<MemoryRecord> records = memory.recall(query);
        if (records == null || records.isEmpty()) {
            return NO_HISTORY;
        }
        StringBuilder sb = new StringBuilder("参考：你此前已为该用户创作过以下系列，请避免选题与之重复、并保持整体风格与脉络连续：");
        for (MemoryRecord record : records) {
            if (record != null && record.getContent() != null) {
                sb.append("\n- ").append(record.getContent());
            }
        }
        return sb.toString();
    }

    /**
     * 将本次系列摘要写入长期记忆（语义记忆），供后续生成召回参考
     * 记忆门面缺省或大纲为空时跳过，不阻断主流程。
     *
     * @param request 系列生成请求
     * @param outline 生成的系列选题大纲
     */
    private void rememberSeries(BlogSeriesRequest request, BlogSeriesOutline outline) {
        AgentMemory memory = memory();
        if (memory == null || outline == null) {
            return;
        }
        MemoryRecord record = new MemoryRecord()
                .setUserId(request.getUserId())
                .setType(MemoryType.SEMANTIC)
                .setContent(buildSeriesSummary(request, outline));
        memory.remember(record);
    }

    /**
     * 构造系列摘要文本：系列标题、主题与各篇选题标题
     *
     * @param request 系列生成请求
     * @param outline 系列选题大纲
     * @return 摘要文本
     */
    private String buildSeriesSummary(BlogSeriesRequest request, BlogSeriesOutline outline) {
        String title = outline.getTitle() == null ? request.getTopic() : outline.getTitle();
        StringBuilder sb = new StringBuilder("《").append(title).append("》（主题：").append(request.getTopic()).append("）");
        List<BlogTopic> topics = outline.getTopics();
        if (topics != null && !topics.isEmpty()) {
            List<String> titles = new ArrayList<>();
            for (BlogTopic topic : topics) {
                if (topic != null && topic.getTitle() != null) {
                    titles.add(topic.getTitle());
                }
            }
            if (!titles.isEmpty()) {
                sb.append("：").append(String.join("、", titles));
            }
        }
        return sb.toString();
    }

    /**
     * 组装提示词模板参数
     *
     * @param request 系列生成请求
     * @return 模板参数
     */
    private Map<String, Object> buildVariables(BlogSeriesRequest request) {
        Map<String, Object> variables = new HashMap<>();
        if (request.getVariables() != null) {
            variables.putAll(request.getVariables());
        }
        variables.put("topic", request.getTopic());
        variables.put("articleCount", request.getArticleCount());
        variables.put("audience", request.getAudience());
        variables.put("style", request.getStyle());
        variables.put("language", request.getLanguage());
        return variables;
    }

    /**
     * 将模型返回的JSON解析为系列选题大纲
     * 容错处理：剥离可能存在的Markdown代码块包裹，再定位最外层JSON对象。
     * 解析失败时回退为仅含主题标题的空大纲，避免链路因模型输出异常而中断。
     *
     * @param content       模型返回文本
     * @param fallbackTitle 解析失败时回退使用的系列标题
     * @return 系列选题大纲
     */
    private BlogSeriesOutline parseOutline(String content, String fallbackTitle) {
        String json = extractJson(content);
        if (json == null) {
            return new BlogSeriesOutline().setTitle(fallbackTitle);
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(json);
            BlogSeriesOutline outline = new BlogSeriesOutline()
                    .setTitle(text(root, "title", fallbackTitle))
                    .setSummary(text(root, "summary", null));

            List<BlogTopic> topics = new ArrayList<>();
            JsonNode topicsNode = root.get("topics");
            if (topicsNode != null && topicsNode.isArray()) {
                int index = 1;
                for (JsonNode node : topicsNode) {
                    topics.add(parseTopic(node, index));
                    index++;
                }
            }
            return outline.setTopics(topics);
        } catch (Exception e) {
            return new BlogSeriesOutline().setTitle(fallbackTitle);
        }
    }

    /**
     * 解析单个选题节点
     *
     * @param node         选题JSON节点
     * @param defaultOrder 节点未提供序号时使用的默认序号
     * @return 选题
     */
    private BlogTopic parseTopic(JsonNode node, int defaultOrder) {
        JsonNode orderNode = node.get("order");
        int order = orderNode != null && orderNode.isInt() ? orderNode.asInt() : defaultOrder;

        List<String> tags = new ArrayList<>();
        JsonNode tagsNode = node.get("tags");
        if (tagsNode != null && tagsNode.isArray()) {
            for (JsonNode tag : tagsNode) {
                tags.add(tag.asText());
            }
        }
        return new BlogTopic()
                .setOrder(order)
                .setTitle(text(node, "title", null))
                .setSummary(text(node, "summary", null))
                .setCategory(text(node, "category", null))
                .setTags(tags);
    }

    /**
     * 读取JSON文本字段
     *
     * @param node         JSON节点
     * @param field        字段名
     * @param defaultValue 字段缺失或为null时的默认值
     * @return 字段文本值
     */
    private String text(JsonNode node, String field, String defaultValue) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? defaultValue : value.asText();
    }

    /**
     * 从模型文本中抽取JSON对象字符串
     * 兼容模型可能附带的说明文字或```json```代码块：定位首个'{'与末个'}'之间的内容。
     *
     * @param content 模型返回文本
     * @return JSON字符串；无法定位时返回null
     */
    private String extractJson(String content) {
        if (content == null || content.isBlank()) {
            return null;
        }
        int start = content.indexOf('{');
        int end = content.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        return content.substring(start, end + 1);
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
