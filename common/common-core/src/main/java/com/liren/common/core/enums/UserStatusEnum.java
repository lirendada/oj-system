package com.liren.common.core.enums;

import com.baomidou.mybatisplus.annotation.EnumValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum UserStatusEnum {

    FORBIDDEN(0, "禁用"),
    NORMAL(1, "正常");

    /**
     * @EnumValue 是 MyBatis-Plus 的注解
     * 标记这个字段是存入数据库的真实值
     */
    @EnumValue
    private final Integer code;

    private final String desc;
}