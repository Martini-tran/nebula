package com.nebula.manager.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class UserListVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String mobile;
    private String email;
    private Integer status;
    private String remark;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    /** 是否为超级管理员（前端用于禁用编辑/删除/禁用/重置密码按钮） */
    private Boolean superAdmin;
}
