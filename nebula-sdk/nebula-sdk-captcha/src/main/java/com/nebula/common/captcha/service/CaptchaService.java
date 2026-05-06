package com.nebula.common.captcha.service;

import com.nebula.common.captcha.model.CaptchaCheckRequest;
import com.nebula.common.captcha.model.CaptchaGetRequest;
import java.util.Map;

/**
 * Nebula 对外的验证码门面：屏蔽 AJ-Captcha 内部细节，
 * 同时增加重复生成节流、verifyToken 一次性消费等额外语义。
 *
 * @author nebula
 */
public interface CaptchaService {

    /**
     * 生成验证码。
     * 含「同 IP / clientUid 重复生成节流」校验。
     *
     * @return AJ-Captcha 标准返回结构（包含原图 base64、滑块图 base64、token 等）
     */
    Map<String, Object> get(CaptchaGetRequest request, String clientIp);

    /**
     * 用户提交验证答案。
     * 通过后返回包含 verifyToken 的结构，调用方需把 verifyToken 传给登录/注册接口。
     */
    Map<String, Object> check(CaptchaCheckRequest request, String clientIp);

    /**
     * 给登录等下游接口使用：消费一次性 verifyToken。
     * 失败抛 {@link com.nebula.common.core.exception.BizException}。
     */
    void assertVerified(String captchaType, String verifyToken);
}
