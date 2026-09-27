package com.hou.shortlink.stat;

import lombok.Data;

import java.time.LocalDate;

/** access_log 按天聚合的查询结果行 */
@Data
public class DailyAggRow {
    private LocalDate statDate;
    private Long pv;
    private Long uv;
}
