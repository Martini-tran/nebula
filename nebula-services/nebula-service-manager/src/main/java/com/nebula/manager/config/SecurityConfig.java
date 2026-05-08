package com.nebula.manager.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Spring Security安全配置类
 * 提供密码编码器等相关安全配置
 *
 * @author nebula
 */
@Configuration
public class SecurityConfig {

    /**
     * 创建密码编码器Bean
     * 使用BCrypt算法对密码进行哈希加密
     * BCrypt是一种安全的密码哈希算法，具有良好的抗彩虹表攻击能力
     *
     * @return PasswordEncoder密码编码器实例
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
