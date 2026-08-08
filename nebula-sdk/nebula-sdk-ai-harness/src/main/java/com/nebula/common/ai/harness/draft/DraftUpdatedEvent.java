package com.nebula.common.ai.harness.draft;

import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;

/**
 * 草稿成功变更事件。事件仅携带只读摘要，不共享可变 FlowDraft。
 *
 * @param draftId 草稿 ID
 * @param revision 新 revision
 * @param summary 图摘要
 * @author nebula
 */
public record DraftUpdatedEvent(String draftId, long revision, Map<String, Object> summary) {

    public DraftUpdatedEvent {
        summary = summary == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(summary));
    }
}
