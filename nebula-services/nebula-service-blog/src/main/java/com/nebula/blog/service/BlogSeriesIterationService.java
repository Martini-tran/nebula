package com.nebula.blog.service;

import com.nebula.blog.dto.admin.SeriesIterationAppendRequest;

/**
 * 迭代链回调落库服务（编排 webhook 消费端）
 * 每轮 advance 成功的回调落到这里：首轮建系列（记 chain_id）+ 建"正文"目录节点，每轮建文章（草稿、sourceType=AI）
 * 并挂到该目录下。幂等键 {@code (chainId, seq)}——重复投递返回既有文章 id，不重复落库。
 * 见 docs/编排回调Webhook设计.md。
 *
 * @author nebula
 */
public interface BlogSeriesIterationService {

    /**
     * 落一篇迭代链产出的文章（幂等）。
     *
     * @param req 回调请求（chainId/seq/系列名/文章标题正文摘要）
     * @return 本篇文章 id（幂等：重复投递返回既有 id）
     */
    Long append(SeriesIterationAppendRequest req);
}
