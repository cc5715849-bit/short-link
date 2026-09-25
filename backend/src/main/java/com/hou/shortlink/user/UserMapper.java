package com.hou.shortlink.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * MyBatis-Plus：继承 BaseMapper 就自动拥有 insert / selectOne / selectCount
 * / updateById / deleteById 等单表 CRUD，一行 SQL 都不用写
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
