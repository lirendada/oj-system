package com.liren.user.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liren.api.problem.dto.user.UserBasicInfoDTO;
import com.liren.common.core.constant.Constants;
import com.liren.common.redis.RedisUtil;
import com.liren.user.dto.UserRegisterDTO;
import com.liren.user.dto.UserResetPassDTO;
import com.liren.user.entity.UserEntity;
import com.liren.common.core.enums.UserStatusEnum;
import com.liren.common.core.result.ResultCode;
import com.liren.common.core.utils.BCryptUtil;
import com.liren.common.core.utils.JwtUtil;
import com.liren.user.dto.UserLoginDTO;
import com.liren.user.exception.UserException;
import com.liren.user.mapper.UserMapper;
import com.liren.user.service.IMailService;
import com.liren.user.service.IUserService;
import com.liren.user.vo.UserLoginVO;
import com.liren.user.vo.UserVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, UserEntity> implements IUserService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private IMailService mailService;

    @Autowired
    private RedisUtil redisUtil; // 假设 common-redis 中有这个工具类

    //TODO：redis优化
    @Override
    public UserLoginVO login(UserLoginDTO userLoginDTO) {
        // 1. 判断用户是否存在
        LambdaQueryWrapper<UserEntity> wrapper = new LambdaQueryWrapper<>();
        UserEntity user = userMapper.selectOne(
                wrapper.eq(UserEntity::getUserAccount, userLoginDTO.getUserAccount())
        );
        if(user == null) {
            throw new UserException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 判断用户是否状态正常
        if(UserStatusEnum.FORBIDDEN.getCode().equals(user.getStatus())) {
            throw new UserException(ResultCode.USER_IS_FORBIDDEN);
        }

        // 3. 校验密码
        if(!BCryptUtil.isMatch(userLoginDTO.getPassword(), user.getPassword())) {
            throw new UserException(ResultCode.USER_PASSWORD_ERROR);
        }

        // 4. 生成 Token (带上 user 角色)
        Map<String, Object> claims = new HashMap<>();
        claims.put("userRole", "user");
        String token = jwtUtil.createToken(user.getUserId(), claims);

        // 5. 【修改点】组装 VO 返回
        UserLoginVO loginVO = new UserLoginVO();
        loginVO.setToken(token);
        loginVO.setUserId(user.getUserId());
        loginVO.setNickName(user.getNickName());
        loginVO.setAvatar(user.getAvatar());

        return loginVO;
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


    /**
     * 更新用户的提交统计信息
     */
    @Override
    public boolean updateUserStats(Long userId, boolean isAc) {
        LambdaUpdateWrapper<UserEntity> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(UserEntity::getUserId, userId);

        // 提交数 +1 (使用 IFNULL 防止字段默认为 null 导致计算失败)
        wrapper.setSql("submitted_count = IFNULL(submitted_count, 0) + 1");

        // 如果 AC 了，通过数也 +1
        if (isAc) {
            wrapper.setSql("accepted_count = IFNULL(accepted_count, 0) + 1");
        }

        return this.update(wrapper);
    }


    /**
     * 获取用户信息
     */
    @Override
    public UserVO getUserInfo(Long userId) {
        // 1. 查询数据库
        UserEntity userEntity = this.getById(userId);

        // 2. 判空
        if (userEntity == null) {
            throw new UserException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 转换为 VO
        UserVO userVO = new UserVO();
        // 因为 UserVO 的字段名和 UserEntity 的字段名（驼峰）现在完全一致，
        // 所以 BeanUtil 可以自动拷贝所有对应字段
        BeanUtil.copyProperties(userEntity, userVO);

        // 4. 头像默认值处理
        // 如果数据库里 avatar 是 null 或者 空字符串，给个默认头像
        if (userVO.getAvatar() == null || userVO.getAvatar().isEmpty()) {
            userVO.setAvatar("https://p.ssl.qhimg.com/sdm/480_480_/t01520a1bd1802ae864.jpg");
        }

        return userVO;
    }


    /**
     * 用户注册
     */
    @Override
    public Long register(UserRegisterDTO userRegisterDTO) {
        String userAccount = userRegisterDTO.getUserAccount();
        String password = userRegisterDTO.getPassword();
        String checkPassword = userRegisterDTO.getCheckPassword();

        // 1. 校验两次密码是否一致
        if (!StrUtil.equals(password, checkPassword)) {
            throw new UserException(ResultCode.PASSWORD_NOT_MATCH);
        }

        // 2. 检查账号是否重复
        LambdaQueryWrapper<UserEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserEntity::getUserAccount, userAccount);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new UserException(ResultCode.USER_ALREADY_EXISTS);
        }

        // 3. 密码加密
        String encryptPassword = BCryptUtil.encode(password);

        // 4. 插入数据
        UserEntity userEntity = new UserEntity();
        userEntity.setUserAccount(userAccount);
        userEntity.setPassword(encryptPassword);
        // 默认昵称与账号相同，用户后续可修改
        userEntity.setNickName(userAccount);
        // 初始状态正常
        userEntity.setStatus(UserStatusEnum.NORMAL.getCode());
        // 默认头像（也可以留空，由 getUserInfo 处理默认值，这里显式设置一下更安全）
        userEntity.setAvatar("https://p.ssl.qhimg.com/sdm/480_480_/t01520a1bd1802ae864.jpg");

        boolean saveResult = this.save(userEntity);
        if (!saveResult) {
            throw new UserException(ResultCode.Register_FAILED);
        }

        return userEntity.getUserId();
    }


    /**
     * 发送忘记密码验证码
     */
    @Override
    public void sendForgetPasswordCode(String email) {
        // 1. 检查用户是否存在
        UserEntity user = this.getOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, email));
        if (user == null) {
            throw new UserException(ResultCode.USER_NOT_FOUND);
        }

        // 2. 生成 6 位随机验证码
        String code = String.valueOf((int)((Math.random() * 9 + 1) * 100000));

        // 3. 保存到 Redis，有效期 5 分钟
        redisUtil.set(Constants.FORGET_PASS_CODE_PREFIX + email, code, 300);

        // 4. 发送邮件
        mailService.sendSimpleMail(email, "【Liren OJ】重置密码验证码",
                "您的验证码是：" + code + "，有效期 5 分钟，请勿泄露给他人。");
    }


    /**
     * 重置密码
     */
    @Override
    public void resetPassword(UserResetPassDTO req) {
        String redisKey = Constants.FORGET_PASS_CODE_PREFIX + req.getEmail();

        // 1. 校验验证码
        if (!redisUtil.hasKey(redisKey)) {
            throw new UserException(ResultCode.RESET_PASS_CODE_EXPIRED);
        }
        String cachedCode = (String) redisUtil.get(redisKey);
        if (!cachedCode.equals(req.getCode())) {
            throw new UserException(ResultCode.RESET_PASS_CODE_ERROR);
        }

        // 2. 查找用户
        UserEntity user = this.getOne(new LambdaQueryWrapper<UserEntity>().eq(UserEntity::getEmail, req.getEmail()));
        if (user == null) {
            throw new UserException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 更新密码 (记得加密)
        user.setPassword(BCryptUtil.encode(req.getNewPassword()));
        this.updateById(user);

        // 4. 删除验证码
        redisUtil.del(redisKey);
    }
}
