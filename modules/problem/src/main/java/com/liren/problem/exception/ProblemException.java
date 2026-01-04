package com.liren.problem.exception;

import com.liren.common.core.exception.BizException;
import com.liren.common.core.result.ResultCode;

public class ProblemException extends BizException {
    public ProblemException(ResultCode resultCode) {
        super(resultCode);
    }

    public ProblemException(int code, String message) {
        super(code, message);
    }
}