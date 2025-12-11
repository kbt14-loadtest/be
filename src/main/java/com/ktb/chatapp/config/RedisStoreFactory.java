package com.ktb.chatapp.config;

import com.corundumstudio.socketio.store.RedissonStoreFactory;
import java.util.List;
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

    @Value("${app.redis.cluster.nodes}")
    private List<String> clusterNodes;

    @Bean
    public RedissonClient redissonClient() {
        Config config = new Config();
        var cluster = config.useClusterServers()
            .addNodeAddress(
                clusterNodes.stream()
                    .map(node -> node.startsWith("redis://") ? node : "redis://" + node)
                    .toArray(String[]::new)
            );
        // 비밀번호 있으면 여기
        if (redisPassword != null && !redisPassword.isEmpty()) {
            cluster.setPassword(redisPassword);
        }
        return Redisson.create(config);
    }

    @Bean
    public RedissonStoreFactory redissonStoreFactory(RedissonClient redissonClient) {
        log.info("Creating RedissonStoreFactory for multi-instance Socket.IO support");
        return new RedissonStoreFactory(redissonClient);
    }
}
