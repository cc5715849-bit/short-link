package com.hou.shortlink.auth.dto;

import com.hou.shortlink.user.User;
import lombok.Data;

/**
 * 用户信息返回体（VO = View Object，返回给前端看的对象）
 * 之所以单独定义，是为了把 password 等敏感字段挡在后面
 */
@Data
public class UserVO {

    private Long id;
    private String username;

    public static UserVO from(User user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        return vo;
    }
}
