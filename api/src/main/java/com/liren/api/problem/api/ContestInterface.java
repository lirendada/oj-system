package com.liren.api.problem.api;

import com.liren.common.core.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * contextId: 用于区分同一个服务名的不同 Client (防止Bean冲突)
 * name: 目标服务名 (contest-service)
 * path: 统一前缀 (建议内部接口加 /inner 区分)
 */
@FeignClient(contextId = "ContestInterface",name = "contest-service", path = "/contest/inner")
public interface ContestInterface {
    /**
     * 校验提交权限
     */
    @GetMapping("/validate-permission")
    Result<Boolean> validateContestPermission(@RequestParam("contestId") Long contestId, @RequestParam("userId") Long userId);

}
