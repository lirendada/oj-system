package com.liren.problem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liren.common.core.result.Result;
import com.liren.problem.dto.ProblemAddDTO;
import com.liren.problem.dto.ProblemQueryRequest;
import com.liren.problem.dto.ProblemSubmitDTO;
import com.liren.problem.vo.ProblemDetailVO;
import com.liren.problem.service.IProblemService;
import com.liren.problem.vo.ProblemVO;
import com.liren.problem.vo.SubmitRecordVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/problem")
@Tag(name = "题目管理API")
public class ProblemController {
    @Autowired
    private IProblemService problemService;

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
    public Result<Long> submitProblem(@RequestBody @Valid ProblemSubmitDTO problemSubmitDTO) {
        return Result.success(problemService.submitProblem(problemSubmitDTO));
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
}
