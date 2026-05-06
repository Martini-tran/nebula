package com.nebula.common.captcha.model;

import lombok.Data;

@Data
public class CaptchaGetRequest {

    /**
     * 验证码类型：blockPuzzle / clickWord，不传走默认
     */
    private String captchaType;

    /**
     * 客户端唯一标识（前端生成 uuid，跟 verifyToken 绑定）
     */
    private String clientUid;

    /**
     * 浏览器/设备信息（可选）
     */
    private String browserInfo;
}
