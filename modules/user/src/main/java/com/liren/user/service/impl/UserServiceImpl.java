package com.liren.user.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liren.common.core.entity.UserEntity;
import com.liren.common.core.enums.UserStatusEnum;
import com.liren.common.core.result.ResultCode;
import com.liren.common.core.utils.BCryptUtil;
import com.liren.common.core.utils.JwtUtil;
import com.liren.user.dto.UserLoginDTO;
import com.liren.user.exception.UserLoginException;
import com.liren.user.mapper.UserMapper;
import com.liren.user.service.IUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements IUserService {
    @Autowired
    private UserMapper userMapper;


    @Override
    public String login(UserLoginDTO userLoginDTO) {
        // 1. 判断用户是否存在
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        UserEntity user = userMapper.selectOne(
                wrapper.eq(UserEntity::getUserAccount, userLoginDTO.getUserAccount())
        );
        if(user == null) {
            throw new UserLoginException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 判断用户是否状态正常
        if(user.getStatus().equals(UserStatusEnum.FORBIDDEN.getCode())) {
            throw new UserLoginException(ResultCode.USER_IS_FORBIDDEN);
        }

        // 3. 校验密码
        if(!BCryptUtil.isMatch(userLoginDTO.getPassword(), user.getPassword())) {
            throw new UserLoginException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 4. 生成token进行返回
        return JwtUtil.createToken(user.getUserId());
    }
}
