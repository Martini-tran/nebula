package com.nebula.common.ai.harness.conversation;

/**
 * Harness 对话消息。
 *
 * @param role    消息角色，如 user / assistant / system
 * @param content 消息正文
 * @author nebula
 */
public record HarnessMessage(String role, String content) {
}
