package com.liren.common.core.result;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum ResultCode {

    /* ===================== 成功 ===================== */
    SUCCESS(1000, "操作成功"),

    /* ===================== 通用错误（2xxx） ===================== */
    ERROR(2000, "服务繁忙，请稍后重试"),
    SYSTEM_ERROR(2001, "系统内部错误"),
    RUNTIME_ERROR(2002, "系统运行异常"),
    IO_ERROR(2003, "IO 操作异常"),

    /* ===================== 参数错误（4xxx） ===================== */
    PARAM_ERROR(4000, "参数错误"),
    PARAM_FORMAT_ERROR(4001, "参数格式错误"),
    PARAM_ILLEGAL(4002, "参数不合法"),

    /* ===================== 权限 / 认证（5xxx） ===================== */
    UNAUTHORIZED(5001, "未登录或登录已过期"),
    FORBIDDEN(5002, "无权限访问"),

    /* ===================== 业务错误（6xxx） ===================== */
    BIZ_ERROR(6000, "业务处理失败"),
    DATA_NOT_FOUND(6001, "数据不存在"),
    DATA_CONFLICT(6002, "数据状态冲突"),
    USER_NOT_FOUND(6003, "用户不存在"),
    USER_ALREADY_EXIST(6004, "用户已存在"),
    USER_PASSWORD_ERROR(6005, "用户名或密码错误"),
    SUBJECT_NOT_FOUND(6006, "题目不存在"),
    SUBJECT_TITLE_EXIST(6007, "题目名称已存在，请勿重复添加"),
    USER_IS_FORBIDDEN(6008, "用户已被禁用"),

    /* ===================== 程序缺陷类（9xxx） ===================== */
    NULL_POINTER(9001, "空指针异常"),
    CLASS_CAST(9002, "类型转换异常"),
    INDEX_OUT_OF_BOUNDS(9003, "数据索引越界");

    private final int code;
    private final String message;
}

