package com.liren.contest.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liren.api.problem.api.problem.ProblemInterface;
import com.liren.api.problem.dto.problem.ProblemBasicInfoDTO;
import com.liren.common.core.enums.ContestStatusEnum;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.contest.dto.ContestAddDTO;
import com.liren.contest.dto.ContestProblemAddDTO;
import com.liren.contest.dto.ContestQueryRequest;
import com.liren.contest.entity.ContestEntity;
import com.liren.contest.entity.ContestProblemEntity;
import com.liren.contest.entity.ContestRegistrationEntity;
import com.liren.contest.exception.ContestException;
import com.liren.contest.mapper.ContestMapper;
import com.liren.contest.mapper.ContestProblemMapper;
import com.liren.contest.mapper.ContestRegistrationMapper;
import com.liren.contest.service.IContestService;
import com.liren.contest.vo.ContestProblemVO;
import com.liren.contest.vo.ContestVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ContestServiceImpl extends ServiceImpl<ContestMapper, ContestEntity> implements IContestService {
    @Autowired
    private ContestProblemMapper contestProblemMapper;

    @Autowired
    private ProblemInterface remoteProblemService;

    @Autowired
    private ContestRegistrationMapper contestRegistrationMapper;

    /**
     * 新增或修改竞赛信息
     */
    @Override
    public boolean saveOrUpdateContest(ContestAddDTO contestAddDTO) {
        // 1. 校验时间
        if(contestAddDTO.getStartTime().isAfter(contestAddDTO.getEndTime())) {
            throw new ContestException(ResultCode.CONTEST_TIME_ERROR);
        }

        // 2. 保存或修改竞赛信息
        ContestEntity contest = new ContestEntity();
        BeanUtil.copyProperties(contestAddDTO, contest);
        contest.setStatus(ContestStatusEnum.NOT_STARTED.getCode());

        return this.saveOrUpdate(contest);
    }

    /**
     * 查询竞赛列表
     */
    @Override
    public Page<ContestVO> listContestVO(ContestQueryRequest queryRequest) {
        LambdaQueryWrapper<ContestEntity> wrapper = new LambdaQueryWrapper<>();

        // 关键词搜索
        if(StrUtil.isNotBlank(queryRequest.getKeyword())) {
            wrapper.like(ContestEntity::getTitle, queryRequest.getKeyword());
        }

        // 状态搜索
        // 0-未开始: startTime > now
        // 1-进行中: startTime <= now && endTime >= now
        // 2-已结束: endTime < now
        if(queryRequest.getStatus() != null) {
            LocalDateTime now = LocalDateTime.now();
            if(ContestStatusEnum.NOT_STARTED.getCode().equals(queryRequest.getStatus())) {
                wrapper.gt(ContestEntity::getStartTime, now);
            } else if(queryRequest.getStatus().equals(ContestStatusEnum.RUNNING.getCode())) {
                wrapper.le(ContestEntity::getStartTime, now).ge(ContestEntity::getEndTime, now);
            } else {
                wrapper.lt(ContestEntity::getEndTime, now);
            }
        }

        // 按照时间倒序排序
        wrapper.orderByDesc(ContestEntity::getStartTime);

        // 分页查询
        Page<ContestEntity> page = this.page(new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize()), wrapper);
        Page<ContestVO> contestVOPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());

        // 转换成VO对象并设置状态
        List<ContestVO> contestVOS = page.getRecords().stream()
                .map(this::convertContestEntity2ContestVO).collect(Collectors.toList());

        contestVOPage.setRecords(contestVOS);
        return contestVOPage;
    }

    /**
     * 根据竞赛ID查询竞赛详情
     */
    @Override
    public ContestVO getContestVO(Long contestId) {
        ContestEntity contestEntity = this.getById(contestId);
        if(contestEntity == null) {
            throw new ContestException(ResultCode.CONTEST_NOT_FOUND);
        }
        return this.convertContestEntity2ContestVO(contestEntity);
    }

    /**
     * 添加题目到竞赛
     */
    @Override
    public void addProblemToContest(ContestProblemAddDTO addDTO) {
        // 1. 校验比赛是否存在
        ContestEntity contest = this.getById(addDTO.getContestId());
        if (contest == null) {
            throw new ContestException(ResultCode.CONTEST_NOT_FOUND);
        }

        // 2. 校验题目是否存在 (远程调用)
        Result<ProblemBasicInfoDTO> problemResult = remoteProblemService.getProblemBasicInfo(addDTO.getProblemId());
        if (problemResult.getData() == null) {
            throw new ContestException(ResultCode.SUBJECT_NOT_FOUND);
        }

        // 3. 检验是否重复添加
        LambdaQueryWrapper<ContestProblemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestProblemEntity::getContestId, addDTO.getContestId())
               .eq(ContestProblemEntity::getProblemId, addDTO.getProblemId());
        if(contestProblemMapper.selectCount(wrapper) > 0) {
            throw new ContestException(ResultCode.CONTEST_PROBLEM_ALREADY_EXIST);
        }

        // 4. 添加题目到竞赛
        ContestProblemEntity contestProblemEntity = new ContestProblemEntity();
        BeanUtil.copyProperties(addDTO, contestProblemEntity);
        contestProblemMapper.insert(contestProblemEntity);
    }

    /**
     * 获取竞赛题目列表
     */
    @Override
    public List<ContestProblemVO> getContestProblemList(Long contestId) {
        // 1. 查出该比赛所有的题目关联
        LambdaQueryWrapper<ContestProblemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestProblemEntity::getContestId, contestId);
        wrapper.orderByAsc(ContestProblemEntity::getDisplayId); // 按 A, B, C 排序
        List<ContestProblemEntity> entities = contestProblemMapper.selectList(wrapper);

        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 2. 转换为 VO 并填充题目详情
        return entities.stream().map(entity -> {
            ContestProblemVO vo = new ContestProblemVO();
            BeanUtil.copyProperties(entity, vo);

            // TODO: 远程调用查标题 (注意：这里是循环调用，性能较差，后期可用 Feign 批量查询接口优化)
            try {
                Result<ProblemBasicInfoDTO> result = remoteProblemService.getProblemBasicInfo(entity.getProblemId());
                if (result.getData() != null) {
                    vo.setTitle(result.getData().getTitle());
                    vo.setDifficulty(result.getData().getDifficulty());
                }
            } catch (Exception e) {
                vo.setTitle("题目信息加载失败"); // 降级处理
                log.error("远程查询题目失败", e);
            }
            return vo;
        }).collect(Collectors.toList());
    }

    /**
     * 删除竞赛题目
     */
    @Override
    public void removeContestProblem(Long contestId, Long problemId) {
        LambdaQueryWrapper<ContestProblemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestProblemEntity::getContestId, contestId)
                .eq(ContestProblemEntity::getProblemId, problemId);
        contestProblemMapper.delete(wrapper);
    }

    /**
     * 注册/报名比赛
     */
    @Override
    public boolean registerContest(Long contestId, Long userId) {
        // 1. 校验比赛是否存在
        ContestEntity contest = this.getById(contestId);
        if(contest == null) {
            throw new ContestException(ResultCode.CONTEST_NOT_FOUND);
        }

        // 2. 校验比赛状态 (依赖时间，而不是依赖数据库的 status 字段)，只要没结束，就可以报名（支持 提前报名 + 赛中报名）
        LocalDateTime now = LocalDateTime.now();
        if(contest.getEndTime().isBefore(now)) {
            throw new ContestException(ResultCode.CONTEST_IS_ENDED);
        }

        // 3. 校验是否重复报名
        LambdaQueryWrapper<ContestRegistrationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestRegistrationEntity::getUserId, userId)
                .eq(ContestRegistrationEntity::getContestId, contestId);
        ContestRegistrationEntity contestRegistration = contestRegistrationMapper.selectOne(wrapper);
        if(contestRegistration != null) {
            throw new ContestException(ResultCode.USER_ALREADY_REGISTERED_CONTEST);
        }

        // 4. 保存报名信息
        ContestRegistrationEntity registration = new ContestRegistrationEntity();
        registration.setContestId(contestId);
        registration.setUserId(userId);
        int insert = contestRegistrationMapper.insert(registration);
        return insert == 1;
    }

    /**
     * 校验用户是否有参赛资格 (供远程调用)
     */
    @Override
    public boolean validateContestPermission(Long contestId, Long userId) {
        // 1. 校验比赛是否存在
        ContestEntity contest = this.getById(contestId);
        if(contest == null) {
            throw new ContestException(ResultCode.CONTEST_NOT_FOUND);
        }

        // 2. 校验比赛状态 (依赖时间，而不是依赖数据库的 status 字段)
        LocalDateTime now = LocalDateTime.now();
        if(contest.getEndTime().isBefore(now)) {
            throw new ContestException(ResultCode.CONTEST_IS_ENDED);
        }
        if(contest.getStartTime().isAfter(now)) {
            throw new ContestException(ResultCode.CONTEST_NOT_STARTED);
        }

        // 3. 校验用户是否已报名
        if(!isUserRegistered(contestId, userId)) {
            throw new ContestException(ResultCode.USER_NOT_REGISTERED_CONTEST);
        }
        return true;
    }

    /**
     * 判断用户是否有访问比赛的权限 (供远程调用)
     */
    @Override
    public boolean hasAccess(Long contestId, Long userId) {
        // 1. 校验比赛是否存在
        ContestEntity contest = this.getById(contestId);
        if(contest == null) {
            throw new ContestException(ResultCode.CONTEST_NOT_FOUND);
        }

        // 2. 检查比赛状态
        // 2.1 比赛结束 -> 允许查看
        LocalDateTime now = LocalDateTime.now();
        if (contest.getEndTime().isBefore(now)) {
            return true;
        }

        // 2.2 比赛未开始 -> 禁止查看
        if (contest.getStartTime().isAfter(now)) {
            return false;
        }

        // 2.3 正在进行中 -> 报名才能查看
        return isUserRegistered(contestId, userId);
    }

    /**
     * 根据题目ID获取所在的竞赛ID (供远程调用)
     */
    @Override
    public Long getContestIdByProblemId(Long problemId) {
        LambdaQueryWrapper<ContestProblemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestProblemEntity::getProblemId, problemId);
        ContestProblemEntity entity = contestProblemMapper.selectOne(wrapper);
        if(entity == null) {
            return null;
        }
        return entity.getContestId();
    }

    /**
     * 根据contestId判断比赛是否正在进行
     */
    @Override
    public Boolean isContestOngoing(Long contestId) {
        ContestEntity contest = this.getById(contestId);
        if(contest == null) {
            return false;
        }

        LocalDateTime now = LocalDateTime.now();
        if(contest.getStartTime().isBefore(now) && contest.getEndTime().isAfter(now)) {
            return true;
        }
        return false;
    }


    /**
     * 判断用户是否已报名
     */
    private boolean isUserRegistered(Long contestId, Long userId) {
        // TODO：建议加个缓存，不要每次都查库
        if (userId == null) {
            return false;
        }
        LambdaQueryWrapper<ContestRegistrationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestRegistrationEntity::getUserId, userId)
                .eq(ContestRegistrationEntity::getContestId, contestId);
        ContestRegistrationEntity registration = contestRegistrationMapper.selectOne(wrapper);
        if(registration == null) {
            return false;
        }
        return true;
    }

    /**
     * 转换ContestEntity到ContestVO
     */
    private ContestVO convertContestEntity2ContestVO(ContestEntity entity) {
        ContestVO contestVO = new ContestVO();
        BeanUtil.copyProperties(entity, contestVO);

        LocalDateTime startTime = entity.getStartTime();
        LocalDateTime endTime = entity.getEndTime();
        LocalDateTime now = LocalDateTime.now();

        // 动态设置状态
        // 0-未开始: startTime > now
        // 1-进行中: startTime <= now && endTime >= now
        // 2-已结束: endTime < now
        if (startTime.isAfter(now)) {
            contestVO.setStatus(ContestStatusEnum.NOT_STARTED.getCode());
            contestVO.setStatusDesc(ContestStatusEnum.NOT_STARTED.getMessage());
        } else if (startTime.isBefore(now) && endTime.isAfter(now)) {
            contestVO.setStatus(ContestStatusEnum.RUNNING.getCode());
            contestVO.setStatusDesc(ContestStatusEnum.RUNNING.getMessage());
        } else {
            contestVO.setStatus(ContestStatusEnum.ENDED.getCode());
            contestVO.setStatusDesc(ContestStatusEnum.ENDED.getMessage());
        }

        Duration duration = Duration.between(startTime, endTime);
        long hours = duration.toHours();
        long minutes = duration.toMinutes() % 60;
        contestVO.setDuration(String.format("%d小时%d分", hours, minutes));

        return contestVO;
    }
}
