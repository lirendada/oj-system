package com.liren.api.problem.api;

import com.liren.api.problem.dto.ProblemBasicInfoDTO;
import com.liren.api.problem.dto.ProblemSubmitUpdateDTO;
import com.liren.api.problem.dto.SubmitRecordDTO;
import com.liren.api.problem.dto.TestCaseDTO;
import com.liren.common.core.result.Result;
import lombok.Getter;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.List;

/**
 * contextId: 用于区分同一个服务名的不同 Client (防止Bean冲突)
 * name: 目标服务名 (problem-service)
 * path: 统一前缀 (建议内部接口加 /inner 区分)
 */
@FeignClient(contextId = "ProblemInterface",name = "problem-service", path = "/problem/inner")
public interface ProblemInterface {

    /**
     * 更新提交结果
     */
    @PostMapping("/submit/update")
    Result<Boolean> updateSubmitResult(@RequestBody ProblemSubmitUpdateDTO problemSubmitUpdateDTO);

    /**
     * 获取测试用例
     */
    @GetMapping("/test-case/{problemId}")
    Result<List<TestCaseDTO>> getTestCases(@PathVariable("problemId") Long problemId);

    /**
     * 获取提交记录
     */
    @GetMapping("/submit/{submitId}")
    Result<SubmitRecordDTO> getSubmitRecord(@PathVariable("submitId") Long submitId);

    /**
     * 获取题目基本信息 (用于比赛服务调用)
     */
    @GetMapping("/contest/brief/{problemId}")
    Result<ProblemBasicInfoDTO> getProblemBasicInfo(@PathVariable("problemId") Long problemId);
}
