package com.hou.shortlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步线程池 + 定时任务开关。
 * 访问日志不用 @Async 默认线程池的原因：默认的 SimpleAsyncTaskExecutor 每个任务 new 一个线程、
 * 没有上限，高并发下会打爆内存。显式声明线程池才能讲清"核心线程/队列/拒绝策略"。
 */
@EnableAsync
@EnableScheduling
@Configuration
public class AsyncConfig {

    @Bean("accessLogExecutor")
    public ThreadPoolTaskExecutor accessLogExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("access-log-");
        executor.setCorePoolSize(2);        // 常驻 2 个线程够写日志
        executor.setMaxPoolSize(4);         // 峰值最多扩到 4
        executor.setQueueCapacity(2000);    // 线程占满先进队列，最多积压 2000 条
        executor.setKeepAliveSeconds(60);
        // 队列也满了就让"调用线程"（跳转线程）自己写：宁可慢一点，也不丢日志
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 应用关闭时把队列里剩余日志写完再退出，最多等 5 秒
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        return executor;
    }
}
