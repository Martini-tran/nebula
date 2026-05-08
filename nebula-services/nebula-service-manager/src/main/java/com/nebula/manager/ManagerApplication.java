package com.nebula.manager;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 系统管理模块应用启动类
 * 配置Spring Boot应用程序的基本设置和MyBatis映射器扫描路径
 *
 * @author nebula
 */
@SpringBootApplication
@MapperScan("com.nebula.manager.mapper")
public class ManagerApplication {

    /**
     * 应用程序主入口方法
     * 启动Spring Boot应用程序
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ManagerApplication.class, args);
    }
}
