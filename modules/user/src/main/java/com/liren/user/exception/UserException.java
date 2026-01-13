package com.liren.user.exception;

import com.liren.common.core.exception.BizException;
import com.liren.common.core.result.ResultCode;

public class UserException extends BizException {
    public UserException(ResultCode resultCode) {
        super(resultCode);
    }

    public UserException(int code, String message) {
        super(code, message);
    }
}