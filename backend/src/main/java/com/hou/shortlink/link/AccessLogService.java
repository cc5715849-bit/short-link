package com.hou.shortlink.link;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 访问日志异步落库。
 * 注意：必须单独放一个 Bean。@Async 靠 Spring 动态代理实现，
 * 如果写在 ShortLinkServiceImpl 里"自己调用自己"，注解不生效（经典坑）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogMapper accessLogMapper;

    /** 在 accessLogExecutor 线程池里执行，调用方（跳转线程）立刻返回 */
    @Async("accessLogExecutor")
    public void recordAsync(ShortLink link, String ip, String userAgent) {
        AccessLog logRow = new AccessLog();
        logRow.setShortLinkId(link.getId());
        logRow.setShortCode(link.getShortCode());
        logRow.setIp(ip);
        logRow.setUserAgent(userAgent != null && userAgent.length() > 512
                ? userAgent.substring(0, 512) : userAgent);
        logRow.setAccessTime(LocalDateTime.now());
        accessLogMapper.insert(logRow);
    }
}
