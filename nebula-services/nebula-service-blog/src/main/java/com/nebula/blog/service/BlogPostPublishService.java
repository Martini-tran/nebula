package com.nebula.blog.service;

import com.nebula.blog.dto.admin.PostPublishRequest;
import com.nebula.blog.vo.admin.PostPublishVO;

/**
 * 文章发布服务（供脚本等外部调用方使用）
 * 一次调用完成「按 slug 建或更新文章」与「挂到系列目录」，二者同一事务。
 */
public interface BlogPostPublishService {

    /**
     * 发布文章，按 slug 幂等。
     */
    PostPublishVO publish(PostPublishRequest req);
}
