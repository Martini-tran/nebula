package com.nebula.common.ai.harness.runtime;

import java.util.ArrayList;
import java.util.List;

/**
 * 测试用事件收集器：按顺序记录 Harness 事件，供断言事件序列。
 *
 * @author nebula
 */
public final class RecordingSink implements HarnessEventSink {

    private final List<HarnessEvent> events = new ArrayList<>();

    @Override
    public void publish(HarnessEvent event) {
        events.add(event);
    }

    /** 事件类型序列。 */
    public List<String> types() {
        return events.stream().map(HarnessEvent::type).toList();
    }

    /** 原始事件序列。 */
    public List<HarnessEvent> events() {
        return List.copyOf(events);
    }
}
