package com.liren.user.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.liren.api.problem.dto.user.UserBasicInfoDTO;
import com.liren.common.core.result.Result;
import com.liren.user.dto.UserLoginDTO;
import com.liren.user.entity.UserEntity;
import com.liren.user.vo.UserLoginVO;
import com.liren.user.vo.UserVO;

import java.util.List;

public interface IUserService extends IService<UserEntity> {
    UserLoginVO login(UserLoginDTO userLoginDTO);

    /**
     * 批量获取用户信息
     */
    List<UserBasicInfoDTO> getBatchUserBasicInfo(List<Long> userIds);

    /**
     * 更新用户的提交统计信息
     */
    boolean updateUserStats(Long userId, boolean isAc);

    /**
     * 获取用户信息
     */
    UserVO getUserInfo(Long userId);
}
