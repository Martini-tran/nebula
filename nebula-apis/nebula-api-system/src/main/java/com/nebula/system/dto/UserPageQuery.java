package com.nebula.system.dto;

import com.nebula.common.core.domain.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户分页查询入参
 *
 * @author nebula
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class UserPageQuery extends PageQuery {

    /**
     * 用户名（模糊匹配）
     */
    private String username;

    /**
     * 昵称（模糊匹配）
     */
    private String nickname;

    /**
     * 手机号（精确匹配）
     */
    private String mobile;

    /**
     * 邮箱（精确匹配）
     */
    private String email;

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;

    /**
     * 创建时间起，ISO 格式（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd'T'HH:mm:ss）
     */
    private String createTimeStart;

    /**
     * 创建时间止，ISO 格式
     */
    private String createTimeEnd;
}
