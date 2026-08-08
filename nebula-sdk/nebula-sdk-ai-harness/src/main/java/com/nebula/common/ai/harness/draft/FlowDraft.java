package com.nebula.common.ai.harness.draft;

import com.nebula.common.ai.flow.FlowDefinition;
import lombok.Data;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 流程生成草稿领域对象。
 *
 * <p>为保持与现有 Flow 模型一致，本类型是 Lombok 可变 Bean。可变实例仅允许存在于一次 mutation 内部；
 * Store 读取、校验、事件和只读投影均须使用独立深拷贝。
 *
 * @author nebula
 */
@Data
@Accessors(chain = true)
public class FlowDraft {

    private String draftId;

    private String sessionId;

    private Long userId;

    private String flowCode;

    private String name;

    private String description;

    private String engineType;

    private long revision;

    private Long lastValidatedRevision;

    private Long lastSimulatedRevision;

    private DraftStatus status = DraftStatus.BUILDING;

    private String committedFlowCode;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private FlowDefinition graph;
}
