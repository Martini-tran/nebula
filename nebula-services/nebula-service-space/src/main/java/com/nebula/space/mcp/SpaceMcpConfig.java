package com.nebula.space.mcp;

import io.modelcontextprotocol.json.jackson3.JacksonMcpJsonMapper;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpStatelessSyncServer;
import io.modelcontextprotocol.spec.McpSchema;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

/**
 * 个人空间 MCP 服务端装配
 */
@Configuration
public class SpaceMcpConfig {

    static final String SERVER_NAME = "nebula-space";
    static final String SERVER_VERSION = "1.0.0";

    /**
     * 协议报文用 Jackson 默认配置，不用接口返回那个：后者把 Long 写成字符串、改了时间格式，是给页面用的
     */
    @Bean
    public SpaceMcpTransport spaceMcpTransport() {
        return new SpaceMcpTransport(new JacksonMcpJsonMapper(JsonMapper.shared()));
    }

    @Bean(destroyMethod = "close")
    public McpStatelessSyncServer spaceMcpServer(SpaceMcpTransport transport, SpaceMcpTools tools) {
        return McpServer.sync(transport)
                .serverInfo(SERVER_NAME, SERVER_VERSION)
                .instructions(SpaceMcpTools.INSTRUCTIONS)
                .capabilities(McpSchema.ServerCapabilities.builder().tools(false).build())
                // 工具必须在请求线程上执行：UserContext、Sa-Token 的请求上下文都是线程绑定的
                .immediateExecution(true)
                .tools(tools.specifications())
                .build();
    }
}
