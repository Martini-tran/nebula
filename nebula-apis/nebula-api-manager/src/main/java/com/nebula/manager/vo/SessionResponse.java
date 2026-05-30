package com.nebula.manager.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话响应VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionResponse {

    /**
     * 是否已登录
     */
    private boolean isLogin;

    /**
     * 登录ID
     */
    private Object loginId;

    /**
     * 令牌剩余有效时间（秒）
     */
    private Long tokenTimeout;
}
