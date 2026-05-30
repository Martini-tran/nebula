package com.nebula.blog.service;

import com.nebula.blog.event.PostIndexEvent;

/**
 * 文章搜索索引同步服务
 * <p>
 * 监听 {@link PostIndexEvent} 并在事务提交后将文章数据同步到 Meilisearch。
 * 失败时记录 blog_search_index_task 以供人工排查或后续重试。
 */
public interface BlogSearchSyncService {

    /**
     * 处理文章索引事件（upsert / delete）
     *
     * @param event 文章索引事件
     */
    void onPostIndex(PostIndexEvent event);
}
