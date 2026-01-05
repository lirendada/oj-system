package com.liren.user.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.liren.api.problem.dto.user.UserBasicInfoDTO;
import com.liren.user.entity.UserEntity;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl implements IUserService {
    @Autowired
    private UserMapper userMapper;


    //TODO：redis优化
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
        if(UserStatusEnum.FORBIDDEN.getCode().equals(user.getStatus())) {
            throw new UserLoginException(ResultCode.USER_IS_FORBIDDEN);
        }

        // 3. 校验密码
        if(!BCryptUtil.isMatch(userLoginDTO.getPassword(), user.getPassword())) {
            throw new UserLoginException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 4. 生成token进行返回
        // 【修改】添加 "user" 角色
        Map<String, Object> claims = new HashMap<>();
        claims.put("userRole", "user"); // 标记为普通用户
        return JwtUtil.createToken(user.getUserId(), claims);
    }


    /**
     * 获取批量用户基本信息
     */
    @Override
    public List<UserBasicInfoDTO> getBatchUserBasicInfo(List<Long> userIds) {
        if(CollectionUtil.isEmpty(userIds)) {
            return new ArrayList<>();
        }

        List<UserEntity> users = userMapper.getBatchUser(userIds);
        if(CollectionUtil.isEmpty(users)) {
            return new ArrayList<>();
        }

        List<UserBasicInfoDTO> infoDTOS = users.stream().map(user -> {
            UserBasicInfoDTO userBasicInfoDTO = new UserBasicInfoDTO();
            userBasicInfoDTO.setId(user.getUserId());
            userBasicInfoDTO.setNickname(user.getNickName());
            userBasicInfoDTO.setAvatar(user.getAvatar());
            return userBasicInfoDTO;
        }).collect(Collectors.toList());
        return infoDTOS;
    }
}
