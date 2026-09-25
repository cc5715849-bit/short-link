package com.hou.shortlink.link;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hou.shortlink.link.dto.CreateLinkRequest;
import com.hou.shortlink.link.dto.LinkVO;

/**
 * 短链服务接口
 */
public interface ShortLinkService {

    /** 创建短链：insert 占位 → 拿自增 id → 62 进制编码 → 回填短码（事务内两步写） */
    LinkVO create(Long userId, CreateLinkRequest req);

    /** 我的短链分页列表，keyword 模糊匹配长网址 */
    IPage<LinkVO> page(Long userId, long page, long size, String keyword);

    /** 短链详情（校验归属：只能看自己的） */
    LinkVO getOwn(Long userId, Long id);

    /** 启用/禁用 */
    void updateStatus(Long userId, Long id, Integer status);

    /** 删除（逻辑删） */
    void deleteOwn(Long userId, Long id);

    /** 跳转用：按短码查"启用中且未过期"的短链，查不到返回 null */
    ShortLink findActiveByCode(String shortCode);

    /** 记录一次访问：写 access_log + 短链表 pv+1（W2 同步写，W3 优化为异步） */
    void recordAccess(ShortLink link, String ip, String userAgent);
}
