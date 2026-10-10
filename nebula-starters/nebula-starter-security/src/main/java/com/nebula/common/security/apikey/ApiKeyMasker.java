package com.nebula.common.security.apikey;

/**
 * API Key 掩码：只露首尾，用于日志和列表展示，避免完整 key 外泄
 *
 * @author nebula
 */
public final class ApiKeyMasker {

    private static final int HEAD = 7;
    private static final int TAIL = 4;

    private ApiKeyMasker() {
    }

    /**
     * 形如 {@code AK-abcd****wxyz}；过短的 key 不足以安全地露出首尾，整体打码
     */
    public static String mask(String apiKey) {
        if (apiKey == null || apiKey.isEmpty()) {
            return null;
        }
        if (apiKey.length() <= HEAD + TAIL) {
            return "****";
        }
        return apiKey.substring(0, HEAD) + "****" + apiKey.substring(apiKey.length() - TAIL);
    }
}
