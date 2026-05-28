package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 模型 VO（前台）
 */
@Data
public class AiRelayModelFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private String name;

    /**
     * 厂商：Anthropic / OpenAI / Google ...
     */
    private String modelVendor;

    /**
     * 模型类型：1=对话 2=多模态 3=图像 ... （由字典定义）
     */
    private Integer modelType;

    private String description;
}
