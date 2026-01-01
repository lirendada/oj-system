package com.liren.problem.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.liren.problem.dto.ProblemAddDTO;
import com.liren.problem.dto.ProblemQueryRequest;
import com.liren.problem.entity.ProblemEntity;
import com.liren.problem.vo.ProblemVO;

public interface IProblemService extends IService<ProblemEntity> {
    // 添加题目
    boolean addProblem(ProblemAddDTO problemAddDTO);

    // 查看题目列表
    Page<ProblemVO> getProblemList(ProblemQueryRequest queryRequest);
}
