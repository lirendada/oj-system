package com.liren.problem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liren.api.problem.api.user.UserInterface;
import com.liren.api.problem.dto.problem.ProblemSubmitUpdateDTO;
import com.liren.api.problem.dto.user.UserBasicInfoDTO;
import com.liren.common.core.constant.Constants;
import com.liren.common.core.result.Result;
import com.liren.problem.dto.ProblemAddDTO;
import com.liren.problem.dto.ProblemQueryRequest;
import com.liren.problem.dto.ProblemSubmitDTO;
import com.liren.problem.vo.ProblemDetailVO;
import com.liren.problem.service.IProblemService;
import com.liren.problem.vo.ProblemVO;
import com.liren.problem.vo.RankItemVO;
import com.liren.problem.vo.SubmitRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/problem")
@Tag(name = "题目管理API")
public class ProblemController {
    @Autowired
    private IProblemService problemService;

    @Autowired
    private UserInterface userService;

    // 注入 RankingManager
    @Autowired
    private com.liren.common.redis.RankingManager rankingManager;

    @PostMapping("/add")
    @Operation(
            summary = "新增题目",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "题目信息",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemAddDTO.class)
                    )
            )
    )
    public Result<Boolean> addProblem(@Validated @RequestBody ProblemAddDTO problemAddDTO) {
        return Result.success(problemService.addProblem(problemAddDTO));
    }


    @PostMapping("/list/page")
    @Operation(
            summary = "分页获取题目列表",
            description = "支持条件搜索，返回脱敏数据",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "分页查询条件",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemQueryRequest.class)
                    )
            )
    )
    public Result<Page<ProblemVO>> getProblemList(@RequestBody ProblemQueryRequest queryRequest) {
        // 1. 限制爬虫/恶意请求
        long size = queryRequest.getPageSize();
        if (size > 20) {
            queryRequest.setPageSize(20);
        }

        // 2. 调用 Service
        Page<ProblemVO> page = problemService.getProblemList(queryRequest);

        return Result.success(page);
    }


    @GetMapping("/detail/{problemId}")
    @Operation(summary = "获取题目详情", description = "C端展示题目详情，包含描述、样例、标签等")
    @ApiResponse(responseCode = "200",
            description = "获取题目详情成功",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ProblemDetailVO.class))
    )
    public Result<ProblemDetailVO> getProblemDetail(@PathVariable("problemId") Long problemId) {
        return Result.success(problemService.getProblemDetail(problemId));
    }


    @PostMapping("/submit")
    @Operation(
            summary = "提交代码",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "提交代码信息",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ProblemSubmitDTO.class)
                    )
            )
    )
    public Result<String> submitProblem(@RequestBody @Valid ProblemSubmitDTO problemSubmitDTO) {
        Long submitId = problemService.submitProblem(problemSubmitDTO);
        return Result.success(String.valueOf(submitId));
    }


    @GetMapping("/submit/result/{submitId}")
    @Operation(summary = "查询提交记录详情", description = "包含代码、状态、消耗时间等。非本人查看代码会被隐藏。")
    @ApiResponse(
            responseCode = "200",
            description = "提交成功，返回提交ID",
            content = @Content(
                    mediaType = "application/json",
                    schema = @Schema(
                            type = "integer",
                            format = "int64",
                            description = "submitId",
                            example = "12345"
                    )
            )
    )
    public Result<SubmitRecordVO> getSubmitResult(@PathVariable("submitId") Long submitId) {
        return Result.success(problemService.getSubmitRecord(submitId));
    }


    @GetMapping("/rank/total")
    @Operation(summary = "获取总榜 (Top 10)")
    public Result<List<RankItemVO>> getTotalRank() {
        return Result.success(getRankList(rankingManager.getTotalRankTopN(Constants.RANK_TOTAL_SIZE)));
    }

    @GetMapping("/rank/daily")
    @Operation(summary = "获取日榜 (Top 10)")
    public Result<List<RankItemVO>> getDailyRank() {
        return Result.success(getRankList(rankingManager.getDailyRankTopN(Constants.RANK_TOTAL_SIZE)));
    }

    @GetMapping("/rank/weekly")
    @Operation(summary = "获取周榜 (Top 10)")
    public Result<List<RankItemVO>> getWeeklyRank() {
        return Result.success(getRankList(rankingManager.getWeeklyRankTopN(Constants.RANK_TOTAL_SIZE)));
    }

    @GetMapping("/rank/monthly")
    @Operation(summary = "获取月榜 (Top 10)")
    public Result<List<RankItemVO>> getMonthlyRank() {
        return Result.success(getRankList(rankingManager.getMonthlyRankTopN(Constants.RANK_TOTAL_SIZE)));
    }

    // 封装通用转换逻辑
    private List<RankItemVO> getRankList(Set<ZSetOperations.TypedTuple<Object>> topUserTuples) {
        if (topUserTuples == null || topUserTuples.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 提取所有用户 ID
        List<Long> userIdList = new ArrayList<>();
        for (ZSetOperations.TypedTuple<Object> tuple : topUserTuples) {
            // tuple.getValue() 获取的是 UserId
            userIdList.add(Long.valueOf(tuple.getValue().toString()));
        }

        // 2. 远程批量查询用户信息
        Map<Long, UserBasicInfoDTO> userMap = null;
        try {
            Result<List<UserBasicInfoDTO>> userResult = userService.getBatchUserBasicInfo(userIdList);
            if (userResult.isSuccess(userResult) && userResult.getData() != null) {
                // 转成 Map<UserId, UserDto> 方便后续快速查找
                userMap = userResult.getData().stream()
                        .collect(Collectors.toMap(UserBasicInfoDTO::getId, Function.identity(), (k1, k2) -> k1));
            }
        } catch (Exception e) {
            // 远程调用失败不要崩，降级处理 (只显示ID)
             log.error("远程获取用户信息失败", e);
        }

        // 3. 组装 VO
        List<RankItemVO> resultList = new ArrayList<>();
        for (ZSetOperations.TypedTuple<Object> tuple : topUserTuples) {
            Long userId = Long.valueOf(tuple.getValue().toString());
            Double score = tuple.getScore(); // ✅ 获取当前榜单的真实分数 (日/周 AC数)

            RankItemVO vo = new RankItemVO();
            vo.setUserId(userId);
            // ✅ 核心修复：设置当前榜单的分数，而不是总分
            vo.setAcceptedCount(score != null ? score.intValue() : 0);

            // 填充用户信息
            if (userMap != null && userMap.containsKey(userId)) {
                UserBasicInfoDTO user = userMap.get(userId);
                vo.setNickname(user.getNickname());
                vo.setAvatar(user.getAvatar());
            } else {
                vo.setNickname("用户" + userId);
                vo.setAvatar("");
            }

            resultList.add(vo);
        }
        return resultList;
    }

    /**
     * 【临时测试接口】模拟判题机回调
     * 作用：手动发送判题结果，触发分数计算和榜单更新
     * 注意：测试完成后请删除，或者加上 @Profile("dev") 注解防止生产环境暴露
     */
    @PostMapping("/test/update-result")
    @Operation(summary = "【测试用】模拟判题回调")
    public Result<Boolean> testUpdateResult(@RequestBody ProblemSubmitUpdateDTO updateDTO) {
        // 直接调用 Service 里的核心逻辑
        Boolean success = problemService.updateSubmitResult(updateDTO);
        return Result.success(success);
    }
}
