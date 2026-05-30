package com.nebula.blog.service.impl;

import com.nebula.blog.entity.BlogSearchIndexTask;
import com.nebula.blog.event.PostIndexEvent;
import com.nebula.blog.mapper.BlogSearchIndexTaskMapper;
import com.nebula.blog.service.BlogSearchSyncService;
import com.nebula.common.meilisearch.api.MeilisearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文章搜索索引同步服务实现
 * <p>
 * 在文章写操作事务提交（AFTER_COMMIT）后异步触发 Meilisearch 同步，保证最终一致性。
 * Meilisearch 调用失败时，记录 blog_search_index_task（status=failed）供后续排查。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BlogSearchSyncServiceImpl implements BlogSearchSyncService {

    private final MeilisearchService meilisearchService;
    private final BlogSearchIndexTaskMapper taskMapper;

    /**
     * 从 application.yml 读取索引 UID，默认值为 nebula_blog_posts
     * 对应配置：nebula.meilisearch.indexes.nebula_blog_posts.uid
     */
    @Value("${nebula.meilisearch.indexes.nebula_blog_posts.uid:nebula_blog_posts}")
    private String indexUid;

    /**
     * 监听 PostIndexEvent，在父事务提交后执行 Meilisearch 同步。
     * <p>
     * 采用 AFTER_COMMIT 阶段：确保 DB 数据已落库，搜索引擎不会读到脏数据。
     * 若 Meilisearch 不可用，捕获异常后仅记录错误日志和失败任务，不影响主流程响应。
     */
    @Override
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onPostIndex(PostIndexEvent event) {
        try {
            if ("upsert".equals(event.getAction())) {
                meilisearchService.addOrUpdateDocument(indexUid, event.getDocument());
                log.debug("Meilisearch upsert success, post={}", event.getPostId());
            } else {
                meilisearchService.deleteDocument(indexUid, event.getPostId());
                log.debug("Meilisearch delete success, post={}", event.getPostId());
            }
        } catch (Exception e) {
            log.error("Meilisearch sync failed, post={}, action={}", event.getPostId(), event.getAction(), e);
            recordFailedTask(event.getPostId(), event.getAction(), e.getMessage());
        }
    }

    /**
     * 记录同步失败任务。
     * <p>
     * 使用 REQUIRES_NEW 开启独立事务，确保即使父事务已提交、当前上下文无事务，
     * 也能正常写入 blog_search_index_task 表。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailedTask(Long postId, String action, String errorMessage) {
        try {
            BlogSearchIndexTask task = new BlogSearchIndexTask();
            task.setPostId(postId);
            task.setIndexName(indexUid);
            task.setAction(action);
            task.setStatus("failed");
            task.setRetryCount(0);
            // errorMessage 字段为 text 类型，截断保护
            if (errorMessage != null && errorMessage.length() > 2000) {
                errorMessage = errorMessage.substring(0, 2000);
            }
            task.setErrorMessage(errorMessage);
            taskMapper.insert(task);
        } catch (Exception ex) {
            // 失败任务记录本身失败，只能打日志，绝不能再抛出
            log.error("Failed to record search index task, post={}, action={}", postId, action, ex);
        }
    }
}
