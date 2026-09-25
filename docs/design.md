# 短链接平台（ShortLink）设计与开发计划

> 定位：求职实战项目。目标不是"功能多"，而是**完整走一遍真实开发流程**：
> 需求 → 库表设计 → 接口文档 → 分支开发 → 自测 → 联调 → 部署上线。
> 面试叙事：高频读、低频写的典型场景，天然适合讨论缓存、发号器、限流和性能优化。

## 1. 一句话需求

用户提交长网址，系统生成短码（如 `https://s.xxx.com/Ab3xK`）；
任何人访问短链时 302 跳转到原网址，并记录访问日志、统计 PV/UV。

## 2. 功能清单

### P0 — MVP（必须完成，面试底线）

- 用户注册 / 登录（JWT），密码 BCrypt 加密
- 创建短链：长网址 → 6 位短码，支持设置过期天数
- 短链跳转：302 重定向 + 记录访问日志（异步写，不阻塞跳转）
- 短链管理：我的短链分页列表、启用/禁用、删除（逻辑删）
- 访问统计：单条短链 PV/UV、近 7 天每日趋势
- 全局规范：统一返回结构 `Result<T>`、全局异常处理、参数校验（`@Validated`）

### P1 — 进阶（面试加分项，做 2~3 个即可）

- Redis 缓存热点短链映射（Cache Aside + 空值缓存防穿透）
- 接口限流：创建短链接口按用户/IP 限流（Redis + Lua）
- 访问日志异步落库（线程池，批量写入）
- 每日统计定时任务：凌晨汇总 `access_log` → `daily_stat`
- 短码自定义（校验唯一 + 保留字）

### P2 — 拔高（可选，有余力再做）

- 布隆过滤器防穿透；UV 精确去重（HyperLogLog 为估算方案）
- 号段模式发号器（为高并发扩展做讨论素材）
- 访问日志按月分表 / 归档策略

## 3. 技术栈

| 层 | 选型 |
|---|---|
| 后端 | Spring Boot 3.x、MyBatis-Plus、JWT（jjwt）、Validation |
| 存储 | MySQL 8、Redis 7 |
| 前端 | 原生 HTML/CSS/JS（管理后台 + 跳转演示页，不用框架降低成本） |
| 部署 | Docker + Docker Compose + Nginx（反代 + 托管静态页） |
| 工具 | Apifox（接口文档）、Git（分支流）、JUnit 5（核心链路单测） |

## 4. 表结构（4 张表）

```sql
-- 用户表
CREATE TABLE `user` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`   VARCHAR(32)  NOT NULL COMMENT '用户名',
  `password`   VARCHAR(100) NOT NULL COMMENT '密码（BCrypt）',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户';

