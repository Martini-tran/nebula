package com.nebula.common.ai.rag.memory.store;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nebula.blog.entity.AiMemory;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 长期记忆 Mapper。
 *
 * <p>批次4 从 blog 服务下沉至 sdk-ai-rag：记忆的 DB 真相源是通用能力（凡依赖 rag 的服务——manager 的 Agent 引擎、
 * blog 的博客 Agent——都需要），不应困在 blog 进程。实体 {@link AiMemory} 沿用 sdk-domain 既有位置
 * （{@code com.nebula.blog.entity}）。
 *
 * @author nebula
 */
@Mapper
public interface AiMemoryMapper extends BaseMapper<AiMemory> {
}
