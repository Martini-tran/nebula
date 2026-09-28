package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 文件柜里的一个文件或文件夹
 */
@Data
public class SpaceFileVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    /**
     * 所在文件夹，空为根目录
     */
    private Long folderId;

    private Boolean isFolder;

    private String name;

    /**
     * 字节数；全局把 Long 输出成字符串，前端要参与计算，这里用 BigDecimal 保持 JSON 数字
     */
    private BigDecimal size;

    private String mime;

    /**
     * upload / notes / meetings / reading / bookmarks
     */
    private String source;

    /**
     * 移进最近删除的时间，空为正常
     */
    private LocalDateTime deleteTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
