package com.opspilot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 异步任务线程池配置
 *
 * <p>为发版部署等异步任务提供统一的线程池管理，
 * 替代原有的 {@code new Thread()} 方式，避免线程泄漏和资源耗尽。</p>
 *
 * @author opspilot-team
 * @since 2026-04-13
 */
@Configuration
public class AsyncConfig {

    /**
     * 部署任务专用线程池
     *
     * <p>核心线程数 5，最大线程数 10，队列容量 100。
     * 当任务超过最大线程数时，新任务进入队列等待；队列满时由调用线程执行。
     * 线程空闲 60 秒后回收。</p>
     *
     * @return 部署任务线程池
     */
    @Bean("deployTaskExecutor")
    public TaskExecutor deployTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // 核心线程数：常驻线程数量
        executor.setCorePoolSize(5);
        // 最大线程数：并发上限
        executor.setMaxPoolSize(10);
        // 队列容量：等待执行的任务数
        executor.setQueueCapacity(100);
        // 线程名称前缀：便于日志排查
        executor.setThreadNamePrefix("deploy-task-");
        // 线程空闲存活时间（秒）
        executor.setKeepAliveSeconds(60);
        // 拒绝策略：队列满时由调用线程执行
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 应用关闭时等待任务完成
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 最多等待 60 秒
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
}
