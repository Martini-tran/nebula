package com.nebula.blog.vo.front;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 中转通用筛选项 VO（前台）。
 *
 * <p>用于厂商、计费模式等下拉/筛选场景，返回 value + label 的极简结构。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AiRelayOptionVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 选项值，用于查询参数（如 claude / gpt / usage / subscription）
     */
    private String value;

    /**
     * 展示标签
     */
    private String label;
}
