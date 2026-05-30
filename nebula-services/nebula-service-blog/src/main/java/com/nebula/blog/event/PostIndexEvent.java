package com.nebula.blog.event;

import com.nebula.blog.vo.admin.PostSearchDocument;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 文章搜索索引事件
 * <p>
 * 由 BlogPostAdminServiceImpl 在事务提交前发布；
 * BlogSearchSyncServiceImpl 监听 AFTER_COMMIT 阶段，执行 Meilisearch 同步。
 */
@Getter
public class PostIndexEvent extends ApplicationEvent {

    /**
     * 文章 ID
     */
    private final Long postId;

    /**
     * 操作类型：upsert（新增/更新）或 delete（删除）
     */
    private final String action;

    /**
     * Meilisearch 文档数据；action=delete 时为 null
     */
    private final PostSearchDocument document;

    public PostIndexEvent(Object source, Long postId, String action, PostSearchDocument document) {
        super(source);
        this.postId = postId;
        this.action = action;
        this.document = document;
    }
}
