package com.liren.system.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liren.common.core.result.ResultCode;
import com.liren.common.core.utils.BCryptUtil;
import com.liren.common.core.utils.JwtUtil;
import com.liren.system.dto.SystemUserLoginDTO;
import com.liren.system.entity.SystemUserEntity;
import com.liren.system.exception.SystemUserException;
import com.liren.system.mapper.SystemUserMapper;
import com.liren.system.service.ISystemUserService;
import com.liren.system.vo.SystemUserLoginVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class SystemUserServiceImpl extends ServiceImpl<SystemUserMapper, SystemUserEntity> implements ISystemUserService {

    @Override
    public SystemUserLoginVO login(SystemUserLoginDTO systemUserLoginDTO) {
        // 获取用户信息
        LambdaQueryWrapper<SystemUserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(SystemUserEntity::getUserAccount, systemUserLoginDTO.getUserAccount());
        SystemUserEntity user = this.getOne(queryWrapper);
        if(user == null) {
            throw new SystemUserException(ResultCode.USER_NOT_FOUND);
        }

        // 到这说明用户存在，开始校验密码
        log.info("user：{}，loginDTO: {}", user, systemUserLoginDTO);
        if(!BCryptUtil.isMatch(systemUserLoginDTO.getPassword(), user.getPassword())) {
            throw new SystemUserException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 到这说明密码正确，开始返回登录信息
        log.info("用户登录成功，用户账号：{}", user);
        SystemUserLoginVO responseVO = new SystemUserLoginVO();
        BeanUtil.copyProperties(user, responseVO);

        // 【新增】生成 Token，并硬编码角色为 "admin"
        Map<String, Object> claims = new HashMap<>();
        claims.put("userRole", "admin"); // 标记为管理员
        String token = JwtUtil.createToken(user.getUserId(), claims);

        responseVO.setToken(token); // 设置 Token

        return responseVO;
    }

}
