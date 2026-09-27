package com.hou.shortlink.common;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 基于 Redis + Lua 的固定窗口限流器。
 * 为什么用 Lua：INCR 和 EXPIRE 是两条命令，分开执行时进程若在中间挂掉，
 * 计数器就永远没有过期时间，用户会被永久限流。Lua 脚本在 Redis 里原子执行，两步绑定生效。
 *
 * 固定窗口的已知问题：窗口切换瞬间可能放行 2 倍流量 → 面试可延伸滑动窗口 / 令牌桶
 */
@Component
@RequiredArgsConstructor
public class RedisRateLimiter {

    private static final DefaultRedisScript<Long> INCR_AND_EXPIRE = new DefaultRedisScript<>(
            "local v = redis.call('INCR', KEYS[1]) " +
            "if v == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end " +
            "return v", Long.class);

    private final StringRedisTemplate redisTemplate;

    /** 尝试获取一次配额：窗口内累计次数不超过 limit 返回 true */
    public boolean tryAcquire(String key, int limit, int windowSeconds) {
        Long count = redisTemplate.execute(INCR_AND_EXPIRE, List.of(key), String.valueOf(windowSeconds));
        return count != null && count <= limit;
    }
}
