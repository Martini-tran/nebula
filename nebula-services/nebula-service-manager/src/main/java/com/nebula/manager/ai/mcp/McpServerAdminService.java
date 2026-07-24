package com.nebula.manager.ai.mcp;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.McpServerPageQuery;
import com.nebula.manager.dto.McpServerSaveRequest;
import com.nebula.manager.vo.McpServerVO;

/**
 * AI MCP 服务器管理服务接口（管理员端）
 * 负责 MCP 服务器配置（{@code ai_mcp_server}）的分页/详情/创建/更新/删除/启停。authToken 以密文存储，
 * 出参一律掩码、不回传明文。
 *
 * @author nebula
 */
public interface McpServerAdminService {

    /**
     * 分页查询 MCP 服务器
     *
     * @param query 查询参数
     * @return 分页结果（authToken 掩码）
     */
    PageResult<McpServerVO> page(McpServerPageQuery query);

    /**
     * 获取 MCP 服务器详情
     *
     * @param id 服务器ID
     * @return 详情（authToken 掩码）
     */
    McpServerVO detail(Long id);

    /**
     * 创建 MCP 服务器
     *
     * @param request 保存请求（authToken 明文）
     * @return 新建服务器ID
     */
    Long create(McpServerSaveRequest request);

    /**
     * 更新 MCP 服务器（authToken 留空表示不修改原凭证）
     *
     * @param id      服务器ID
     * @param request 保存请求
     */
    void update(Long id, McpServerSaveRequest request);

    /**
     * 删除 MCP 服务器
     *
     * @param id 服务器ID
     */
    void delete(Long id);

    /**
     * 更新启用/停用状态
     *
     * @param id     服务器ID
     * @param status 状态：0=停用 1=启用
     */
    void updateStatus(Long id, Integer status);
}
