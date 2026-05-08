package com.nebula.manager.dto;

import lombok.Data;

@Data
public class UserUpdateRequest {

    private String nickname;
    private String avatar;
    private String mobile;
    private String email;
    private Integer status;
    private String remark;
}
