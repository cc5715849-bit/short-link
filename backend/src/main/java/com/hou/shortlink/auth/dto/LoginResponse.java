package com.hou.shortlink.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录成功返回体：token 是 JWT 字符串，
 * 之后所有需要登录的接口都在 Header 里带 Authorization: Bearer {token}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private Long userId;
    private String username;
}
