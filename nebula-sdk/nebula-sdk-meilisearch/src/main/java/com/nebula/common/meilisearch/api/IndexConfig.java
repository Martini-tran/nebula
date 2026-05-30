package com.nebula.common.meilisearch.api;

import lombok.Data;

import java.util.List;

/**
 * 索引配置
 *
 * @author nebula
 */
@Data
public class IndexConfig {

    /**
     * 索引唯一名称
     */
    private String uid;

    /**
     * 主键字段名
     */
    private String primaryKey = "id";

    /**
     * 可搜索属性
     */
    private List<String> searchableAttributes;

    /**
     * 可过滤属性
     */
    private List<String> filterableAttributes;

    /**
     * 可排序属性
     */
    private List<String> sortableAttributes;

    /**
     * 显示属性，默认全部
     */
    private List<String> displayedAttributes;

    public IndexConfig() {
    }

    public IndexConfig(String uid) {
        this.uid = uid;
    }

    public IndexConfig(String uid, String primaryKey) {
        this.uid = uid;
        this.primaryKey = primaryKey;
    }
}
