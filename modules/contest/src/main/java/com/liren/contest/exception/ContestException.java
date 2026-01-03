package com.liren.contest.exception;

import com.liren.common.core.exception.BizException;
import com.liren.common.core.result.ResultCode;

public class ContestException extends BizException {
    public ContestException(ResultCode resultCode) {
        super(resultCode);
    }
}