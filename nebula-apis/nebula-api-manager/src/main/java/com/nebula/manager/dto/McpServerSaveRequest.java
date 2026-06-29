package com.nebula.manager.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AI MCP 服务器保存请求（创建/更新共用）
 * authToken 以明文传入，由服务端加密落库；更新时该字段留空表示「不修改原凭证」。
 * 传输形态由 transport 决定取用哪组字段：stdio→command/args/env；sse/streamable-http→url/headers/authToken。
 *
 * @author nebula
 */
@Data
public class McpServerSaveRequest {

    /**
     * 服务器编码，全局唯一（创建必填；更新时不可变更）
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
     * 【stdio】命令参数数组
     */
    private List<String> args;

    /**
     * 【stdio】子进程环境变量（明文）
     */
    private Map<String, String> env;

    /**
     * 【远程】SSE/HTTP 端点地址
     */
    private String url;

    /**
     * 【远程】自定义请求头（明文非敏感部分）
     */
    private Map<String, String> headers;

    /**
     * 【远程】鉴权凭证 Bearer/apiKey（明文；更新时留空表示不修改）
     */
    private String authToken;

    /**
     * 连接/请求超时（毫秒）
     */
    private Integer timeoutMs;

    /**
     * 扩展参数
     */
    private Map<String, Object> options;

    /**
     * 状态：0=停用 1=启用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
