package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Map;

/**
 * 文件柜容量（字节，BigDecimal 保持 JSON 数字）
 */
@Data
public class FileUsageVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 已用，最近删除里的也算
     */
    private BigDecimal used;

    /**
     * 每人容量
     */
    private BigDecimal total;

    /**
     * 单个文件大小上限，前端上传前先查一遍
     */
    private BigDecimal maxFileSize;

    /**
     * 按类型分的已用：pdf / image / doc / sheet / zip / other
     */
    private Map<String, BigDecimal> byKind;
}
