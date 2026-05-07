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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;

/**
 * Nebula 对外验证码门面实现
 * 对 AJ-Captcha 的 get/check 调用透传，附加 Nebula 自己的频控、verifyToken 一次性化策略
 * 所有可调参数从 SysConfigService（即 sys_config 表）实时读取，admin 改完即时生效
 *
 * @author nebula
 */
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {
    private static final String AJ_CAPTCHA_SECOND_KEY_PREFIX = "RUNNING:CAPTCHA:second-";
    private static final long AJ_CAPTCHA_SECOND_EXPIRES_SECONDS = 180L;
    private static final String CAPTCHA_SECRET_KEY_PREFIX = "captcha:secret:";

    /**
     * 同 IP 重复生成节流的 redis key 前缀
     * 用于限制同一IP在一定时间内不能频繁请求验证码
     */
    private static final String COOLDOWN_KEY_PREFIX = "captcha:cooldown:";
    private static final long COOLDOWN_MAX_REQUESTS = 5L;
    
    /**
     * verifyToken 单次消费的 redis key 前缀
     * 用于确保验证码令牌只能被使用一次
     */
    private static final String VERIFY_KEY_PREFIX = "captcha:verify:";

    /**
     * AJ-Captcha服务实例
     * 用于实际的验证码生成和校验操作
     */
    private final com.anji.captcha.service.CaptchaService ajCaptchaService;
    
    /**
     * 系统配置服务实例
     * 用于动态获取验证码相关的配置参数
     */
    private final SysConfigService configService;
    
    /**
     * Redis工具实例
     * 用于存储和验证验证码相关数据
     */
    private final RedisUtils redis;

    /**
     * 构造函数
     * 初始化验证码服务实现类
     * 
     * @param ajCaptchaService AJ-Captcha服务实例
     * @param configService 系统配置服务实例
     * @param redis Redis 工具实例
     */
    public CaptchaServiceImpl(com.anji.captcha.service.CaptchaService ajCaptchaService,
                              SysConfigService configService,
                              RedisUtils redis) {
        this.ajCaptchaService = ajCaptchaService;
        this.configService = configService;
        this.redis = redis;
    }

    /**
     * 获取验证码
     * 生成验证码并应用IP频率控制策略
     * 
     * @param request 验证码获取请求对象
     * @param clientIp 客户端IP地址
     * @return 包含验证码信息的Map
     */
    @Override
    public Map<String, Object> get(CaptchaGetRequest request, String clientIp) {
        // 解析并验证验证码类型
        String type = resolveType(request.getCaptchaType());
        // 确保指定的验证码类型已启用
        ensureTypeEnabled(type);
        // 应用IP冷却策略，防止频繁请求
        String cooldownSubject = resolveCooldownSubject(request.getClientUid(), clientIp);
        checkCooldown(cooldownSubject);

        // 构建AJ-Captcha所需的请求对象
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaType(type);
        vo.setClientUid(request.getClientUid());
        vo.setBrowserInfo(request.getBrowserInfo());

        // 调用AJ-Captcha服务获取验证码
        ResponseModel resp = ajCaptchaService.get(vo);
        if (resp == null || !"0000".equals(resp.getRepCode())) {
            log.warn("[captcha] generate fail: {}", resp == null ? "null" : resp.getRepMsg());
            throw new BizException(CaptchaResultCode.GENERATE_FAIL,
                    resp == null ? CaptchaResultCode.GENERATE_FAIL.getMessage() : resp.getRepMsg());
        }

        cacheSecretKey(resp.getRepData());

        applyCooldown(cooldownSubject);
        return wrap(resp);
    }

    /**
     * 校验验证码
     * 验证用户提交的验证码是否正确，并生成一次性验证令牌
     * 
     * @param request 验证码校验请求对象
     * @param clientIp 客户端IP地址
     * @return 包含验证结果的Map
     */
    @Override
    public Map<String, Object> check(CaptchaCheckRequest request, String clientIp) {
        // 验证请求参数的有效性
        if (request == null || request.getToken() == null || request.getCaptchaType() == null) {
            throw new BizException(CaptchaResultCode.CHECK_FAIL);
        }
        String type = resolveType(request.getCaptchaType());

        // 构建AJ-Captcha校验请求对象
        CaptchaVO vo = new CaptchaVO();
        vo.setCaptchaType(type);
        vo.setToken(request.getToken());
        vo.setPointJson(request.getPointJson());

        // 调用AJ-Captcha服务校验验证码
        ResponseModel resp = ajCaptchaService.check(vo);
        if (resp == null || !"0000".equals(resp.getRepCode())) {
            throw new BizException(CaptchaResultCode.CHECK_FAIL,
                    resp == null ? CaptchaResultCode.CHECK_FAIL.getMessage() : resp.getRepMsg());
        }

        // 从响应中提取AJ-Captcha生成的一次性验证串
        Object data = resp.getRepData();
        String ajVerification = extractVerification(data);
        if (ajVerification == null) {
            ajVerification = buildFallbackVerification(request);
        }
        if (ajVerification == null) {
            throw new BizException(CaptchaResultCode.CHECK_FAIL);
        }

        // 从系统配置获取验证令牌超时时间
        long ttlSeconds = configService.getInt(CaptchaConfigKeys.VERIFY_TIMEOUT_SECONDS,
                CaptchaConfigKeys.DEFAULT_VERIFY_TIMEOUT_SECONDS);
        // 将验证令牌存储到Redis，设置过期时间，确保只能使用一次
        redis.set(VERIFY_KEY_PREFIX + type + ":" + ajVerification, "1", Duration.ofSeconds(ttlSeconds));

        // 构建返回结果
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("verifyToken", ajVerification);
        out.put("captchaType", type);
        out.put("expiresInSeconds", ttlSeconds);
        return out;
    }

    /**
     * 断言验证码已被验证
     * 验证一次性验证令牌是否有效且未被使用
     * 
     * @param captchaType 验证码类型
     * @param verifyToken 验证令牌
     */
    @Override
    public void assertVerified(String captchaType, String verifyToken) {
        // 验证令牌不能为空
        if (verifyToken == null || verifyToken.isBlank()) {
            throw new BizException(CaptchaResultCode.VERIFY_TOKEN_INVALID);
        }
        String type = resolveType(captchaType);
        String key = VERIFY_KEY_PREFIX + type + ":" + verifyToken;
        // 从Redis获取并删除验证令牌，确保只能使用一次
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

    // ===== 辅助方法 =====

    /**
     * 解析验证码类型
     * 根据请求的类型或系统默认配置确定最终的验证码类型
     * 
     * @param requested 请求的验证码类型
     * @return 解析后的验证码类型
     */
    private String resolveType(String requested) {
        String def = configService.getOrDefault(CaptchaConfigKeys.TYPE_DEFAULT, CaptchaConfigKeys.DEFAULT_TYPE);
        String type = requested != null && !requested.isBlank() ? requested.trim() : def;
        if (!"blockPuzzle".equals(type) && !"clickWord".equals(type)) {
            throw new BizException(CaptchaResultCode.TYPE_UNKNOWN);
        }
        return type;
    }

    /**
     * 确保验证码类型已启用
     * 检查系统配置中是否启用了指定的验证码类型
     * 
     * @param type 要检查的验证码类型
     */
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

    private void checkCooldown(String subject) {
        if (subject == null) {
            return;
        }
        String counter = redis.get(COOLDOWN_KEY_PREFIX + subject);
        if (counter != null && Long.parseLong(counter) >= COOLDOWN_MAX_REQUESTS) {
            throw new BizException(CaptchaResultCode.REPEAT_TOO_FAST);
        }
    }

    private void applyCooldown(String subject) {
        if (subject == null) {
            return;
        }
        int interval = configService.getInt(CaptchaConfigKeys.REPEAT_INTERVAL_SECONDS,
                CaptchaConfigKeys.DEFAULT_REPEAT_INTERVAL_SECONDS);
        if (interval <= 0) {
            return;
        }
        String key = redis.key(COOLDOWN_KEY_PREFIX + subject);
        Long counter = redis.template().opsForValue().increment(key);
        if (counter == null) {
            throw new BizException(CaptchaResultCode.REPEAT_TOO_FAST);
        }
        if (counter == 1L) {
            redis.template().expire(key, Duration.ofSeconds(interval));
        }
        if (counter > COOLDOWN_MAX_REQUESTS) {
            throw new BizException(CaptchaResultCode.REPEAT_TOO_FAST);
        }
    }

    private String resolveCooldownSubject(String clientUid, String clientIp) {
        if (clientUid != null && !clientUid.isBlank()) {
            return "uid:" + clientUid.trim();
        }
        if (clientIp != null && !clientIp.isBlank()) {
            return "ip:" + clientIp.trim();
        }
        return null;
    }

    private Map<String, Object> wrap(ResponseModel resp) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("repCode", resp.getRepCode());
        out.put("repMsg", resp.getRepMsg());
        out.put("repData", resp.getRepData());
        return out;
    }

    /**
     * 从响应数据中提取验证串
     * 从AJ-Captcha返回的数据中提取一次性验证串
     * 
     * @param data 响应数据
     * @return 提取的验证串
     */
    private String extractVerification(Object data) {
        if (data instanceof CharSequence text) {
            String value = text.toString().trim();
            return value.isBlank() ? null : value;
        }
        if (data instanceof Map<?, ?> map) {
            Object v = map.get("captchaVerification");
            return v == null ? null : v.toString();
        }
        if (data instanceof CaptchaVO vo) {
            return vo.getCaptchaVerification();
        }
        return null;
    }

    private String decryptPointJson(String pointJson, String secretKey) throws Exception {
        if (pointJson == null || pointJson.isBlank() || secretKey == null || secretKey.isBlank()) {
            return pointJson;
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec keySpec =
                new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        byte[] decrypted = cipher.doFinal(Base64.getDecoder().decode(pointJson));
        return new String(decrypted, StandardCharsets.UTF_8);
    }

    private String encryptPointJson(String pointJson, String secretKey) throws Exception {
        if (pointJson == null || pointJson.isBlank() || secretKey == null || secretKey.isBlank()) {
            return null;
        }
        Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
        SecretKeySpec keySpec =
                new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "AES");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        byte[] encrypted = cipher.doFinal(pointJson.getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(encrypted);
    }

    private String buildFallbackVerification(CaptchaCheckRequest request) {
        try {
            String secretKey = redis.get(CAPTCHA_SECRET_KEY_PREFIX + request.getToken());
            String decryptedPoint = decryptPointJson(request.getPointJson(), secretKey);
            if (secretKey == null || secretKey.isBlank()
                    || decryptedPoint == null || decryptedPoint.isBlank()) {
                return null;
            }
            String verification =
                    encryptPointJson(request.getToken() + "---" + decryptedPoint, secretKey);
            if (verification == null || verification.isBlank()) {
                return null;
            }
            redis.template().opsForValue().set(
                    AJ_CAPTCHA_SECOND_KEY_PREFIX + verification,
                    request.getToken(),
                    Duration.ofSeconds(AJ_CAPTCHA_SECOND_EXPIRES_SECONDS));
            return verification;
        } catch (Exception ex) {
            log.warn("[captcha] fallback verification build fail token={}", request.getToken(), ex);
            return null;
        }
    }

    private void cacheSecretKey(Object data) {
        try {
            if (data instanceof Map<?, ?> map) {
                Object token = map.get("token");
                Object secretKey = map.get("secretKey");
                if (token != null && secretKey != null) {
                    redis.set(
                            CAPTCHA_SECRET_KEY_PREFIX + token,
                            secretKey.toString(),
                            Duration.ofSeconds(AJ_CAPTCHA_SECOND_EXPIRES_SECONDS));
                }
                return;
            }
            if (data instanceof CaptchaVO vo) {
                if (vo.getToken() != null && vo.getSecretKey() != null) {
                    redis.set(
                            CAPTCHA_SECRET_KEY_PREFIX + vo.getToken(),
                            vo.getSecretKey(),
                            Duration.ofSeconds(AJ_CAPTCHA_SECOND_EXPIRES_SECONDS));
                }
            }
        } catch (Exception ex) {
            log.warn("[captcha] cache secret key fail, dataType={}",
                    data == null ? null : data.getClass().getName(), ex);
        }
    }
}
