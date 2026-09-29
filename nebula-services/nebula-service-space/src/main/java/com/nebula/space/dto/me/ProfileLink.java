package com.nebula.space.dto.me;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 公开主页上的一个外部链接：GitHub、博客、邮箱（存在 JSON 列里）
 */
@Data
public class ProfileLink implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Size(max = 12, message = "链接名称最长 12 字")
    private String label;

    /**
     * http(s) 网址或邮箱
     */
    @Size(max = 500, message = "链接地址最长 500 字")
    private String url;
}
