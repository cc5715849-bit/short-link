package com.hou.shortlink.stat;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.hou.shortlink.link.LinkCacheService;
import com.hou.shortlink.link.ShortLink;
import com.hou.shortlink.link.ShortLinkMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.Set;

/**
 * 统计定时任务，两件事：
 *
 * 1. flushPv：跳转时 pv 只在 Redis 里 INCR（快），这里定期把增量回写 MySQL。
 *    用 GETSET 原子地"取出并清零"，期间新产生的访问会重新计数、重新打脏标记，不丢。
 *    效果：列表/详情看到的 pv 是"最终一致"，不是强一致（面试点）
 *
 * 2. summarizeYesterday：每天凌晨把昨天的 access_log 汇总进 daily_stat，
 *    统计接口只查汇总表，不再扫日志大表。
 *    汇总的 upsert SQL 幂等，任务重跑/补数都安全
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StatTask {

    private final StringRedisTemplate redisTemplate;
    private final ShortLinkMapper shortLinkMapper;
    private final DailyStatMapper dailyStatMapper;

    /** 默认 5 分钟一次；周期可在 yml 里用 stat.pv-flush-ms 覆盖（演示时可调小） */
    @Scheduled(fixedDelayString = "${stat.pv-flush-ms:300000}", initialDelayString = "${stat.pv-flush-ms:300000}")
    public void flushPv() {
        Set<String> ids = redisTemplate.opsForSet().members(LinkCacheService.KEY_PV_DIRTY);
        if (ids == null || ids.isEmpty()) {
            return;
        }
        int flushed = 0;
        for (String id : ids) {
            // 先移除脏标记、再 GETSET 取走增量：两步之间新产生的访问会重新打标记，不会丢
            redisTemplate.opsForSet().remove(LinkCacheService.KEY_PV_DIRTY, id);
            String v = redisTemplate.opsForValue().getAndSet(LinkCacheService.KEY_PV + id, "0");
            long delta = v == null ? 0 : Long.parseLong(v);
            if (delta > 0) {
                shortLinkMapper.update(null, new LambdaUpdateWrapper<ShortLink>()
                        .eq(ShortLink::getId, Long.parseLong(id))
                        .setSql("pv = pv + {0}", delta));
                flushed++;
            }
        }
        log.info("定时任务：pv 增量回写完成，本次更新 {} 条短链", flushed);
    }

    /** 默认每天 01:00 汇总昨天；cron 可在 yml 里用 stat.daily-cron 覆盖（演示时可改成每 30 秒） */
    @Scheduled(cron = "${stat.daily-cron:0 0 1 * * ?}")
    public void summarizeYesterday() {
        summarizeDay(LocalDate.now().minusDays(1));
    }

    /** 汇总指定日期的访问日志（包级可见，JUnit 可直接调用验证） */
    public int summarizeDay(LocalDate date) {
        int n = dailyStatMapper.summarizeRange(date.atStartOfDay(), date.plusDays(1).atStartOfDay());
        log.info("定时任务：{} 访问统计汇总完成，更新 {} 条", date, n);
        return n;
    }
}
