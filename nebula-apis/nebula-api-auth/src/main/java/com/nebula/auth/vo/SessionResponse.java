package com.nebula.auth.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    /**
     * 当前会话是否已登录
     */
    private boolean isLogin;
    /**
     * 已登录时的登录主体 ID（默认是 sys_user.id），未登录返回 null
     */
    private Object loginId;
    /**
     * 已登录时 token 剩余有效秒数，永不过期返回 -1，未登录返回 null
     */
    private Long tokenTimeout;
}
