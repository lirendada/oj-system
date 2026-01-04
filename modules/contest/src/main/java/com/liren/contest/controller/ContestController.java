package com.liren.contest.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liren.common.core.context.UserContext;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.contest.dto.ContestAddDTO;
import com.liren.contest.dto.ContestProblemAddDTO;
import com.liren.contest.dto.ContestQueryRequest;
import com.liren.contest.exception.ContestException;
import com.liren.contest.service.IContestService;
import com.liren.contest.vo.ContestProblemVO;
import com.liren.contest.vo.ContestVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/contest")
@Tag(name = "比赛管理API")
public class ContestController {

    @Autowired
    private IContestService contestService;

    @PostMapping("/add")
    @Operation(summary = "创建/更新比赛", description = "ID为空新增，不为空更新")
    public Result<Boolean> saveOrUpdateContest(@RequestBody @Valid ContestAddDTO contestAddDTO) {
        return Result.success(contestService.saveOrUpdateContest(contestAddDTO));
    }

    @PostMapping("/list")
    @Operation(summary = "分页查询比赛列表", description = "支持根据状态动态筛选")
    public Result<Page<ContestVO>> listContest(@RequestBody ContestQueryRequest queryRequest) {
        return Result.success(contestService.listContestVO(queryRequest));
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取比赛详情")
    public Result<ContestVO> getContestDetail(@PathVariable("id") Long id) {
        return Result.success(contestService.getContestVO(id));
    }

    @PostMapping("/problem/add")
    @Operation(summary = "添加题目到比赛")
    public Result<Void> addProblemToContest(@RequestBody @Valid ContestProblemAddDTO addDTO) {
        contestService.addProblemToContest(addDTO);
        return Result.success();
    }

    @GetMapping("/{contestId}/problems")
    @Operation(summary = "获取比赛题目列表")
    public Result<List<ContestProblemVO>> getContestProblemList(@PathVariable("contestId") Long contestId) {
        return Result.success(contestService.getContestProblemList(contestId));
    }

    @PostMapping("/problem/remove")
    @Operation(summary = "移除比赛题目")
    public Result<Void> removeContestProblem(@RequestParam Long contestId, @RequestParam Long problemId) {
        contestService.removeContestProblem(contestId, problemId);
        return Result.success();
    }

    @PostMapping("/register/{contestId}")
    @Operation(summary = "报名比赛")
    public Result<Boolean> registerContest(@PathVariable("contestId") Long contestId) {
        // 从 UserContext 获取当前登录用户 ID
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new ContestException(ResultCode.UNAUTHORIZED);
        }
        return Result.success(contestService.registerContest(contestId, userId));
    }
}
