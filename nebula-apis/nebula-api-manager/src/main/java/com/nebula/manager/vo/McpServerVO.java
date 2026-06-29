package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * AI MCP 服务器行（管理员端）
 * 出于安全考虑不回传明文 authToken，仅以 {@code authTokenMasked} 给出掩码、以 {@code hasAuthToken} 标记是否已配置。
 *
 * @author nebula
 */
@Data
public class McpServerVO {

    /**
     * 服务器ID
     */
    private Long id;

    /**
     * 服务器编码
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
     * 【stdio】启动命令
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
     * 【远程】自定义请求头（明文）
     */
    private Map<String, String> headers;

    /**
     * 鉴权凭证掩码（如 ****1234），不含明文
     */
    private String authTokenMasked;

    /**
     * 是否已配置鉴权凭证
     */
    private Boolean hasAuthToken;

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

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
