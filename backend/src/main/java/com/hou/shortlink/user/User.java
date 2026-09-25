package com.hou.shortlink.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * user 表实体，对应 sql/init.sql 里的用户表
 */
@Data
@TableName("user")
public class User {

    /** 主键，数据库自增 */
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 加密后的密码（数据库里不存明文） */
    private String password;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
