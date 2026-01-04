package com.liren.common.core.result;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;

    public static <T> Result<T> success() {
        return new Result<T>(ResultCode.SUCCESS.getCode(), null, null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<T>(ResultCode.SUCCESS.getCode(), null, data);
    }

    public static <T> Result<T> fail(int code, String msg) {
        return new Result<T>(code, msg, null);
    }

    public static boolean isSuccess() {
        return ResultCode.SUCCESS.getCode() == 1000;
    }
}