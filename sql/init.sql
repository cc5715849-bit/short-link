-- 短链接平台建库脚本（可重复执行环境准备）
-- 使用：mysql -uroot -p < sql/init.sql

CREATE DATABASE IF NOT EXISTS short_link DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
USE short_link;

-- 用户表
CREATE TABLE IF NOT EXISTS `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`   VARCHAR(32)  NOT NULL COMMENT '用户名',
  `password`   VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 短链表
CREATE TABLE IF NOT EXISTS `short_link` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键（发号器来源）',
  `short_code`  VARCHAR(16)   NOT NULL COMMENT '短码',
  `origin_url`  VARCHAR(1024) NOT NULL COMMENT '原始网址',
  `user_id`     BIGINT        NOT NULL COMMENT '所属用户',
  `status`      TINYINT       NOT NULL DEFAULT 1 COMMENT '1启用 0禁用',
  `pv`          BIGINT        NOT NULL DEFAULT 0 COMMENT '访问次数（冗余计数）',
  `expire_time` DATETIME      NULL COMMENT '过期时间，NULL 永不过期',
  `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '逻辑删除',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_short_code` (`short_code`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='短链';

-- 访问日志表
CREATE TABLE IF NOT EXISTS `access_log` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `short_link_id` BIGINT      NOT NULL,
  `short_code`    VARCHAR(16) NOT NULL,
  `ip`            VARCHAR(64)          DEFAULT NULL,
  `user_agent`    VARCHAR(512)         DEFAULT NULL,
  `access_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_link_time` (`short_link_id`, `access_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问日志';

-- 每日统计表
CREATE TABLE IF NOT EXISTS `daily_stat` (
  `id`            BIGINT NOT NULL AUTO_INCREMENT,
  `short_link_id` BIGINT NOT NULL,
  `stat_date`     DATE   NOT NULL,
  `pv`            BIGINT NOT NULL DEFAULT 0,
  `uv`            BIGINT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_link_date` (`short_link_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日统计';
