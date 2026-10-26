package com.bili.translator.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@EnableAsync
@Configuration
public class AsyncConfig {

    @Bean
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("bili-async-");
        // 关闭时先停止接收新任务，给在跑的任务 30 秒收尾；超时才强制中断。
        // 不配的话 shutdownNow 直接中断线程，正在跑的 ffmpeg 只能靠「重启后标 failed」兜底。
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        // 这里不需要（也不应该）手动 initialize()：ThreadPoolTaskExecutor 实现了
        // InitializingBean，容器会调 afterPropertiesSet()；手动再调一次会多造一个线程池对象
        return executor;
    }
}
