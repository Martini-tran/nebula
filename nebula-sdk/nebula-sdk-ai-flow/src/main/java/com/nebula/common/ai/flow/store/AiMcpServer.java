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
 * AI MCP 服务器配置表
 * 集中维护一组 MCP（Model Context Protocol）服务器接入点，由 {@code server_code} 被流程/节点引用复用。
 * 传输形态由 {@code transport} 区分：{@code stdio}（command/args/env）/ {@code sse} / {@code streamable-http}（url/headers/authToken）。
 * 其中 {@code authToken} 以密文形式落库（加解密由管理服务负责，实体本身只承载原始列值），
 * {@code args/env/headers/options} 为 JSON 字符串（序列化在服务层处理）。
 *
 * @author nebula
 */
@Data
@TableName("ai_mcp_server")
public class AiMcpServer implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 服务器ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 服务器编码，全局唯一，被流程/节点引用
     */
    private String serverCode;

    /**
     * 服务器名称
     */
    private String name;

    /**
     * 传输类型：stdio/sse/streamable-http
     */
    private String transport;

    /**
     * 【stdio】启动命令（npx/uvx/python 等）
     */
    private String command;

    /**
     * 【stdio】命令参数数组（JSON 数组字符串；序列化在服务层处理）
     */
    private String args;

    /**
     * 【stdio】子进程环境变量（JSON 对象字符串，明文；序列化在服务层处理）
     */
    private String env;

    /**
     * 【远程】SSE/HTTP 端点地址
     */
    private String url;

    /**
     * 【远程】自定义请求头（JSON 对象字符串，明文；序列化在服务层处理）
     */
    private String headers;

    /**
     * 【远程】鉴权凭证 Bearer/apiKey（AES加密密文存储）
     */
    private String authToken;

    /**
     * 连接/请求超时（毫秒）
     */
    private Integer timeoutMs;

    /**
     * 扩展参数（JSON 对象字符串；序列化在服务层处理）
     */
    private String options;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

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
