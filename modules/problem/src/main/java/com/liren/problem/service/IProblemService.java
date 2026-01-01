package com.liren.problem.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.liren.problem.dto.ProblemAddDTO;
import com.liren.problem.dto.ProblemQueryRequest;
import com.liren.problem.dto.ProblemSubmitDTO;
import com.liren.problem.vo.ProblemDetailVO;
import com.liren.problem.entity.ProblemEntity;
import com.liren.problem.vo.ProblemVO;

public interface IProblemService extends IService<ProblemEntity> {
    // 添加题目
    boolean addProblem(ProblemAddDTO problemAddDTO);

    // 获取题目列表
    Page<ProblemVO> getProblemList(ProblemQueryRequest queryRequest);

    // 获取题目详情
    ProblemDetailVO getProblemDetail(Long problemId);

    // 提交题目
    Long submitProblem(ProblemSubmitDTO problemSubmitDTO);
}
