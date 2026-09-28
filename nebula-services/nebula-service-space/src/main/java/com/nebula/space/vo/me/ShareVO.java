package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我分享的链接（只给分享人自己看，带提取码原文方便再复制）
 */
@Data
public class ShareVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;

    private String code;

    private Long fileId;

    /**
     * 文件还在就是现在的名字，彻底删除了用分享时的名字
     */
    private String fileName;

    private Boolean isFolder;

    /**
     * 提取码，空为不需要
     */
    private String password;

    /**
     * 过期时间，空为永久
     */
    private LocalDateTime expireAt;

    private Integer maxDownloads;

    private Integer downloads;

    private boolean revoked;

    private LocalDateTime createTime;
}
