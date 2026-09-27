package com.hou.shortlink.link;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hou.shortlink.common.BizException;
import com.hou.shortlink.common.ErrorCode;
import com.hou.shortlink.common.RedisRateLimiter;
import com.hou.shortlink.link.dto.CreateLinkRequest;
import com.hou.shortlink.link.dto.LinkStatsVO;
import com.hou.shortlink.link.dto.LinkVO;
import com.hou.shortlink.stat.DailyAggRow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 短链服务实现
 */
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final AccessLogMapper accessLogMapper;
    private final LinkCacheService linkCacheService;
    private final AccessLogService accessLogService;
    private final RedisRateLimiter rateLimiter;

    @Override
    @Transactional  // 两次写库必须同生共死：短码回填失败的话，占位记录也要回滚
    public LinkVO create(Long userId, CreateLinkRequest req) {
        // 创建接口按用户限流：每分钟最多 10 次（Redis + Lua 固定窗口）
        if (!rateLimiter.tryAcquire("rl:create:" + userId, 10, 60)) {
            throw new BizException(ErrorCode.RATE_LIMIT);
        }
        ShortLink link = new ShortLink();
        link.setUserId(userId);
        link.setOriginUrl(req.getOriginUrl());
        link.setStatus(1);
        link.setPv(0L);
        // 时间字段代码里直接赋值，insert 后返回给前端时就是完整数据（否则要回查一次库）
        link.setCreatedAt(LocalDateTime.now());
        link.setUpdatedAt(LocalDateTime.now());
        if (req.getExpireDays() != null) {
            link.setExpireTime(LocalDateTime.now().plusDays(req.getExpireDays()));
        }
        // 第一步：短码先用 16 位随机串占位（表里 short_code 有唯一索引，必须先有值）
        // 占位串几乎不可能重复，且马上被第二步覆盖
        link.setShortCode(UUID.randomUUID().toString().replace("-", "").substring(0, 16));
        shortLinkMapper.insert(link);
        // 第二步：insert 后 MyBatis-Plus 已把自增 id 回填到 link 对象，
        // 用 id 算出正式短码再更新回去。这样短码和 id 一一对应，永不冲突，无需重试
        link.setShortCode(Base62Utils.encodeFromId(link.getId()));
        shortLinkMapper.updateById(link);
        return LinkVO.from(link);
    }

    @Override
    public IPage<LinkVO> page(Long userId, long page, long size, String keyword) {
        LambdaQueryWrapper<ShortLink> wrapper = new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getUserId, userId)
                .like(keyword != null && !keyword.isBlank(), ShortLink::getOriginUrl, keyword)
                .orderByDesc(ShortLink::getId);
        IPage<ShortLink> result = shortLinkMapper.selectPage(Page.of(page, size), wrapper);
        // 实体分页转 VO 分页（复用 MyBatis-Plus 的 convert，records/total 都带过去）
        return result.convert(link -> withPendingPv(LinkVO.from(link)));
    }

    @Override
    public LinkVO getOwn(Long userId, Long id) {
        return withPendingPv(LinkVO.from(getOwnEntity(userId, id)));
    }

    @Override
    public LinkStatsVO stats(Long userId, Long id, Integer days) {
        ShortLink link = getOwnEntity(userId, id);
        int n = (days == null || days < 1) ? 7 : Math.min(days, 90);
        LocalDate today = LocalDate.now();
        LocalDate startDate = today.minusDays(n - 1L);
        // 总 PV = 库值 + Redis 未回写增量；总 UV 按日志表 IP 精确去重
        LinkStatsVO vo = new LinkStatsVO();
        vo.setPv(link.getPv() + linkCacheService.pendingPv(link.getId()));
        vo.setUv(accessLogMapper.countUv(link.getId()));
        vo.setDays(n);
        // 每日趋势：一次聚合查询，按日期放入 map，缺失的天补 0（前端画图需要连续日期）
        Map<LocalDate, DailyAggRow> byDate = accessLogMapper
                .aggregateDaily(link.getId(), startDate.atStartOfDay(), today.plusDays(1).atStartOfDay())
                .stream()
                .collect(Collectors.toMap(DailyAggRow::getStatDate, r -> r));
        List<LinkStatsVO.DailyTrend> trend = new ArrayList<>();
        for (LocalDate d = startDate; !d.isAfter(today); d = d.plusDays(1)) {
            DailyAggRow row = byDate.get(d);
            trend.add(new LinkStatsVO.DailyTrend(d,
                    row == null ? 0L : row.getPv(),
                    row == null ? 0L : row.getUv()));
        }
        vo.setTrend(trend);
        return vo;
    }

    @Override
    public void updateStatus(Long userId, Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.STATUS_INVALID);
        }
        ShortLink link = getOwnEntity(userId, id);  // 先校验存在且是自己的
        ShortLink update = new ShortLink();
        update.setId(id);
        update.setStatus(status);
        shortLinkMapper.updateById(update);  // 只更新非 null 字段，不会覆盖其他列
        // Cache Aside 写顺序：先更新库，再删缓存，下次跳转重新从库加载
        linkCacheService.evict(link.getShortCode());
    }

    @Override
    public void deleteOwn(Long userId, Long id) {
        ShortLink link = getOwnEntity(userId, id);
        shortLinkMapper.deleteById(id);  // 全局配置了逻辑删除，实际执行的是 UPDATE deleted=1
        linkCacheService.evict(link.getShortCode());
    }

    @Override
    public ShortLink findActiveByCode(String shortCode) {
        // W3 起：缓存逻辑收敛到 LinkCacheService（Cache Aside：先 Redis 后 MySQL）
        return linkCacheService.findActiveByCode(shortCode);
    }

    @Override
    public void recordAccess(ShortLink link, String ip, String userAgent) {
        // 1. pv 计数从数据库 "pv = pv + 1" 改为 Redis 原子 INCR：
        //    跳转高峰期没必要每次访问都写 MySQL，Redis 里的增量由定时任务定期回写（最终一致）
        linkCacheService.incrPv(link.getId());
        // 2. 访问日志异步落库，不阻塞 302 跳转
        accessLogService.recordAsync(link, ip, userAgent);
    }

    /** 展示 pv = 数据库里已回写的 + Redis 里还没回写的增量（读写分离后是最终一致，不是强一致） */
    private LinkVO withPendingPv(LinkVO vo) {
        vo.setPv(vo.getPv() + linkCacheService.pendingPv(vo.getId()));
        return vo;
    }

    /** 查"自己的"短链，不存在或不是自己的统一报同一个错，不暴露他人数据 */
    private ShortLink getOwnEntity(Long userId, Long id) {
        ShortLink link = shortLinkMapper.selectById(id);
        if (link == null || !link.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.LINK_NOT_FOUND);
        }
        return link;
    }
}
