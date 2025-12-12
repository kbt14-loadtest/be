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
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

@Slf4j
@Configuration
public class RedisStoreFactory {

    @Value("${spring.data.redis.host:localhost}")
    private String redisHost;

    @Value("${spring.data.redis.port:6379}")
    private int redisPort;

    @Value("${spring.data.redis.password:}")
    private String redisPassword;

    @Value("${app.redis.cluster.nodes:}")
    private List<String> clusterNodes;

    private final Environment environment;

    public RedisStoreFactory(Environment environment) {
        this.environment = environment;
    }

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient() {
        Config config = new Config();

        // ✅ dev 프로필에서는 싱글 Redis 사용
        if (environment.acceptsProfiles(Profiles.of("dev"))) {
            String address = "redis://" + redisHost + ":" + redisPort;
            log.info("[Redis] Using SINGLE server mode for dev: {}", address);

            var single = config.useSingleServer()
                .setAddress(address);

            if (redisPassword != null && !redisPassword.isEmpty()) {
                single.setPassword(redisPassword);
            }
        } else {
            // 필요하면 다른 프로필에서만 클러스터 사용
            log.info("[Redis] Using CLUSTER mode with nodes: {}", clusterNodes);

            var cluster = config.useClusterServers()
                .addNodeAddress(
                    clusterNodes.stream()
                        .map(node -> node.startsWith("redis://") ? node : "redis://" + node)
                        .toArray(String[]::new)
                );

            if (redisPassword != null && !redisPassword.isEmpty()) {
                cluster.setPassword(redisPassword);
            }
        }

        return Redisson.create(config);
    }

    @Bean
    public RedissonStoreFactory redissonStoreFactory(RedissonClient redissonClient) {
        log.info("Creating RedissonStoreFactory for multi-instance Socket.IO support");
        return new RedissonStoreFactory(redissonClient);
    }
}
