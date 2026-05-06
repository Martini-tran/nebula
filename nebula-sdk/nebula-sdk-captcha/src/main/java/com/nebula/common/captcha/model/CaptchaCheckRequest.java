package com.nebula.common.captcha.model;

import lombok.Data;

@Data
public class CaptchaCheckRequest {

    /** 验证码类型：blockPuzzle / clickWord */
    private String captchaType;

    /** /captcha/get 返回的 token */
    private String token;

    /** 用户答案：滑块为坐标 JSON，文字点选为点击坐标 JSON */
    private String pointJson;
}
