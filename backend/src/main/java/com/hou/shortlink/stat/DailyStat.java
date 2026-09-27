package com.hou.shortlink.stat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/** daily_stat 表实体：由定时任务从 access_log 汇总而来，避免统计接口每次都扫日志大表 */
@Data
@TableName("daily_stat")
public class DailyStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long shortLinkId;

    private LocalDate statDate;

    private Long pv;

    private Long uv;
}
