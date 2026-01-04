package com.liren.contest.controller;

import com.liren.api.problem.api.ContestInterface;
import com.liren.common.core.result.Result;
import com.liren.contest.service.IContestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contest/inner")
@Tag(name = "竞赛内部远程调用接口")
public class ContestInnerController implements ContestInterface {
    @Autowired
    private IContestService contestService;

    @Override
    @Operation(summary = "验证竞赛权限")
    public Result<Boolean> validateContestPermission(@RequestParam("contestId") Long contestId, @RequestParam("userId") Long userId) {
        return Result.success(contestService.validateContestPermission(contestId, userId));
    }
}
