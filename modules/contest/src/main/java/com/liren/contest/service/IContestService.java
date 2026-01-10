package com.liren.contest.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.liren.contest.dto.ContestAddDTO;
import com.liren.contest.dto.ContestProblemAddDTO;
import com.liren.contest.dto.ContestQueryRequest;
import com.liren.contest.entity.ContestEntity;
import com.liren.contest.vo.ContestProblemVO;
import com.liren.contest.vo.ContestVO;

import java.util.List;

public interface IContestService extends IService<ContestEntity> {
    /**
     * 新增或更新比赛
     */
    boolean saveOrUpdateContest(ContestAddDTO contestAddDTO);

    /**
     * 分页查询比赛列表
     */
    Page<ContestVO> listContestVO(ContestQueryRequest queryRequest);

    /**
     * 获取比赛详情
     */
    ContestVO getContestVO(Long contestId);

    /**
     * 添加题目到比赛
     */
    void addProblemToContest(ContestProblemAddDTO addDTO);

    /**
     * 获取比赛的题目列表
     */
    List<ContestProblemVO> getContestProblemList(Long contestId);

    /**
     * 移除比赛题目
     */
    void removeContestProblem(Long contestId, Long problemId);

    /**
     * 注册/报名比赛
     */
    boolean registerContest(Long contestId, Long userId);

    /**
     * 校验用户是否有参赛资格 (供远程调用)
     */
    boolean validateContestPermission(Long contestId, Long userId);

    /**
     * 判断用户是否有访问比赛的权限 (供远程调用)
     */
    boolean hasAccess(Long contestId, Long userId);

    /**
     * 根据题目ID获取比赛ID
     */
    Long getContestIdByProblemId(Long problemId);

    /**
     * 根据contestId判断比赛是否正在进行
     */
    Boolean isContestOngoing(Long contestId);
}
