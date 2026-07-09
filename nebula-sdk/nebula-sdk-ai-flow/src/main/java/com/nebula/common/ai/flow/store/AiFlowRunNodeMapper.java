package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI流程执行节点轨迹 Mapper
 * 继承 {@code BaseMapper<AiFlowRunNode>}，获得完整 CRUD 能力，无需自定义 SQL。
 *
 * @author nebula
 */
@Mapper
public interface AiFlowRunNodeMapper extends BaseMapper<AiFlowRunNode> {
}
