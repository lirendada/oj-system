package com.liren.user.service;

import com.liren.user.dto.UserLoginDTO;

public interface IUserService {
    String login(UserLoginDTO userLoginDTO);
}
