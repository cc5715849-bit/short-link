package com.hou.shortlink.link.dto;

import com.hou.shortlink.link.ShortLink;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 短链信息返回体（列表和详情共用）
 * 不返回 userId/deleted 等内部字段
 */
@Data
public class LinkVO {

    private Long id;
    private String shortCode;

    /** 完整短链接（http://localhost:8080/Ab3xK），Controller 里按请求域名拼 */
    private String shortUrl;

    private String originUrl;
    private Integer status;
    private Long pv;
    private LocalDateTime expireTime;
    private LocalDateTime createdAt;

    public static LinkVO from(ShortLink link) {
        LinkVO vo = new LinkVO();
        vo.setId(link.getId());
        vo.setShortCode(link.getShortCode());
        vo.setOriginUrl(link.getOriginUrl());
        vo.setStatus(link.getStatus());
        vo.setPv(link.getPv());
        vo.setExpireTime(link.getExpireTime());
        vo.setCreatedAt(link.getCreatedAt());
        return vo;
    }
}
