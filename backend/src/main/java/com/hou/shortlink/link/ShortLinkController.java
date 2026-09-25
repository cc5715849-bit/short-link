package com.hou.shortlink.link;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hou.shortlink.auth.JwtInterceptor;
import com.hou.shortlink.common.Result;
import com.hou.shortlink.link.dto.CreateLinkRequest;
import com.hou.shortlink.link.dto.LinkVO;
import com.hou.shortlink.link.dto.StatusUpdateRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链管理接口：全部需要登录（在拦截器白名单 /api/links/** 里）
 */
@RestController
@RequestMapping("/api/links")
public class ShortLinkController {

    private final ShortLinkService shortLinkService;

    public ShortLinkController(ShortLinkService shortLinkService) {
        this.shortLinkService = shortLinkService;
    }

    /** 创建短链 */
    @PostMapping
    public Result<LinkVO> create(@Valid @RequestBody CreateLinkRequest req, HttpServletRequest request) {
        LinkVO vo = shortLinkService.create(currentUserId(request), req);
        fillShortUrl(vo, request);
        return Result.success(vo);
    }

    /** 我的短链分页列表 */
    @GetMapping
    public Result<IPage<LinkVO>> page(@RequestParam(defaultValue = "1") long page,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) String keyword,
                                      HttpServletRequest request) {
        Long userId = currentUserId(request);
        IPage<LinkVO> result = shortLinkService.page(userId, page, Math.min(size, 50), keyword);
        result.getRecords().forEach(vo -> fillShortUrl(vo, request));
        return Result.success(result);
    }

    /** 短链详情 */
    @GetMapping("/{id}")
    public Result<LinkVO> detail(@PathVariable Long id, HttpServletRequest request) {
        LinkVO vo = shortLinkService.getOwn(currentUserId(request), id);
        fillShortUrl(vo, request);
        return Result.success(vo);
    }

    /** 启用/禁用 */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(@PathVariable Long id,
                                     @Valid @RequestBody StatusUpdateRequest req,
                                     HttpServletRequest request) {
        shortLinkService.updateStatus(currentUserId(request), id, req.getStatus());
        return Result.success();
    }

    /** 删除（逻辑删） */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id, HttpServletRequest request) {
        shortLinkService.deleteOwn(currentUserId(request), id);
        return Result.success();
    }

    /** 从拦截器放进 request 的属性里取当前用户 id */
    private static Long currentUserId(HttpServletRequest request) {
        return (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTR);
    }

    /** 按当前请求的域名拼出完整短链（http://localhost:8080/Ab3xK），前端直接复制可用 */
    private static void fillShortUrl(LinkVO vo, HttpServletRequest request) {
        int port = request.getServerPort();
        String portPart = (port == 80 || port == 443) ? "" : ":" + port;
        vo.setShortUrl(request.getScheme() + "://" + request.getServerName() + portPart + "/" + vo.getShortCode());
    }
}
