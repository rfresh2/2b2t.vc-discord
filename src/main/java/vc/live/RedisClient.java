package vc.live;

import org.redisson.Redisson;
import org.redisson.api.RBoundedBlockingQueue;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

public class RedisClient {
    private RedissonClient redissonClient;

    public RedisClient(final String redisURL, final String redisUsername, final String redisPassword) {
        this.redissonClient = buildRedisClient(redisURL, redisUsername, redisPassword);
    }

    public <T> RBoundedBlockingQueue<T> getQueue(final String queueName) {
        return redissonClient.getBoundedBlockingQueue(queueName);
    }

    public RedissonClient buildRedisClient(final String redisURL, final String redisUsername, final String redisPassword) {
        Config config = new Config();
        config.setNettyThreads(1)
            .setThreads(1)
            .useSingleServer()
            .setAddress(redisURL)
            .setUsername(redisUsername)
            .setPassword(redisPassword)
            .setConnectionPoolSize(1)
            .setConnectionMinimumIdleSize(1);
        return Redisson.create(config);
    }
}
