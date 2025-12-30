package com.liren.user.exception;

import com.liren.common.core.exception.BizException;
import com.liren.common.core.result.ResultCode;

public class UserLoginException extends BizException {
    public UserLoginException(ResultCode resultCode) {
        super(resultCode);
    }
}