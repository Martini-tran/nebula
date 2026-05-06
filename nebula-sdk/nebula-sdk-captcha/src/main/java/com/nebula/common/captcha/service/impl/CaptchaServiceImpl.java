package com.nebula.common.captcha.service.impl;

import com.anji.captcha.model.common.ResponseModel;
import com.anji.captcha.model.vo.CaptchaVO;
import com.nebula.common.captcha.constant.CaptchaConfigKeys;
import com.nebula.common.captcha.enums.CaptchaResultCode;
import com.nebula.common.captcha.model.CaptchaCheckRequest;
import com.nebula.common.captcha.model.CaptchaGetRequest;
import com.nebula.common.captcha.service.CaptchaService;
import com.nebula.common.config.service.SysConfigService;
import com.nebula.common.core.exception.BizException;
import com.nebula.common.redis.util.RedisUtils;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;

/**
 * Nebula 对外验证码门面实现。
 * <ul>
 *     <li>对 AJ-Captcha 的 get/check 调用透传，附加 Nebula 自己的频控、verifyToken 一次性化策略。</li>
 *     <li>所有可调参数从 {@link SysConfigService}（即 sys_config 表）实时读取，admin 改完即时生效。</li>
 * </ul>
 *
 * @author nebula
 */
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {

    /**
     * 同 IP 重复生成节流的 redis key 前缀
     */
    private static final String COOLDOWN_KEY_PREFIX = "nebula:captcha:cooldown:";
    /**
     * verifyToken 单次消费的 redis key 前缀
     */
    private static final String VERIFY_KEY_PREFIX = "nebula:captcha:verify:";

    private final com.anji.captcha.service.CaptchaService ajCaptchaService;
    private final SysConfigService configService;
    private final RedisUtils redis;

    public CaptchaServiceImpl(com.anji.captcha.service.CaptchaService ajCaptchaService,
                              SysConfigService configService,
                              RedisUtils redis) {
        this.ajCaptchaService = ajCaptchaService;
        this.configService = configService;
        this.redis = redis;
    }

    @Override
    public Map<String, Object> get(CaptchaGetRequest request, String clientIp) {
        String type = resolveType(request.getCaptchaType());
        ensureTypeEnabled(type);
        applyCooldown(clientIp);

        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaType(type);
        vo.setClientUid(request.getClientUid());
        vo.setBrowserInfo(request.getBrowserInfo());

        ResponseModel resp = ajCaptchaService.get(vo);
        if (resp == null || !"0000".equals(resp.getRepCode())) {
            log.warn("[captcha] generate fail: {}", resp == null ? "null" : resp.getRepMsg());
            throw new BizException(CaptchaResultCode.GENERATE_FAIL,
                    resp == null ? CaptchaResultCode.GENERATE_FAIL.getMessage() : resp.getRepMsg());
        }
        return wrap(resp);
    }

    @Override
    public Map<String, Object> check(CaptchaCheckRequest request, String clientIp) {
        if (request == null || request.getToken() == null || request.getCaptchaType() == null) {
            throw new BizException(CaptchaResultCode.CHECK_FAIL);
        }
        String type = resolveType(request.getCaptchaType());

        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaType(type);
        vo.setToken(request.getToken());
        vo.setPointJson(request.getPointJson());

        ResponseModel resp = ajCaptchaService.check(vo);
        if (resp == null || !"0000".equals(resp.getRepCode())) {
            throw new BizException(CaptchaResultCode.CHECK_FAIL,
                    resp == null ? CaptchaResultCode.CHECK_FAIL.getMessage() : resp.getRepMsg());
        }

        // resp.getRepData() 通常是一个 Map / CaptchaVO；提取 captchaVerification（AJ-Captcha 算法生成的一次性串），
        // 我们再额外封装一层 verifyToken（拼上 type + 哈希），写 Redis 让它仅可用一次。
        Object data = resp.getRepData();
        String ajVerification = extractVerification(data);
        if (ajVerification == null) {
            throw new BizException(CaptchaResultCode.CHECK_FAIL);
        }

        long ttlSeconds = configService.getInt(CaptchaConfigKeys.VERIFY_TIMEOUT_SECONDS,
                CaptchaConfigKeys.DEFAULT_VERIFY_TIMEOUT_SECONDS);
        redis.set(VERIFY_KEY_PREFIX + type + ":" + ajVerification, "1", Duration.ofSeconds(ttlSeconds));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verifyToken", ajVerification);
        out.put("captchaType", type);
        out.put("expiresInSeconds", ttlSeconds);
        return out;
    }

    @Override
    public void assertVerified(String captchaType, String verifyToken) {
        if (verifyToken == null || verifyToken.isBlank()) {
            throw new BizException(CaptchaResultCode.VERIFY_TOKEN_INVALID);
        }
        String type = resolveType(captchaType);
        String key = VERIFY_KEY_PREFIX + type + ":" + verifyToken;
        String marker = redis.getAndDelete(key);
        if (marker == null) {
            throw new BizException(CaptchaResultCode.VERIFY_TOKEN_INVALID);
        }
        // 还需把 AJ-Captcha 内部一次性校验串也消费掉（防止跨实例重放）
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaType(type);
        vo.setCaptchaVerification(verifyToken);
        ResponseModel resp = ajCaptchaService.verification(vo);
        if (resp == null || !"0000".equals(resp.getRepCode())) {
            throw new BizException(CaptchaResultCode.VERIFY_TOKEN_INVALID,
                    resp == null ? CaptchaResultCode.VERIFY_TOKEN_INVALID.getMessage() : resp.getRepMsg());
        }
    }

    // ===== helpers =====

    private String resolveType(String requested) {
        String def = configService.getOrDefault(CaptchaConfigKeys.TYPE_DEFAULT, CaptchaConfigKeys.DEFAULT_TYPE);
        String type = requested != null && !requested.isBlank() ? requested.trim() : def;
        if (!"blockPuzzle".equals(type) && !"clickWord".equals(type)) {
            throw new BizException(CaptchaResultCode.TYPE_UNKNOWN);
        }
        return type;
    }

    private void ensureTypeEnabled(String type) {
        if ("blockPuzzle".equals(type)
                && !configService.getBool(CaptchaConfigKeys.TYPE_SLIDER_ENABLED, CaptchaConfigKeys.DEFAULT_SLIDER_ENABLED)) {
            throw new BizException(CaptchaResultCode.TYPE_DISABLED);
        }
        if ("clickWord".equals(type)
                && !configService.getBool(CaptchaConfigKeys.TYPE_CLICK_ENABLED, CaptchaConfigKeys.DEFAULT_CLICK_ENABLED)) {
            throw new BizException(CaptchaResultCode.TYPE_DISABLED);
        }
    }

    private void applyCooldown(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        int interval = configService.getInt(CaptchaConfigKeys.REPEAT_INTERVAL_SECONDS,
                CaptchaConfigKeys.DEFAULT_REPEAT_INTERVAL_SECONDS);
        if (interval <= 0) {
            return;
        }
        Boolean ok = redis.setIfAbsent(COOLDOWN_KEY_PREFIX + clientIp, "1", Duration.ofSeconds(interval));
        if (!Boolean.TRUE.equals(ok)) {
            throw new BizException(CaptchaResultCode.REPEAT_TOO_FAST);
        }
    }

    private Map<String, Object> wrap(ResponseModel resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("repCode", resp.getRepCode());
        out.put("repMsg", resp.getRepMsg());
        out.put("repData", resp.getRepData());
        return out;
    }

    @SuppressWarnings("unchecked")
    private String extractVerification(Object data) {
        if (data instanceof Map<?, ?> map) {
            Object v = map.get("captchaVerification");
            return v == null ? null : v.toString();
        }
        if (data instanceof CaptchaVO vo) {
            return vo.getCaptchaVerification();
        }
        return null;
    }
}
