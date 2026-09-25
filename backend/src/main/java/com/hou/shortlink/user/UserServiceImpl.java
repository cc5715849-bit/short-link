package com.hou.shortlink.user;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hou.shortlink.common.BizException;
import com.hou.shortlink.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 */
@Service
@RequiredArgsConstructor  // Lombok：为所有 final 字段生成构造器，实现构造器注入
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void register(String username, String password) {
        // 1. 用户名唯一校验（数据库 uk_username 唯一索引兜底，双保险）
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (count > 0) {
            throw new BizException(ErrorCode.USERNAME_EXISTS);
        }
        // 2. BCrypt 加密后入库；BCrypt 自带随机盐，同一密码每次密文都不同
        User user = new User();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        userMapper.insert(user);
    }

    @Override
    public User login(String username, String password) {
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        // 统一提示"用户名或密码错误"，不区分具体哪个错，防止攻击者撞库探测
        if (user == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new BizException(ErrorCode.USERNAME_OR_PASSWORD_WRONG);
        }
        return user;
    }

    @Override
    public User getById(Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            // token 有效但用户已不存在（如被删除），当作未登录处理
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        return user;
    }
}
