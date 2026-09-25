package com.hou.shortlink.link;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hou.shortlink.common.BizException;
import com.hou.shortlink.common.ErrorCode;
import com.hou.shortlink.link.dto.CreateLinkRequest;
import com.hou.shortlink.link.dto.LinkVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 短链服务实现
 */
@Service
@RequiredArgsConstructor
public class ShortLinkServiceImpl implements ShortLinkService {

    private final ShortLinkMapper shortLinkMapper;
    private final AccessLogMapper accessLogMapper;

    @Override
    @Transactional  // 两次写库必须同生共死：短码回填失败的话，占位记录也要回滚
    public LinkVO create(Long userId, CreateLinkRequest req) {
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
        return result.convert(LinkVO::from);
    }

    @Override
    public LinkVO getOwn(Long userId, Long id) {
        return LinkVO.from(getOwnEntity(userId, id));
    }

    @Override
    public void updateStatus(Long userId, Long id, Integer status) {
        if (status == null || (status != 0 && status != 1)) {
            throw new BizException(ErrorCode.STATUS_INVALID);
        }
        getOwnEntity(userId, id);  // 先校验存在且是自己的
        ShortLink update = new ShortLink();
        update.setId(id);
        update.setStatus(status);
        shortLinkMapper.updateById(update);  // 只更新非 null 字段，不会覆盖其他列
    }

    @Override
    public void deleteOwn(Long userId, Long id) {
        getOwnEntity(userId, id);
        shortLinkMapper.deleteById(id);  // 全局配置了逻辑删除，实际执行的是 UPDATE deleted=1
    }

    @Override
    public ShortLink findActiveByCode(String shortCode) {
        ShortLink link = shortLinkMapper.selectOne(new LambdaQueryWrapper<ShortLink>()
                .eq(ShortLink::getShortCode, shortCode)
                .eq(ShortLink::getStatus, 1));  // 只跳"启用中"的，禁用的视同不存在
        if (link == null) {
            return null;
        }
        // 已过期视同不存在（expire_time 为 null 表示永不过期）
        if (link.getExpireTime() != null && link.getExpireTime().isBefore(LocalDateTime.now())) {
            return null;
        }
        return link;
    }

    @Override
    public void recordAccess(ShortLink link, String ip, String userAgent) {
        // 1. 访问日志表追加一条（只有 insert，没有 update，适合归档）
        AccessLog log = new AccessLog();
        log.setShortLinkId(link.getId());
        log.setShortCode(link.getShortCode());
        log.setIp(ip);
        log.setUserAgent(userAgent != null && userAgent.length() > 512
                ? userAgent.substring(0, 512) : userAgent);
        log.setAccessTime(LocalDateTime.now());
        accessLogMapper.insert(log);
        // 2. pv 冗余计数 +1。用 "pv = pv + 1" 而不是查出数值再 set，
        //    让数据库原子自增，避免并发下丢计数（经典面试题）
        shortLinkMapper.update(null, new LambdaUpdateWrapper<ShortLink>()
                .eq(ShortLink::getId, link.getId())
                .setSql("pv = pv + 1"));
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
