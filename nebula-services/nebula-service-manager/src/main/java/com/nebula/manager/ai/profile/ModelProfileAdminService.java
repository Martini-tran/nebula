package com.nebula.manager.ai.profile;

import com.nebula.common.core.domain.PageResult;
import com.nebula.manager.dto.ModelProfilePageQuery;
import com.nebula.manager.dto.ModelProfileSaveRequest;
import com.nebula.manager.vo.ModelProfileVO;

/**
 * AI模型档案管理服务接口（管理员端）
 * 负责模型档案（{@code ai_model_profile}）的分页/详情/创建/更新/删除/启停。apiKey 以密文存储，
 * 出参一律掩码、不回传明文。
 *
 * @author nebula
 */
public interface ModelProfileAdminService {

    /**
     * 分页查询模型档案
     *
     * @param query 查询参数
     * @return 分页结果（apiKey 掩码）
     */
    PageResult<ModelProfileVO> page(ModelProfilePageQuery query);

    /**
     * 获取模型档案详情
     *
     * @param id 档案ID
     * @return 档案详情（apiKey 掩码）
     */
    ModelProfileVO detail(Long id);

    /**
     * 创建模型档案
     *
     * @param request 保存请求（apiKey 明文）
     * @return 新建档案ID
     */
    Long create(ModelProfileSaveRequest request);

    /**
     * 更新模型档案（apiKey 留空表示不修改原密钥）
     *
     * @param id      档案ID
     * @param request 保存请求
     */
    void update(Long id, ModelProfileSaveRequest request);

    /**
     * 删除模型档案
     *
     * @param id 档案ID
     */
    void delete(Long id);

    /**
     * 更新启用/停用状态
     *
     * @param id     档案ID
     * @param status 状态：0=停用 1=启用
     */
    void updateStatus(Long id, Integer status);
}
