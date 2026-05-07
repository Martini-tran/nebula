package com.nebula.system.dto;

import lombok.Data;

/**
 * 后台创建用户入参
 *
 * @author nebula
 */
@Data
public class UserCreateRequest {

    /**
     * 登录账号
     */
    private String username;

    /**
     * 登录密码（明文，由后端 BCrypt 加密落库）
     */
    private String password;

    /**
     * 昵称（可选，不填默认使用 username）
     */
    private String nickname;

    /**
     * 头像地址（可选）
     */
    private String avatar;

    /**
     * 手机号（可选）
     */
    private String mobile;

    /**
     * 邮箱（可选）
     */
    private String email;

    /**
     * 状态：1正常 0禁用，缺省按 1
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
