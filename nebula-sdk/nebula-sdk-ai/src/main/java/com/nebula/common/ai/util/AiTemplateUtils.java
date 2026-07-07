package com.nebula.common.ai.util;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI模板工具
 *
 * @author nebula
 */
public final class AiTemplateUtils {

    /**
     * 占位符两种写法并存：
     * - {@code {{var}}}：前端画布各节点模板统一写法（LLM 提示词 / END / JOIN 输出模板）
     * - {@code #{var}}：历史写法，保留兼容
     * 命中任一即用变量池替换；变量缺失时保留原占位符原文。
     */
    private static final Pattern PLACEHOLDER_PATTERN =
            Pattern.compile("\\{\\{\\s*([\\w.-]+)\\s*}}|#\\{\\s*([\\w.-]+)\\s*}");

    private AiTemplateUtils() {
    }

    /**
     * 替换模板占位符
     *
     * @param template  模板内容
     * @param variables 模板参数
     * @return 替换后的内容
     */
    public static String render(String template, Map<String, Object> variables) {
        if (template == null || template.isEmpty() || variables == null || variables.isEmpty()) {
            return template;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            // group(1)={{var}} 命中，group(2)=#{var} 命中；取非空者为变量名
            String key = matcher.group(1) != null ? matcher.group(1) : matcher.group(2);
            Object value = variables.get(key);
            if (value == null) {
                matcher.appendReplacement(result, Matcher.quoteReplacement(matcher.group()));
                continue;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(String.valueOf(value)));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
