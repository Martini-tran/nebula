package com.nebula.manager.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * AI 智能体唤醒（signal）请求
 * 唤醒一个 SUSPENDED 实例（Human-in-the-loop）：event 需命中实例的 awaiting_events，payload 写入 context 供出边 guard 裁决。
 *
 * @author nebula
 */
@Data
public class AgentSignalRequest {

    /**
     * 外部事件名（如 approve/reject/request-changes）
     */
    private String event;

    /**
     * 事件负载（写入 context 供 guard 求值，可空）
     */
    private Map<String, Object> payload = new LinkedHashMap<>();
}
