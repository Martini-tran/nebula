package com.nebula.auth.model;

import cn.dev33.satoken.stp.SaTokenInfo;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    private boolean isLogin;
    private Object loginId;
    private SaTokenInfo tokenInfo;
}
