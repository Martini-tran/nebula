package com.nebula.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.BlockAttackInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis-Plus 核心配置
 * - 分页插件（MySQL）
 * - 防全表 update / delete（无 WHERE 时拦截）
 *
 * @author nebula
 */
@Configuration
public class MybatisPlusConfig {

    /**
     * 配置MyBatis-Plus拦截器
     * 创建并配置MyBatis-Plus的核心拦截器，包含分页和安全防护功能
     *
     * @return 配置好的MyBatis-Plus拦截器实例
     */
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        // 创建MyBatis-Plus拦截器实例
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

        // 创建MySQL分页插件实例
        PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
        // 设置分页查询的最大限制数量，防止一次性查询过多数据
        pagination.setMaxLimit(500L);
        // 设置溢出时不进行分页，即当查询数量超过maxLimit时直接抛出异常
        pagination.setOverflow(false);
        // 将分页插件添加到拦截器链中
        interceptor.addInnerInterceptor(pagination);

        // 添加防攻击插件，防止全表更新或删除操作（没有WHERE条件的操作）
        interceptor.addInnerInterceptor(new BlockAttackInnerInterceptor());

        // 返回配置完成的拦截器实例
        return interceptor;
    }
}
