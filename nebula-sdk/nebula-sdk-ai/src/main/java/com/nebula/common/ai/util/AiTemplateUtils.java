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

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("#\\{\\s*([\\w.-]+)\\s*}");

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
            String key = matcher.group(1);
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
