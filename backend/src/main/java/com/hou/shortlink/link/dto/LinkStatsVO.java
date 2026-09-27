package com.hou.shortlink.link.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** 访问统计返回体：GET /api/links/{id}/stats?days=7 */
@Data
public class LinkStatsVO {

    /** 总 PV：数据库已回写的 + Redis 未回写的增量 */
    private Long pv;

    /** 总 UV：按 IP 精确去重（面试延伸：量大时改用 HyperLogLog 估算） */
    private Long uv;

    /** 趋势天数 */
    private Integer days;

    /** 每日趋势（缺失的天补 0，日期连续，前端可直接画图） */
    private List<DailyTrend> trend;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DailyTrend {
        private LocalDate date;
        private Long pv;
        private Long uv;
    }
}
