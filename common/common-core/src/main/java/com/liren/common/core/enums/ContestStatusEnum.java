package com.liren.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ContestStatusEnum {
    NOT_STARTED(0, "未开始"),
    RUNNING(1, "进行中"),
    ENDED(2, "已结束");

    private final Integer code;
    private final String message;
}
