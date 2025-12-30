package com.liren.system.service;

import com.liren.system.dto.SystemUserLoginDTO;
import com.liren.system.vo.SystemUserLoginVO;


public interface ISystemUserService {

    SystemUserLoginVO login(SystemUserLoginDTO systemUserLoginDTO);
}
