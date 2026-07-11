package com.nebula.manager.ai;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.PromptPageQuery;
import com.nebula.manager.dto.PromptSaveRequest;
import com.nebula.manager.vo.PromptVO;

/**
 * AI提示词管理服务接口（管理员端）
 * 负责提示词（{@code ai_prompt}）的分页/详情/创建/更新/删除。
 *
 * @author nebula
 */
public interface PromptAdminService {

    /**
     * 分页查询提示词
     *
     * @param query 查询参数
     * @return 分页结果
     */
    PageResult<PromptVO> page(PromptPageQuery query);

    /**
     * 获取提示词详情
     *
     * @param id 提示词ID
     * @return 提示词详情
     */
    PromptVO detail(Long id);

    /**
     * 创建提示词
     *
     * @param request 保存请求
     * @return 新建提示词ID
     */
    Long create(PromptSaveRequest request);

    /**
     * 更新提示词（提示词编码不可变更）
     *
     * @param id      提示词ID
     * @param request 保存请求
     */
    void update(Long id, PromptSaveRequest request);

    /**
     * 删除提示词
     *
     * @param id 提示词ID
     */
    void delete(Long id);
}
