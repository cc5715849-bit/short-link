package com.hou.shortlink.link;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hou.shortlink.stat.DailyAggRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AccessLogMapper extends BaseMapper<AccessLog> {

    /** 单条短链的总 UV：按 IP 精确去重 */
    @Select("SELECT COUNT(DISTINCT ip) FROM access_log WHERE short_link_id = #{linkId}")
    Long countUv(@Param("linkId") Long linkId);

    /** 单条短链按天聚合（PV/UV），支撑"近 N 天趋势" */
    @Select("SELECT DATE(access_time) AS statDate, COUNT(*) AS pv, COUNT(DISTINCT ip) AS uv " +
            "FROM access_log WHERE short_link_id = #{linkId} " +
            "AND access_time >= #{start} AND access_time < #{end} " +
            "GROUP BY DATE(access_time)")
    List<DailyAggRow> aggregateDaily(@Param("linkId") Long linkId,
                                     @Param("start") LocalDateTime start,
                                     @Param("end") LocalDateTime end);
}
