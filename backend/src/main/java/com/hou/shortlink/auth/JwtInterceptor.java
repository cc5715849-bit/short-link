package com.hou.shortlink.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hou.shortlink.common.ErrorCode;
import com.hou.shortlink.common.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * JWT 登录拦截器：请求进 Controller 之前先校验 token
 * 约定：前端在 Header 里带 Authorization: Bearer {token}
 * 校验通过后把用户 id 放进 request，后续代码用 request.getAttribute("userId") 取
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

    public static final String USER_ID_ATTR = "userId";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public JwtInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String token = request.getHeader("Authorization");
        if (token != null && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        try {
            if (token != null && !token.isBlank()) {
                Long userId = jwtUtil.parseUserId(token);
                request.setAttribute(USER_ID_ATTR, userId);
                return true;  // 校验通过，放行
            }
        } catch (Exception e) {
            // token 缺失 / 篡改 / 过期，统一走下面的 401
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(Result.error(ErrorCode.UNAUTHORIZED)));
        return false; // 拦截，请求到此为止
    }
}
