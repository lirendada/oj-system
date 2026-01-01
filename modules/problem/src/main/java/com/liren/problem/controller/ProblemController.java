package com.liren.problem.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.liren.common.core.result.Result;
import com.liren.problem.dto.ProblemAddDTO;
import com.liren.problem.dto.ProblemQueryRequest;
import com.liren.problem.service.IProblemService;
import com.liren.problem.vo.ProblemVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
    public Result<Page<ProblemVO>> listProblemVOByPage(@RequestBody ProblemQueryRequest queryRequest) {
        // 1. 限制爬虫/恶意请求
        long size = queryRequest.getPageSize();
        if (size > 20) {
            queryRequest.setPageSize(20);
        }

        // 2. 调用 Service
        Page<ProblemVO> page = problemService.getProblemList(queryRequest);

        return Result.success(page);
    }
}
