package com.liren.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum JudgeResultEnum {
    ACCEPTED(1, "通过 (AC)"),

    WRONG_ANSWER(2, "答案错误 (WA)"),

    TIME_LIMIT_EXCEEDED(3, "运行超时 (TLE)"),

    MEMORY_LIMIT_EXCEEDED(4, "内存超限 (MLE)"),

    RUNTIME_ERROR(5, "运行错误 (RE)"),

    COMPILE_ERROR(6, "编译错误 (CE)"),

    SYSTEM_ERROR(7, "系统错误 (SE)");

    private final Integer code;
    private final String message;

    public static JudgeResultEnum getByCode(Integer code) {
        for (JudgeResultEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }
}