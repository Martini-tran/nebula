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
 * 知识库切片表
 * 文档切块后的最小检索单元，是切片正文的<b>真相源</b>（向量存 Milvus，正文存本表）。检索命中回 pk 后可据
 * {@code kb_code}/{@code doc_id}/{@code chunk_index} 回查正文，也可直接用 Milvus 返回的 content 副本省一次回查。
 *
 * @author nebula
 */
@Data
@TableName("ai_knowledge_chunk")
public class AiKnowledgeChunk implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 切片ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 所属知识库编码
     */
    private String kbCode;

    /**
     * 来源文档标识
     */
    private String docId;

    /**
     * 切片在文档内的序号
     */
    private Integer chunkIndex;

    /**
     * 切片正文（真相源）
     */
    private String content;

    /**
     * 粗略 token 数（按字符估算）
     */
    private Integer tokenCount;

    /**
     * 附加元数据（JSON 字符串，来源/标题/页码等）
     */
    private String metadata;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
