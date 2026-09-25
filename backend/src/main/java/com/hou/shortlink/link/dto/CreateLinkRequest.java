package com.hou.shortlink.link.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.hibernate.validator.constraints.URL;

/**
 * 创建短链请求参数
 * expireDays 可不传（null = 永不过期）
 */
@Data
public class CreateLinkRequest {

    @NotBlank(message = "长网址不能为空")
    @URL(message = "请传入合法的网址（需带 http/https）")
    private String originUrl;

    @Min(value = 1, message = "过期天数至少 1 天")
    @Max(value = 3650, message = "过期天数最多 3650 天")
    private Integer expireDays;
}
