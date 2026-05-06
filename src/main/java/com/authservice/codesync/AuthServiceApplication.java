package com.authservice.codesync;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.session.data.redis.config.annotation.web.http.EnableRedisHttpSession;

@SpringBootApplication
@EnableDiscoveryClient
@EnableAsync
@EnableScheduling
// Stores HTTP sessions (including OAuth2 auth-request state) in Redis.
// This is what makes the OAuth2 flow work across multiple pods and gateway hops.
// maxInactiveIntervalInSeconds=300 → session expires in 5 min (enough for the OAuth round-trip).
@EnableRedisHttpSession(maxInactiveIntervalInSeconds = 300)
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
