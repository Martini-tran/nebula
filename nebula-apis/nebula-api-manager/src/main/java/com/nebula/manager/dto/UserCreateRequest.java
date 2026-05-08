package com.nebula.manager.dto;

import lombok.Data;

@Data
public class UserCreateRequest {

    private String username;
    private String password;
    private String nickname;
    private String avatar;
    private String mobile;
    private String email;
    private Integer status;
    private String remark;
}
