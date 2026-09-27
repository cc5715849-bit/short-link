package com.hou.shortlink.stat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface DailyStatMapper extends BaseMapper<DailyStat> {

    /**
     * 把一段时间内的 access_log 聚合后写入 daily_stat。
     * 一条 SQL 完成"聚合 + 写入"，且靠 uk_link_date 唯一键 + ON DUPLICATE KEY UPDATE 保证幂等：
     * 任务重跑、补数都不会产生重复行
     */
    @Insert("INSERT INTO daily_stat (short_link_id, stat_date, pv, uv) " +
            "SELECT short_link_id, DATE(access_time), COUNT(*), COUNT(DISTINCT ip) " +
            "FROM access_log " +
            "WHERE access_time >= #{start} AND access_time < #{end} " +
            "GROUP BY short_link_id, DATE(access_time) " +
            "ON DUPLICATE KEY UPDATE pv = VALUES(pv), uv = VALUES(uv)")
    int summarizeRange(@Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
