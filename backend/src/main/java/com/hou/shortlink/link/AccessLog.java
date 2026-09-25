package com.hou.shortlink.link;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * access_log 表实体：只追加不更新，是典型的"写多读少"日志表
 * W2 先同步写跑通，W3 改异步（线程池），这是预留的面试优化点
 */
@Data
@TableName("access_log")
public class AccessLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shortLinkId;

    private String shortCode;

    /** 访问者 IP */
    private String ip;

    /** 浏览器 UA，表里限 512，入库前截断 */
    private String userAgent;

    private LocalDateTime accessTime;
}
