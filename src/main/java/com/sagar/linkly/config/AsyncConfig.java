package com.sagar.linkly.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${linkly.async.core-pool-size}")
    private int corePoolSize;

    @Value("${linkly.async.max-pool-size}")
    private int maxPoolSize;

    @Value("${linkly.async.queue-capacity}")
    private int queueCapacity;

    @Bean(name = "clickPublisherExecutor")
    public Executor clickPublisherExecutor() {
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        exec.setCorePoolSize(corePoolSize);
        exec.setMaxPoolSize(maxPoolSize);
        exec.setQueueCapacity(queueCapacity);
        exec.setThreadNamePrefix("click-pub-");
        exec.setRejectedExecutionHandler((r, e) -> {
            // Drop on overload — analytics is best-effort
        });
        exec.initialize();
        return exec;
    }
}