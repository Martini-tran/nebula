package com.nebula.common.captcha;

import com.anji.captcha.service.impl.CaptchaServiceFactory;
import com.nebula.common.captcha.cache.RedisCaptchaCacheService;
import com.nebula.common.captcha.constant.CaptchaConfigKeys;
import com.nebula.common.captcha.controller.CaptchaController;
import com.nebula.common.captcha.service.CaptchaService;
import com.nebula.common.captcha.service.impl.CaptchaServiceImpl;
import com.nebula.common.config.ConfigAutoConfiguration;
import com.nebula.common.config.service.SysConfigService;
import com.nebula.common.redis.util.RedisUtils;
import java.util.Properties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

/**
 * 验证码模块自动装配
 *
 * @author nebula
 */
@AutoConfiguration(after = ConfigAutoConfiguration.class)
@ConditionalOnClass(name = "com.anji.captcha.service.CaptchaService")
@Import(CaptchaController.class)
public class CaptchaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(com.anji.captcha.service.CaptchaService.class)
    public com.anji.captcha.service.CaptchaService ajCaptchaService(SysConfigService configService,
                                                                     RedisUtils redisUtils) {
        // SPI 通过无参构造创建 RedisCaptchaCacheService，需在 AJ-Captcha 工厂初始化前完成 RedisUtils 绑定
        RedisCaptchaCacheService.bind(redisUtils);

        Properties config = new Properties();
        // 走我们自己的 Redis 实现
        config.setProperty("captcha.cacheType", "redis");
        config.setProperty("captcha.cache.type", "redis");

        // 文字 / 图形选项
        config.setProperty("captcha.water.mark",
                configService.getOrDefault(CaptchaConfigKeys.IMAGE_WATERMARK, CaptchaConfigKeys.DEFAULT_WATERMARK));
        config.setProperty("captcha.font.type",
                configService.getOrDefault(CaptchaConfigKeys.IMAGE_FONT_TYPE, CaptchaConfigKeys.DEFAULT_FONT_TYPE));
        config.setProperty("captcha.aj.captcha.tolerant",
                String.valueOf(configService.getInt(CaptchaConfigKeys.IMAGE_TOLERANT, CaptchaConfigKeys.DEFAULT_TOLERANT)));
        // 关闭 AJ-Captcha 自带的历史数据清理调度（我们靠 Redis TTL 即可）
        config.setProperty("captcha.history.data.clear.enable", "false");

        return CaptchaServiceFactory.getInstance(config);
    }

    @Bean
    @ConditionalOnMissingBean
    public CaptchaService nebulaCaptchaService(com.anji.captcha.service.CaptchaService ajCaptchaService,
                                                SysConfigService configService,
                                                RedisUtils redisUtils) {
        return new CaptchaServiceImpl(ajCaptchaService, configService, redisUtils);
    }
}
