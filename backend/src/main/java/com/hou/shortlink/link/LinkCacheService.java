package com.hou.shortlink.link;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 短链缓存 + Redis 计数。
 * 跳转链路是全系统 QPS 最高的地方，用 Cache Aside（旁路缓存）模式：
 *   读：先查 Redis → 命中直接返回 → 未命中查 MySQL → 回填缓存
 *   写（更新/删除）：先更新 MySQL → 再删缓存（"先库后缓存"是标准顺序）
 *
 * 两个经典问题都在这里处理：
 *   缓存穿透：不存在的短码也缓存一个空标记（60 秒），恶意刷短码不会每次都打穿到 MySQL
 *   缓存雪崩：过期时间 = 30 分钟 + 随机 0~5 分钟，避免大批 key 同一秒集体过期
 */
@Service
@RequiredArgsConstructor
public class LinkCacheService {

    /** 短链映射缓存 key 前缀，完整 key 形如 link:code:Ab3xK */
    public static final String KEY_LINK_CODE = "link:code:";
    /** pv 计数 key 前缀，完整 key 形如 pv:link:123 */
    public static final String KEY_PV = "pv:link:";
    /** pv 脏集合：本次回写周期内产生过访问的短链 id，定时任务只处理脏的 */
    public static final String KEY_PV_DIRTY = "pv:dirty";

    /** 空值标记：表示"这个短码库里也不存在"，防止穿透 */
    private static final String EMPTY_MARKER = "NULL";
    private static final Duration LINK_TTL = Duration.ofMinutes(30);
    private static final Duration EMPTY_TTL = Duration.ofSeconds(60);

    private final RedisTemplate<String, Object> redisTemplate;  // link:code 对象缓存，JSON 序列化
    private final StringRedisTemplate stringRedisTemplate;      // pv 计数是纯字符串，和定时任务读写格式保持一致
    private final ShortLinkMapper shortLinkMapper;

    /** 跳转入口：先缓存后数据库 */
    public ShortLink findActiveByCode(String shortCode) {
        String key = KEY_LINK_CODE + shortCode;
        Object cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (cached instanceof ShortLink link) {
                // 缓存里的数据放久了可能"刚过期"，返回前仍要做过期判断
                return isExpired(link) ? null : link;
            }
            return null;  // 空值标记命中：这个短码确实不存在
        }
        // 缓存未命中，查库（只查启用中的，禁用视同不存在）
        ShortLink link = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, shortCode)
                .eq(ShortLink::getStatus, 1));
        if (link == null || isExpired(link)) {
            // 查不到也缓存空值（短 TTL），同一不存在的短码 60 秒内只打一次库
            redisTemplate.opsForValue().set(key, EMPTY_MARKER, EMPTY_TTL);
            return null;
        }
        redisTemplate.opsForValue().set(key, link, randomTtl(LINK_TTL));
        return link;
    }

    /** 更新/删除短链后调用：先更新库，再删缓存，下次跳转重新从库加载 */
    public void evict(String shortCode) {
        redisTemplate.delete(KEY_LINK_CODE + shortCode);
    }

    /** pv 在 Redis 里原子累加（INCR），并把短链 id 记入脏集合，供定时任务回写数据库 */
    public long incrPv(Long linkId) {
        Long v = stringRedisTemplate.opsForValue().increment(KEY_PV + linkId);
        stringRedisTemplate.opsForSet().add(KEY_PV_DIRTY, String.valueOf(linkId));
        return v == null ? 0 : v;
    }

    /** 还没回写到数据库的 pv 增量（展示时叠加到库里读出的 pv 上，最终一致） */
    public long pendingPv(Long linkId) {
        String v = stringRedisTemplate.opsForValue().get(KEY_PV + linkId);
        return v == null ? 0 : Long.parseLong(v);
    }

    private static boolean isExpired(ShortLink link) {
        return link.getExpireTime() != null && link.getExpireTime().isBefore(LocalDateTime.now());
    }

    /** TTL 加随机抖动，防止大批 key 同一时刻过期、瞬间全打到数据库（雪崩） */
    private static Duration randomTtl(Duration base) {
        return base.plusSeconds(ThreadLocalRandom.current().nextLong(300));
    }
}
