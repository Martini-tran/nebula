package com.nebula.space.mcp;

/**
 * 个人空间 MCP 工具的 API Key 授权范围
 *
 * <p>取值就是权限码（sys_menu 里的按钮行，见 script/mysql/space_mcp.sql），签发 key 时才选得到。
 * 只读和写分开，写里再把日报单独拆出来：只想让 AI 写日报的 key 不必能改任务和笔记。</p>
 */
public final class SpaceMcpScopes {

    /**
     * 读：任务、清单、会议、笔记、专注、日报周报、搜索
     */
    public static final String READ = "space:me:read";

    /**
     * 写日报 / 周报
     */
    public static final String REPORT_WRITE = "space:report:write";

    /**
     * 写任务、笔记：新建、修改、完成，不含删除
     */
    public static final String WRITE = "space:me:write";

    private SpaceMcpScopes() {
    }
}
