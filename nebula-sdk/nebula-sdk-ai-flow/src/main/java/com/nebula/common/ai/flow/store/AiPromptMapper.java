package com.nebula.common.ai.flow.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI提示词 Mapper
 *
 * @author nebula
 */
@Mapper
public interface AiPromptMapper extends BaseMapper<AiPrompt> {
}
