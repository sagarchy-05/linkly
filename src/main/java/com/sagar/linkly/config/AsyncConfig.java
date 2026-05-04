package com.sagar.linkly.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    @Bean(name = "clickPublisherExecutor")
    public Executor clickPublisherExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(4);
        exec.setMaxPoolSize(16);
        exec.setQueueCapacity(2000);
        exec.setThreadNamePrefix("click-pub-");
        exec.setRejectedExecutionHandler((r, e) -> {
            // Drop on overload — analytics is best-effort
        });
        exec.initialize();
        return exec;
    }
}