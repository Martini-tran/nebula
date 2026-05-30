package com.nebula.common.meilisearch.api;

import java.util.List;

/**
 * Meilisearch搜索服务
 *
 * @author nebula
 */
public interface MeilisearchService {

    /**
     * 创建索引（若不存在）并应用配置
     *
     * @param indexConfig 索引配置
     */
    void createIndexIfAbsent(IndexConfig indexConfig);

    /**
     * 删除索引
     *
     * @param indexUid 索引唯一名
     */
    void deleteIndex(String indexUid);

    /**
     * 判断索引是否存在
     *
     * @param indexUid 索引唯一名
     * @return 是否存在
     */
    boolean indexExists(String indexUid);

    /**
     * 添加或更新文档（按主键覆盖现有字段，缺失字段保持不变）
     *
     * @param indexUid 索引唯一名
     * @param document 单个文档对象
     */
    void addOrUpdateDocument(String indexUid, Object document);

    /**
     * 批量添加或更新文档
     *
     * @param indexUid  索引唯一名
     * @param documents 文档集合
     */
    void addOrUpdateDocuments(String indexUid, List<?> documents);

    /**
     * 完全替换文档（缺失字段会被清空）
     *
     * @param indexUid  索引唯一名
     * @param documents 文档集合
     */
    void replaceDocuments(String indexUid, List<?> documents);

    /**
     * 删除单个文档
     *
     * @param indexUid 索引唯一名
     * @param id       文档主键值
     */
    void deleteDocument(String indexUid, Object id);

    /**
     * 批量删除文档
     *
     * @param indexUid 索引唯一名
     * @param ids      文档主键值集合
     */
    void deleteDocuments(String indexUid, List<?> ids);

    /**
     * 清空索引下所有文档
     *
     * @param indexUid 索引唯一名
     */
    void deleteAllDocuments(String indexUid);

    /**
     * 简单搜索
     *
     * @param indexUid 索引唯一名
     * @param keyword  查询关键字
     * @param clazz    目标类型
     * @param <T>      文档类型
     * @return 搜索结果
     */
    <T> MeiliSearchResult<T> search(String indexUid, String keyword, Class<T> clazz);

    /**
     * 高级搜索
     *
     * @param indexUid 索引唯一名
     * @param query    搜索查询
     * @param clazz    目标类型
     * @param <T>      文档类型
     * @return 搜索结果
     */
    <T> MeiliSearchResult<T> search(String indexUid, MeiliSearchQuery query, Class<T> clazz);

    /**
     * 获取原生 Meilisearch 客户端，用于使用未封装的能力
     *
     * @return Meilisearch 客户端
     */
    com.meilisearch.sdk.Client getClient();
}
