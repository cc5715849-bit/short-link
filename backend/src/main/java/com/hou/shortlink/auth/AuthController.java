package com.hou.shortlink.auth;

import com.hou.shortlink.auth.dto.LoginRequest;
import com.hou.shortlink.auth.dto.LoginResponse;
import com.hou.shortlink.auth.dto.RegisterRequest;
import com.hou.shortlink.auth.dto.UserVO;
import com.hou.shortlink.common.Result;
import com.hou.shortlink.user.User;
import com.hou.shortlink.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口：注册 / 登录 / 当前用户信息
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /** 注册：成功只返回 code=0，不返回数据 */
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest req) {
        userService.register(req.getUsername(), req.getPassword());
        return Result.success();
    }

    /** 登录：校验通过后签发 JWT token */
    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        User user = userService.login(req.getUsername(), req.getPassword());
        String token = jwtUtil.generate(user.getId());
        return Result.success(new LoginResponse(token, user.getId(), user.getUsername()));
    }

    /** 当前登录用户信息：userId 是拦截器解析 token 后放进 request 的 */
    @GetMapping("/me")
    public Result<UserVO> me(HttpServletRequest request) {
        Long userId = (Long) request.getAttribute(JwtInterceptor.USER_ID_ATTR);
        User user = userService.getById(userId);
        return Result.success(UserVO.from(user));
    }
}
