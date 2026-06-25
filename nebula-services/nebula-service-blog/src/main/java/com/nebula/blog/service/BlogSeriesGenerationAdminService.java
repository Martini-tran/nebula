package com.nebula.blog.service;

import com.nebula.blog.dto.admin.SeriesGenerateRequest;

/**
 * 博客系列主题生成服务接口（管理员端）
 * 调用AI博客Agent生成系列选题大纲，并落库为系列与目录节点。
 */
public interface BlogSeriesGenerationAdminService {

    /**
     * 生成系列主题并落库
     * 围绕请求主题规划出整个系列的选题大纲，写入 blog_series 与 blog_series_catalog。
     *
     * @param req 系列主题生成请求
     * @return 新建系列ID
     */
    Long generate(SeriesGenerateRequest req);
}
