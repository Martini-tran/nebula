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
 * Nebula 对外验证码门面实现
 * 对 AJ-Captcha 的 get/check 调用透传，附加 Nebula 自己的频控、verifyToken 一次性化策略
 * 所有可调参数从 SysConfigService（即 sys_config 表）实时读取，admin 改完即时生效
 *
 * @author nebula
 */
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {

    /**
     * 同 IP 重复生成节流的 redis key 前缀
     * 用于限制同一IP在一定时间内不能频繁请求验证码
     */
    private static final String COOLDOWN_KEY_PREFIX = "nebula:captcha:cooldown:";
    
    /**
     * verifyToken 单次消费的 redis key 前缀
     * 用于确保验证码令牌只能被使用一次
     */
    private static final String VERIFY_KEY_PREFIX = "nebula:captcha:verify:";

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
     * @param redis Redis工具实例
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
        applyCooldown(clientIp);

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

    /**
     * 应用IP冷却策略
     * 防止同一IP频繁请求验证码
     * 
     * @param clientIp 客户端IP地址
     */
    private void applyCooldown(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            return;
        }
        // 从系统配置获取重复请求间隔时间
        int interval = configService.getInt(CaptchaConfigKeys.REPEAT_INTERVAL_SECONDS,
                CaptchaConfigKeys.DEFAULT_REPEAT_INTERVAL_SECONDS);
        if (interval <= 0) {
            return;
        }
        // 在Redis中设置冷却标志，防止同一IP在规定时间内重复请求
        Boolean ok = redis.setIfAbsent(COOLDOWN_KEY_PREFIX + clientIp, "1", Duration.ofSeconds(interval));
        if (!Boolean.TRUE.equals(ok)) {
            throw new BizException(CaptchaResultCode.REPEAT_TOO_FAST);
        }
    }

    /**
     * 包装响应模型
     * 将AJ-Captcha的响应模型转换为Map格式
     * 
     * @param resp AJ-Captcha响应模型
     * @return 包装后的Map格式响应
     */
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
