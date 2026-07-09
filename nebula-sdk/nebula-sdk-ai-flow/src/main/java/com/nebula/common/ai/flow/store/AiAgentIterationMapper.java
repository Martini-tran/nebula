package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 智能体迭代链 Mapper
 * 继承 {@code BaseMapper<AiAgentIteration>}，获得完整 CRUD 能力。
 *
 * @author nebula
 */
@Mapper
public interface AiAgentIterationMapper extends BaseMapper<AiAgentIteration> {
}
