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
    UPDATE_PROBLEM_ERROR(6009, "更新题目信息失败"),
    TEST_CASE_NOT_FOUND(6010, "题目缺少测试用例，无法判题"),
    SUBMIT_RECORD_NOT_FOUND(6011, "提交记录不存在"),
    CONTEST_TIME_ERROR(6012, "比赛时间错误"),
    CONTEST_NOT_FOUND(6013, "比赛不存在"),
    CONTEST_PROBLEM_ALREADY_EXIST(6014, "该题目已添加至本场比赛"),
    CONTEST_IS_ENDED(6015, "比赛已结束"),
    USER_ALREADY_REGISTERED_CONTEST(6016, "用户已报名本场比赛"),
    CONTEST_NOT_STARTED(6017, "比赛尚未开始"),
    USER_NOT_REGISTERED_CONTEST(6018, "用户未报名本场比赛"),
    NOT_ACCESS_TO_CONTEST(6019, "无权访问该比赛/题目"),
    FORBIDDEN_OPERATION(6020, "该题目不属于指定的比赛"),
    SUBMIT_LOGIC_ERROR(6021, "该题目正在进行比赛，请前往比赛页面提交，或等待比赛结束。"),
    USER_ALREADY_EXISTS(6022, "用户名已存在，请勿重复注册"),
    Register_FAILED(6023, "注册失败"),
    PASSWORD_NOT_MATCH(6024, "两次输入的密码不一致"),
    MAIL_SEND_ERROR(6025, "邮件发送失败"),
    RESET_PASS_CODE_EXPIRED(6026, "验证码已过期，请重新获取"),
    RESET_PASS_CODE_ERROR(6027, "验证码错误"),

    /* ===================== 程序缺陷类（9xxx） ===================== */
    NULL_POINTER(9001, "空指针异常"),
    CLASS_CAST(9002, "类型转换异常"),
    INDEX_OUT_OF_BOUNDS(9003, "数据索引越界");

    private final int code;
    private final String message;
}

