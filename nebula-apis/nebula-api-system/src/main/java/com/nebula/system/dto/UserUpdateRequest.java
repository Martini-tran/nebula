package com.nebula.system.dto;

import lombok.Data;

/**
 * 后台修改用户资料入参
 * 不允许修改 username 与 password，密码请走 /user/{id}/password
 *
 * @author nebula
 */
@Data
public class UserUpdateRequest {

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 手机号
     */
    private String mobile;

    /**
     * 邮箱
     */
    private String email;

    /**
     * 状态：1正常 0禁用
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;
}
