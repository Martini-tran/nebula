package com.nebula.scribe.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改作品请求
 *
 * <p>整表单覆盖语义：未传或传空的可选字段会被清空，与作品设置页「一次提交整份表单」一致。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkUpdateRequest extends WorkCreateRequest {

    /**
     * 状态：draft/serializing/paused/finished；为空表示不改状态
     */
    private String status;
}
