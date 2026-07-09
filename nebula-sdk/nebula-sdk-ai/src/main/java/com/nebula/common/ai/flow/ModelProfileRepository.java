package com.nebula.common.ai.flow;

/**
 * 模型档案仓储
 * 负责按编码加载模型档案。SDK 默认提供内存实现，业务侧可覆盖为数据库实现（参见配套 DDL：ai_model_profile）。
 *
 * @author nebula
 */
public interface ModelProfileRepository {

    /**
     * 按编码加载模型档案
     *
     * @param profileCode 档案编码
     * @return 模型档案，不存在时返回 null
     */
    ModelProfile findByCode(String profileCode);
}
