package com.nebula.common.captcha.constant;

/**
 * sys_config 中验证码相关的配置 key 与默认值
 *
 * @author nebula
 */
public final class CaptchaConfigKeys {

    private CaptchaConfigKeys() {
    }

    /**
     * 生成验证码后过期时间（秒），默认 120
     */
    public static final String EXPIRE_SECONDS = "captcha.expire.seconds";
    public static final int DEFAULT_EXPIRE_SECONDS = 120;

    /**
     * 校验通过后的 verifyToken 有效期（秒），默认 300
     */
    public static final String VERIFY_TIMEOUT_SECONDS = "captcha.verify.timeout.seconds";
    public static final int DEFAULT_VERIFY_TIMEOUT_SECONDS = 300;

    /**
     * 同一客户端两次生成请求最小间隔（秒），默认 5
     */
    public static final String REPEAT_INTERVAL_SECONDS = "captcha.repeat.interval.seconds";
    public static final int DEFAULT_REPEAT_INTERVAL_SECONDS = 5;

    /**
     * 默认验证码类型：blockPuzzle / clickWord，默认 blockPuzzle
     */
    public static final String TYPE_DEFAULT = "captcha.type.default";
    public static final String DEFAULT_TYPE = "blockPuzzle";

    /**
     * 是否启用滑块验证码，默认 true
     */
    public static final String TYPE_SLIDER_ENABLED = "captcha.type.slider.enabled";
    public static final boolean DEFAULT_SLIDER_ENABLED = true;

    /**
     * 是否启用文字点选验证码，默认 true
     */
    public static final String TYPE_CLICK_ENABLED = "captcha.type.click.enabled";
    public static final boolean DEFAULT_CLICK_ENABLED = true;

    /**
     * 验证码图片水印文字，默认 Nebula
     */
    public static final String IMAGE_WATERMARK = "captcha.image.water.mark";
    public static final String DEFAULT_WATERMARK = "Nebula";

    /**
     * 验证码字体类型，默认 宋体
     */
    public static final String IMAGE_FONT_TYPE = "captcha.image.font.type";
    public static final String DEFAULT_FONT_TYPE = "宋体";

    /**
     * 滑块校验像素容忍度，默认 5
     */
    public static final String IMAGE_TOLERANT = "captcha.image.aj.tolerant";
    public static final int DEFAULT_TOLERANT = 5;
}
