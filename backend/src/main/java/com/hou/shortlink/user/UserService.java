package com.hou.shortlink.user;

/**
 * 用户服务接口：Controller 只依赖接口，不依赖实现
 */
public interface UserService {

    /** 注册：用户名唯一校验 + BCrypt 加密入库 */
    void register(String username, String password);

    /** 登录：校验用户名密码，通过返回用户实体 */
    User login(String username, String password);

    /** 按主键查用户，查不到抛业务异常 */
    User getById(Long id);
}
