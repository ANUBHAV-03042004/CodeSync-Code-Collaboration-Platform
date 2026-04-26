package com.executionservice.codesync.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.socket.config.annotation.*;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class ExecutionConfig {

    /**
     * Dedicated thread pool for sandbox executions.
     * Each job blocks for up to 10s — isolate from the HTTP thread pool.
     */
    @Bean(name = "sandboxExecutor")
    public Executor sandboxExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("sandbox-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }
}

@Configuration
@EnableWebSocketMessageBroker
class ExecutionWebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue");       // in-memory broker for user queues
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/execution")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}