package com.hou.shortlink.link;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * 短链跳转：公开接口，不要求登录（根路径 /{shortCode} 不在拦截器白名单里，天然放行）
 * 这是全系统 QPS 最高的接口，W3 会给它加 Redis 缓存
 */
@RestController
public class RedirectController {

    private final ShortLinkService shortLinkService;

    public RedirectController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    @GetMapping("/{shortCode}")
    public ResponseEntity<byte[]> redirect(@PathVariable String shortCode, HttpServletRequest request) {
        ShortLink link = shortLinkService.findActiveByCode(shortCode);
        if (link == null) {
            // 不存在 / 已禁用 / 已过期，统一返回 404 页面
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .contentType(new MediaType(MediaType.TEXT_HTML, StandardCharsets.UTF_8))
                    .body("<h1>404</h1><p>链接不存在或已失效</p>".getBytes(StandardCharsets.UTF_8));
        }
        // 记录访问日志 + pv+1（W2 同步写；W3 优化为线程池异步，不阻塞跳转）
        shortLinkService.recordAccess(link, resolveIp(request), request.getHeader("User-Agent"));
        // 用 302 临时跳转而不是 301：301 会被浏览器永久缓存，
        // 之后用户再点这个短链根本不会打到服务器，访问统计就全丢了
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.getOriginUrl()))
                .build();
    }

    /** 取真实 IP：经过 Nginx 等反代后，真实 IP 在 X-Forwarded-For 里（第一个） */
    private String resolveIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
