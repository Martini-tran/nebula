package com.nebula.manager.ai.tool;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.ToolPageQuery;
import com.nebula.manager.vo.ToolVO;

/**
 * AI 工具查询服务接口（管理员端，只读）
 * 工具定义于代码、由启动同步器维护进 {@code ai_tool} 表，管理端仅提供查询，无增删改。
 *
 * @author nebula
 */
public interface ToolAdminService {

    /**
     * 分页查询工具
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<ToolVO> page(ToolPageQuery query);

    /**
     * 获取工具详情
     *
     * @param id 工具ID
     * @return 详情
     */
    ToolVO detail(Long id);
}
