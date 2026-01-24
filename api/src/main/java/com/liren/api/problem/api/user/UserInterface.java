package com.liren.api.problem.api.user;

import com.liren.api.problem.dto.user.UserBasicInfoDTO;
import com.liren.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(contextId = "UserInterface", value = "user-service", path = "/user/inner")
public interface UserInterface {

    /**
     * 批量获取用户基本信息
     * @param userIds 用户ID列表
     */
    @GetMapping("/getBatchBasicInfo")
    Result<List<UserBasicInfoDTO>> getBatchUserBasicInfo(@RequestParam("userIds") List<Long> userIds);

    /**
     * 更新用户的提交统计信息
     * @param userId 用户ID
     * @param isAc 是否通过 (Accepted)
     */
    @PostMapping("/update/stats")
    Result<Boolean> updateUserStats(@RequestParam("userId") Long userId, @RequestParam("isAc") Boolean isAc);
}
