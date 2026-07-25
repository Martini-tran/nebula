package com.nebula.common.ai.rag.knowledge.store;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 知识库文档表
 * 记录一篇被导入知识库的文档及其索引进度。{@code doc_id} 在库内唯一定位一篇文档，切块后逐块入 {@link AiKnowledgeChunk}
 * 与 Milvus。{@code status} 标索引中/完成/失败，供管理面观测导入进度。
 *
 * @author nebula
 */
@Data
@TableName("ai_knowledge_document")
public class AiKnowledgeDocument implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 索引状态：索引中
     */
    public static final int STATUS_INDEXING = 0;

    /**
     * 索引状态：完成
     */
    public static final int STATUS_DONE = 1;

    /**
     * 索引状态：失败
     */
    public static final int STATUS_FAILED = 2;

    /**
     * 文档ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属知识库编码
     */
    private String kbCode;

    /**
     * 文档标识，库内唯一
     */
    private String docId;

    /**
     * 标题
     */
    private String title;

    /**
     * 来源类型（text/markdown/url 等）
     */
    private String sourceType;

    /**
     * 来源地址（URL 或原始路径，可空）
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
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
