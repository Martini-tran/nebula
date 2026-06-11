package com.nebula.blog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置。
 * <p>当前仅用于博客文章 Markdown 批量导入（{@code blogImportExecutor}），
 * 使导入请求快速返回、后台逐文件处理。
 */
@Configuration
@EnableAsync
public class AsyncConfig {

    /**
     * 博客导入线程池。
     * <p>队列满后采用 CallerRuns 策略：由提交线程兜底执行，避免任务丢失。
     */
    @Bean(name = "blogImportExecutor")
    public ThreadPoolTaskExecutor blogImportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("blog-import-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
