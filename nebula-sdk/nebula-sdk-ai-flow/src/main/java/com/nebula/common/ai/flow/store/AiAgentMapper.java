package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 智能体定义 Mapper
 * 继承 {@code BaseMapper<AiAgent>}，获得完整 CRUD 能力。
 *
 * @author nebula
 */
@Mapper
public interface AiAgentMapper extends BaseMapper<AiAgent> {
}
