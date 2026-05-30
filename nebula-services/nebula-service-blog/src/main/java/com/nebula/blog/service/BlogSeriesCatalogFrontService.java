package com.nebula.blog.service;

import com.nebula.blog.vo.front.SeriesCatalogNodeVO;
import com.nebula.blog.vo.front.SeriesChapterVO;

import java.util.List;

/**
 * 系列目录前台服务
 */
public interface BlogSeriesCatalogFrontService {

    /**
     * 获取指定系列的目录树（仅已发布系列 + 已发布 + 公开文章）
     */
    List<SeriesCatalogNodeVO> getCatalogTree(Long seriesId);

    /**
     * 获取目录节点下的文章列表（仅已发布 + 公开）
     */
    List<SeriesChapterVO> listPostsByCatalog(Long catalogId);
}
