package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * AI 工具行（管理员端，只读）
 * 工具的实现定义在代码中，本 VO 仅暴露其自描述元数据。{@code paramsSchema/resultSchema} 反序列化为对象返回，
 * 供前端流程编辑器渲染参数表单。本表不含任何密钥，无掩码字段。
 *
 * @author nebula
 */
@Data
public class ToolVO {

    /**
     * 工具ID
     */
    private Long id;

    /**
     * 工具编码（被流程节点引用）
     */
    private String toolCode;

    /**
     * 工具显示名
     */
    private String name;

    /**
     * 用途描述
     */
    private String description;

    /**
     * 分类（http/data/search 等）
     */
    private String category;

    /**
     * 入参 JSON Schema
     */
    private Map<String, Object> paramsSchema;

    /**
     * 出参结构描述
     */
    private Map<String, Object> resultSchema;

    /**
     * 是否启用：0=已下线 1=启用
     */
    private Integer enabled;

    /**
     * 是否内置工具：1=代码内置 0=外部登记
     */
    private Integer builtin;

    /**
     * 排序号
     */
    private Integer sortNo;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
