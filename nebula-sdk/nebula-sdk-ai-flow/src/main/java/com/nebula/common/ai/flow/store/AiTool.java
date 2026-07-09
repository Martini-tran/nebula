package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * AI 工具定义表
 * 流程节点（nodeType=TOOL）可调用工具的「只读目录镜像」。工具的实现（invoke 逻辑）定义在代码中，
 * 本表只承载其自描述元数据（编码/名称/描述/入参schema），供前端流程编辑器渲染「可选工具列表 + 参数表单」，
 * 由节点的 {@code nodeConfig.toolCode} 引用。
 * <p>
 * 数据来源：代码 = 唯一真相源，应用启动时由同步器按 {@code tool_code} upsert 进本表，代码中已移除的工具置
 * {@code enabled=0}（软下线）。故本表对管理端只读，无人工增删改。
 * <p>
 * {@code paramsSchema/resultSchema} 为 JSON 字符串（序列化在服务层处理）。本表不含任何密钥/凭证。
 *
 * @author nebula
 */
@Data
@TableName("ai_tool")
public class AiTool implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 工具ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 工具编码，全局唯一，= 代码 ToolDefinition.code()，被流程节点引用
     */
    private String toolCode;

    /**
     * 工具显示名
     */
    private String name;

    /**
     * 用途描述（兼作 LLM function description）
     */
    private String description;

    /**
     * 分类（http/data/search 等），编辑器分组用
     */
    private String category;

    /**
     * 入参 JSON Schema（JSON 字符串；序列化在服务层处理）
     */
    private String paramsSchema;

    /**
     * 出参结构描述（JSON 字符串，可空；序列化在服务层处理）
     */
    private String resultSchema;

    /**
     * 是否启用：0=已下线(代码中已移除) 1=启用
     */
    private Integer enabled;

    /**
     * 是否内置工具：1=代码内置(由同步器维护) 0=外部登记(预留扩展位)
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
