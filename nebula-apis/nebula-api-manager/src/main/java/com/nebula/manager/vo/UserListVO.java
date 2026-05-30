package com.nebula.manager.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户列表VO
 */
@Data
public class UserListVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
     */
    private String username;

    /**
     * 昵称
     */
    private String nickname;

    /**
     * 头像URL
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
     * 状态
     */
    private Integer status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;

    /**
     * 更新时间
     */
    private LocalDateTime updateTime;

    /**
     * 是否为超级管理员（前端用于禁用编辑/删除/禁用/重置密码按钮）
     */
    private Boolean superAdmin;
}
