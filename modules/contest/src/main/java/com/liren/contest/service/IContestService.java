package com.liren.contest.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.liren.contest.dto.ContestAddDTO;
import com.liren.contest.dto.ContestQueryRequest;
import com.liren.contest.entity.ContestEntity;
import com.liren.contest.vo.ContestVO;

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
}
