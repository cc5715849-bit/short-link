package com.hou.shortlink.link.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 启用/禁用请求参数 */
@Data
public class StatusUpdateRequest {

    @NotNull(message = "status 不能为空")
    private Integer status;
}