-- 短链表
CREATE TABLE `short_link` (
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

-- 访问日志表（写多，MVP 单表；分表/归档作为面试讨论点）
CREATE TABLE `access_log` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT,
  `short_link_id` BIGINT      NOT NULL,
  `short_code`    VARCHAR(16) NOT NULL,
  `ip`            VARCHAR(64)          DEFAULT NULL,
  `user_agent`    VARCHAR(512)         DEFAULT NULL,
  `access_time`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_link_time` (`short_link_id`, `access_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='访问日志';

-- 每日统计表（P1 定时任务汇总，避免每次都扫日志）
CREATE TABLE `daily_stat` (
  `id`            BIGINT  NOT NULL AUTO_INCREMENT,
  `short_link_id` BIGINT  NOT NULL,
  `stat_date`     DATE    NOT NULL,
  `pv`            BIGINT  NOT NULL DEFAULT 0,
  `uv`            BIGINT  NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_link_date` (`short_link_id`, `stat_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日统计';
```

设计要点（面试可讲）：

- 短码唯一靠 `uk_short_code`，跳转先查 `short_code` 索引
- `access_log` 联合索引 `(short_link_id, access_time)` 支撑"单链近 7 天趋势"
- `pv` 冗余计数：列表页展示不用每次聚合日志表；高并发下用 Redis 累加 + 定时回写
- 日志表只追加不更新，统计走 `daily_stat` 汇总表，避免大表聚合

## 5. 接口清单（Apifox 里按此建目录）

### 公开接口

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/{shortCode}` | 302 跳转（记录访问日志，不要求登录） |
| GET | `/api/health` | 健康检查 |

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册 `{username, password}` |
| POST | `/api/auth/login` | 登录，返回 token |
| GET | `/api/auth/me` | 当前用户信息 |

### 短链管理（需登录，Header 带 token）

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/links` | 创建短链 `{originUrl, expireDays?}` |
| GET | `/api/links?page=&size=&keyword=` | 我的短链分页列表 |
| GET | `/api/links/{id}` | 短链详情（含统计概览） |
| PUT | `/api/links/{id}/status` | 启用/禁用 `{status}` |
| DELETE | `/api/links/{id}` | 删除（逻辑删） |
| GET | `/api/links/{id}/stats?days=7` | PV/UV + 每日趋势 |

统一返回：

```json
{ "code": 0, "message": "ok", "data": { } }
```

约定：`code=0` 成功；业务错误用业务码（如 `1001 用户名已存在`）；
参数校验失败 400，未登录 401，无权限 403，跳转目标不存在返回 404 页面。

## 6. 短码生成方案（设计核心，面试必问）

**采用：数据库自增 id + 62 进制编码 + 起始偏移。**

- id 从 10 亿（偏移量）起步，转 62 进制（0-9a-zA-Z）得 6 位短码，容量约 568 亿
- 为什么不用随机串：需要冲突重试，且无法反推；为什么不用 MD5 截断：冲突率不可控、长度不稳定
- 面试扩展：高并发下自增瓶颈 → 号段模式 / 雪花 ID；短码用尽 → 加长到 7 位
- 保留短码：`api`、`health`、`login` 等路由占用的词 + 常见敏感词

## 7. 跳转链路（性能叙事主线）

```
浏览器 GET /Ab3xK
  → Nginx
  → 查 Redis（key = link:code:Ab3xK）
      命中 → 302
      未命中 → 查 MySQL → 回填缓存 → 302
      不存在 → 缓存空值（短 TTL）→ 404 页面   ← 防缓存穿透
  → 异步线程池写 access_log + Redis 累加 pv
```

- 用 302 而不是 301：301 会被浏览器永久缓存，无法再统计访问
- 高并发话题：热点 key、缓存击穿（互斥/逻辑过期）、雪崩（TTL 加随机）

## 8. 里程碑（按周执行，总计约 4–6 周）

| 周 | 目标 | 验收标准 |
|---|---|---|
| W1 | 环境搭建；库表 + Apifox 接口文档定稿；用户模块；统一返回/异常/校验 | Apifox 里所有接口可调通；Git 仓库就绪 |
| W2 | 短链 CRUD + 短码生成 + 302 跳转 + 访问日志（同步写先跑通） | 创建→跳转→日志落库全链路可演示 |
| W3 | Redis 缓存 + 限流 + 统计接口 + 定时任务；核心链路单元测试 | JUnit 通过；压测跳转接口看缓存效果 |
| W4 | 前端页面（登录/列表/创建/统计 4 个页）联调；Docker Compose 部署到云服务器 | 公网可访问，README 附架构图和截图 |
| W5–6 | P1/P2 进阶项 + 面试话术整理 + 简历项目描述 | 能脱稿讲清短码、缓存、限流三个设计点 |

## 9. Git 工作流（模拟团队协作，求职时能讲出来）

- `main` 分支保护，不直接提交
- 每个功能一个分支：`feature/user-auth`、`feature/link-crud`、`feature/redis-cache`
- 提交信息规范：`feat: xxx` / `fix: xxx` / `docs: xxx` / `test: xxx`
- 每个分支自测通过后合并回 `main`，合并前 `git pull --rebase` 保持线性历史

## 10. 面试深挖预演（边做边准备答案）

1. 为什么用 62 进制？为什么不用 64/58？
2. 短码生成的几种方案对比：随机、哈希、自增+编码、号段、雪花？
3. 302 和 301 的区别？为什么统计场景必须 302？
4. 缓存穿透/击穿/雪崩分别是什么，项目里怎么防？
5. 访问日志直接写库会有什么问题？异步怎么保证不丢？
6. PV 用数据库 `pv+1` 有什么并发问题？Redis 累加怎么回写？
7. 如果 QPS 涨 100 倍，这个系统哪里先崩，怎么扩？
