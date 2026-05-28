package com.nebula.blog.vo.front;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * AI 中转服务商优势 VO（前台）
 */
@Data
public class AiRelayProviderAdvantageFrontVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private Long providerId;

    private String title;

    private String content;

    /**
     * 优势类型：1=核心 2=普通 3=待改进
     */
    private Integer advantageType;

    /**
     * 图标可访问 URL
     */
    private String iconUrl;
}
