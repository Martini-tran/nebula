package com.nebula.scribe.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 作品详情，字段对齐前端 {@code WorkDetail}
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WorkDetailVO extends WorkListVO {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 作品简介，纯文本
     */
    private String intro;

    /**
     * 一句话立意/核心冲突
     */
    private String logline;
}
