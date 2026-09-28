package com.nebula.space.vo.me;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 分享页（公开，无需登录）看到的信息
 */
@Data
public class SharePublicVO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String code;

    private String fileName;

    private Boolean isFolder;

    /**
     * 字节数；文件夹为里面文件的合计
     */
    private BigDecimal size;

    private boolean needPassword;

    private LocalDateTime expireAt;

    /**
     * 不能下载的原因：过期、取消、次数用完、文件已删；能下载为空
     */
    private String unavailable;

    /**
     * 分享人的昵称
     */
    private String owner;
}
