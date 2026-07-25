package com.nebula.manager.dto;

import lombok.Data;

/**
 * 文档导入请求
 * 直接以正文文本导入（切块 → embed → 入库）。{@code docId} 缺省由服务端生成；同 docId 重复导入覆盖旧切片。
 *
 * @author nebula
 */
@Data
public class KnowledgeDocumentImportRequest {

    /**
     * 文档标识，库内唯一（留空由服务端生成）
     */
    private String docId;

    /**
     * 标题
     */
    private String title;

    /**
     * 来源类型（text/markdown/url 等，缺省 text）
     */
    private String sourceType;

    /**
     * 来源地址（可空）
     */
    private String sourceUri;

    /**
     * 正文文本
     */
    private String content;
}
