package com.hou.shortlink;

import com.hou.shortlink.common.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 健康检查：最简单的探活接口，也用来验证服务是否正常启动
 */
@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Result<String> health() {
        return Result.success("pong");
    }
}
