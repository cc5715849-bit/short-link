package com.hou.shortlink.link;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * short_link 表实体
 */
@Data
@TableName("short_link")
public class ShortLink {

    /** 主键自增，短码 = base62(id + 偏移量)，所以 id 就是发号器来源 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 6 位短码，全表唯一 */
    private String shortCode;

    private String originUrl;

    /** 所属用户，列表/详情都要按它做归属校验 */
    private Long userId;

    /** 1 启用 0 禁用（禁用后跳转返回 404） */
    private Integer status;

    /** 访问次数冗余计数，列表页直接展示，不用每次聚合日志表 */
    private Long pv;

    /** 过期时间，null 表示永不过期 */
    private LocalDateTime expireTime;

    /** 逻辑删除：1 已删。配合 yml 里的全局配置，查询自动过滤、删除自动变 update */
    @TableLogic
    private Integer deleted;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
