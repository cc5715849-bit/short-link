package com.hou.shortlink.stat;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hou.shortlink.link.AccessLog;
import com.hou.shortlink.link.AccessLogMapper;
import com.hou.shortlink.link.LinkCacheService;
import com.hou.shortlink.link.ShortLink;
import com.hou.shortlink.link.ShortLinkMapper;
import com.hou.shortlink.user.User;
import com.hou.shortlink.user.UserMapper;
import com.hou.shortlink.user.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * StatTask 定时任务测试。
 * 定时方法都是 public 的，不用等 cron 触发，直接调用即可——
 * 测试要验证的是"逻辑对不对"，不是"定时器准不准"
 */
@SpringBootTest
class StatTaskTest {

    @Autowired
    private StatTask statTask;
    @Autowired
    private LinkCacheService linkCacheService;
    @Autowired
    private ShortLinkMapper shortLinkMapper;
    @Autowired
    private AccessLogMapper accessLogMapper;
    @Autowired
    private DailyStatMapper dailyStatMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private UserMapper userMapper;

    @Test
    @DisplayName("flushPv：Redis 增量回写数据库一次，回写后清零；重复执行不重复累加")
    void flushPv_writesDeltaOnce() {
        ShortLink link = newLink(100L);  // 库里初始 pv = 100

        // 模拟 3 次跳转：pv 在 Redis 里 +3（数据库暂时不动）
        linkCacheService.incrPv(link.getId());
        linkCacheService.incrPv(link.getId());
        linkCacheService.incrPv(link.getId());
        assertEquals(3, linkCacheService.pendingPv(link.getId()));

        statTask.flushPv();

        // GETSET 取走全部增量回写：库 100 + 3 = 103，Redis 增量清零
        assertEquals(103L, shortLinkMapper.selectById(link.getId()).getPv());
        assertEquals(0, linkCacheService.pendingPv(link.getId()));

        // 再跑一次：没有新增量，pv 不变化（重复执行安全）
        statTask.flushPv();
        assertEquals(103L, shortLinkMapper.selectById(link.getId()).getPv());
    }

    @Test
    @DisplayName("summarizeDay：access_log 汇总进 daily_stat；重复执行结果不变（幂等 upsert）")
    void summarizeDay_idempotent() {
        ShortLink link = newLink(0L);
        // 造 3 条今天的访问日志：2 个不同 IP → 期望 pv=3，uv=2
        LocalDateTime now = LocalDateTime.now();
        accessLogMapper.insert(newLog(link, "10.0.0.1", now));
        accessLogMapper.insert(newLog(link, "10.0.0.1", now));
        accessLogMapper.insert(newLog(link, "10.0.0.2", now));

        statTask.summarizeDay(LocalDate.now());

        DailyStat row = dailyStatOf(link.getId());
        assertEquals(3L, row.getPv());
        assertEquals(2L, row.getUv());

        // 任务重跑：uk_link_date 唯一键 + ON DUPLICATE KEY UPDATE 覆盖写，不会翻倍
        statTask.summarizeDay(LocalDate.now());
        DailyStat rowAfterRerun = dailyStatOf(link.getId());
        assertEquals(3L, rowAfterRerun.getPv(), "重复汇总 pv 不应翻倍");
        assertEquals(2L, rowAfterRerun.getUv(), "重复汇总 uv 不应翻倍");
    }

    // ---------- 测试辅助方法 ----------

    /** 直接在库里造一条短链（不走接口，避开创建限流），挂在随机新用户下避免外键/归属问题 */
    private ShortLink newLink(long pv) {
        String username = "w3stat_" + UUID.randomUUID().toString().substring(0, 8);
        userService.register(username, "w3pass123");
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));

        ShortLink link = new ShortLink();
        link.setUserId(user.getId());
        // 占位短码：这个测试不经过跳转链路，只要不撞 uk_short_code 唯一索引即可
        link.setShortCode(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        link.setOriginUrl("https://example.com/stat-test");
        link.setStatus(1);
        link.setPv(pv);
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        shortLinkMapper.insert(link);
        return link;
    }

    private AccessLog newLog(ShortLink link, String ip, LocalDateTime time) {
        AccessLog log = new AccessLog();
        log.setShortLinkId(link.getId());
        log.setShortCode(link.getShortCode());
        log.setIp(ip);
        log.setUserAgent("junit");
        log.setAccessTime(time);
        return log;
    }

    /** 查这条短链今天的汇总行 */
    private DailyStat dailyStatOf(Long linkId) {
        return dailyStatMapper.selectOne(new LambdaQueryWrapper<DailyStat>()
                .eq(DailyStat::getShortLinkId, linkId)
                .eq(DailyStat::getStatDate, LocalDate.now()));
    }
}
