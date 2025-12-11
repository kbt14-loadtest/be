package com.ktb.chatapp.config;

import com.corundumstudio.socketio.store.RedissonStoreFactory;
import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Redis Store Factory configuration for multi-instance Socket.IO deployment.
 * Uses Redisson client for Redis Pub/Sub communication across instances.
 */
@Slf4j
@Configuration
public class RedisStoreFactory {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        String address = String.format("redis://%s:%d", redisHost, redisPort);

        config.useSingleServer()
                .setAddress(address)
                .setConnectionPoolSize(64)
                .setConnectionMinimumIdleSize(10)
                .setSubscriptionConnectionPoolSize(50)
                .setSubscriptionConnectionMinimumIdleSize(1);

        if (redisPassword != null && !redisPassword.isEmpty()) {
            config.useSingleServer().setPassword(redisPassword);
        }

        log.info("Redisson client configured for Socket.IO with address: {}", address);
        return Redisson.create(config);
    }

    @Bean
    public RedissonStoreFactory redissonStoreFactory(RedissonClient redissonClient) {
        log.info("Creating RedissonStoreFactory for multi-instance Socket.IO support");
        return new RedissonStoreFactory(redissonClient);
    }
}
