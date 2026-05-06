package com.nebula.common.captcha.controller;

import com.nebula.common.captcha.model.CaptchaCheckRequest;
import com.nebula.common.captcha.model.CaptchaGetRequest;
import com.nebula.common.captcha.service.CaptchaService;
import com.nebula.common.core.domain.R;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 验证码端点：仅需依赖 nebula-sdk-captcha 即可对外暴露。
 *
 * @author nebula
 */
@RestController
@RequestMapping("/captcha")
public class CaptchaController {

    private final CaptchaService captchaService;

    public CaptchaController(CaptchaService captchaService) {
        this.captchaService = captchaService;
    }

    @PostMapping("/get")
    public R<Map<String, Object>> get(@RequestBody(required = false) CaptchaGetRequest request,
                                      HttpServletRequest http) {
        CaptchaGetRequest body = request != null ? request : new CaptchaGetRequest();
        return R.success(captchaService.get(body, clientIp(http)));
    }

    @PostMapping("/check")
    public R<Map<String, Object>> check(@RequestBody CaptchaCheckRequest request,
                                        HttpServletRequest http) {
        return R.success(captchaService.check(request, clientIp(http)));
    }

    private String clientIp(HttpServletRequest req) {
        String ip = req.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            int comma = ip.indexOf(',');
            return comma > 0 ? ip.substring(0, comma).trim() : ip.trim();
        }
        ip = req.getHeader("X-Real-IP");
        if (ip != null && !ip.isBlank()) {
            return ip.trim();
        }
        return req.getRemoteAddr();
    }
}
