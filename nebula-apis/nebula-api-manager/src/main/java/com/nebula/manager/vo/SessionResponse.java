package com.nebula.manager.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private boolean isLogin;
    private Object loginId;
    private Long tokenTimeout;
}
