package com.nebula.blog.service;

import com.nebula.blog.dto.front.SeriesPageQuery;
import com.nebula.blog.vo.front.SeriesDetailVO;
import com.nebula.blog.vo.front.SeriesListResponse;

/**
 * 博客系列前台服务接口
 */
public interface BlogSeriesService {

    /**
     * 分页查询系列（仅 published + public）
     */
    SeriesListResponse getSeriesList(SeriesPageQuery query);

    /**
     * 按 slug 获取系列详情，含目录树与扁平章节列表。
     *
     * @return 系列详情，不存在或不可见时返回 null
     */
    SeriesDetailVO getSeriesDetail(String slug);
}
