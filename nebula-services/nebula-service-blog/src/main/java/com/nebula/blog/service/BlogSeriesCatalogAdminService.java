package com.nebula.blog.service;

import com.nebula.blog.dto.admin.SeriesCatalogCreateRequest;
import com.nebula.blog.dto.admin.SeriesCatalogPostBindRequest;
import com.nebula.blog.dto.admin.SeriesCatalogUpdateRequest;
import com.nebula.blog.vo.admin.SeriesCatalogAdminVO;
import com.nebula.blog.vo.admin.SeriesCatalogPostVO;

import java.util.List;

/**
 * 博客系列目录管理服务接口（管理员端）
 */
public interface BlogSeriesCatalogAdminService {

    /**
     * 获取指定系列的目录树
     */
    List<SeriesCatalogAdminVO> getCatalogTree(Long seriesId);

    /**
     * 创建目录节点
     *
     * @return 节点ID
     */
    Long create(SeriesCatalogCreateRequest req);

    /**
     * 更新目录节点
     */
    void update(Long id, SeriesCatalogUpdateRequest req);

    /**
     * 删除目录节点（级联删除子节点与文章关联）
     */
    void delete(Long id);

    /**
     * 查询目录节点下的文章列表（按 sortOrder 升序）
     */
    List<SeriesCatalogPostVO> listPosts(Long catalogId);

    /**
     * 全量替换目录节点的文章绑定，sort_order 按入参顺序生成
     */
    void bindPosts(Long catalogId, SeriesCatalogPostBindRequest req);
}
