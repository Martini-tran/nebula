package com.nebula.manager.vo;

import lombok.Data;

import java.util.Map;

/**
 * 知识库检索命中行（管理员端检索预览）
 *
 * @author nebula
 */
@Data
public class KnowledgeSearchHitVO {

    /**
     * 命中切片正文
     */
    private String content;

    /**
     * 归一化相似度得分（[0,1]，降序）
     */
    private Double score;

    /**
     * 附加元数据
     */
    private Map<String, Object> metadata;
}
