package com.nebula.manager.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 知识库文档行（管理员端）
 *
 * @author nebula
 */
@Data
public class KnowledgeDocumentVO {

    /**
     * 文档ID
     */
    private Long id;

    /**
     * 所属知识库编码
     */
    private String kbCode;

    /**
     * 文档标识
     */
    private String docId;

    /**
     * 标题
     */
    private String title;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 来源地址
     */
    private String sourceUri;

    /**
     * 正文字符数
     */
    private Integer charCount;

    /**
     * 切块数
     */
    private Integer chunkCount;

    /**
     * 状态：0=索引中 1=完成 2=失败
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
}
