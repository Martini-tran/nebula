package com.nebula.common.ai.util;

import java.util.List;
import java.util.Map;

/**
 * Token估算工具
 * 基于字符的启发式估算，仅用于会话窗口裁剪以避免超出模型上下文上限，
 * 不追求与厂商分词器精确一致，倾向于给出"够用的上界估计"。
 *
 * @author nebula
 */
public final class TokenEstimator {

    /**
     * 每条消息的固定开销（role包裹、分隔符等），单位token
     */
    private static final int MESSAGE_OVERHEAD = 4;

    /**
     * 非CJK字符（ASCII/符号）折算token的字符数：约4字符/token
     */
    private static final int CHARS_PER_TOKEN = 4;

    private TokenEstimator() {
    }

    /**
     * 估算消息列表的token数
     *
     * @param messages 消息列表
     * @return 估算token数
     */
    public static int estimateMessages(List<Map<String, Object>> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (Map<String, Object> message : messages) {
            total += estimateMessage(message);
        }
        return total;
    }

    /**
     * 估算单条消息的token数
     *
     * @param message 消息
     * @return 估算token数
     */
    public static int estimateMessage(Map<String, Object> message) {
        if (message == null || message.isEmpty()) {
            return 0;
        }
        Object content = message.get("content");
        String text = content == null ? null : String.valueOf(content);
        return MESSAGE_OVERHEAD + estimateText(text);
    }

    /**
     * 估算文本的token数
     * CJK字符按约1token/字计，其余字符按约1token/4字符计。
     *
     * @param text 文本
     * @return 估算token数
     */
    public static int estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int cjkCount = 0;
        int otherCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (isCjk(text.charAt(i))) {
                cjkCount++;
            } else {
                otherCount++;
            }
        }
        // 其余字符向上取整折算，保证非空文本至少计1个token
        int otherTokens = (otherCount + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
        return cjkCount + otherTokens;
    }

    /**
     * 判断字符是否为CJK（中日韩）表意文字
     *
     * @param ch 字符
     * @return 是否为CJK字符
     */
    private static boolean isCjk(char ch) {
        return (ch >= '一' && ch <= '鿿')   // CJK统一表意文字
                || (ch >= '㐀' && ch <= '䶿') // CJK扩展A
                || (ch >= '぀' && ch <= 'ヿ') // 日文平假名/片假名
                || (ch >= '가' && ch <= '힣'); // 韩文音节
    }
}
