package com.liren.contest.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liren.api.problem.api.problem.ProblemInterface;
import com.liren.api.problem.api.user.UserInterface;
import com.liren.api.problem.dto.problem.ProblemBasicInfoDTO;
import com.liren.api.problem.dto.user.UserBasicInfoDTO;
import com.liren.common.core.constant.Constants;
import com.liren.common.core.context.UserContext;
import com.liren.common.core.enums.ContestStatusEnum;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.common.redis.RankingManager;
import com.liren.common.redis.RedisUtil;
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
import com.liren.contest.vo.ContestRankVO;
import com.liren.contest.vo.ContestVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ContestServiceImpl extends ServiceImpl<ContestMapper, ContestEntity> implements IContestService {
    @Autowired
    private ContestProblemMapper contestProblemMapper;

    @Autowired
    private ProblemInterface remoteProblemService;

    @Autowired
    private ContestRegistrationMapper contestRegistrationMapper;

    @Autowired
    private RankingManager rankingManager;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private UserInterface userInterface;

    @Autowired
    private RedisUtil redisUtil;

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

        boolean result = this.saveOrUpdate(contest);

        // 3. 如果是更新操作，清除缓存
        if (result && contest.getContestId() != null) {
            String cacheKey = Constants.CONTEST_DETAIL_CACHE_PREFIX + contest.getContestId();
            redisUtil.del(cacheKey);
        }

        return result;
    }

    /**
     * 查询竞赛列表
     * 核心逻辑：
     * 1. 筛选：完全基于 start_time 和 end_time 与当前时间的比较，不依赖数据库 status 字段。
     * 2. 排序：利用 CASE WHEN 语法，将"进行中"的比赛置顶。
     */
    @Override
    public Page<ContestVO> listContestVO(ContestQueryRequest queryRequest) {
        // 使用 QueryWrapper 以便灵活处理 SQL 片段
        QueryWrapper<ContestEntity> queryWrapper = new QueryWrapper<>();

        // 1. 关键词搜索
        if (StrUtil.isNotBlank(queryRequest.getKeyword())) {
            queryWrapper.like("title", queryRequest.getKeyword());
        }

        // 2. 状态筛选 (基于时间)
        LocalDateTime now = LocalDateTime.now();
        if (queryRequest.getStatus() != null) {
            Integer status = queryRequest.getStatus();
            if (ContestStatusEnum.NOT_STARTED.getCode().equals(status)) {
                // 未开始: start_time > now
                queryWrapper.gt("start_time", now);
            } else if (ContestStatusEnum.RUNNING.getCode().equals(status)) {
                // 进行中: start_time <= now <= end_time
                queryWrapper.le("start_time", now).ge("end_time", now);
            } else if (ContestStatusEnum.ENDED.getCode().equals(status)) {
                // 已结束: end_time < now
                queryWrapper.lt("end_time", now);
            }
        }

        // 3. 自定义排序 (基于时间)
        // 目的：让正在进行的比赛排在最前面
        String nowStr = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String orderBySql = String.format(
                "ORDER BY CASE WHEN '%s' >= start_time AND '%s' <= end_time THEN 0 ELSE 1 END ASC, start_time DESC",
                nowStr, nowStr
        );
        queryWrapper.last(orderBySql);

        // 4. 分页查询
        Page<ContestEntity> page = this.page(new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize()), queryWrapper);
        Page<ContestVO> contestVOPage = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());

        // 5. 转换 VO
        List<ContestVO> contestVOS = page.getRecords().stream()
                .map(this::convertContestEntity2ContestVO).collect(Collectors.toList());

        // ✅ 新增逻辑：填充 registered 字段
        // 获取当前登录用户 ID (从 ThreadLocal/Header 中获取)
        Long userId = UserContext.getUserId();

        if (userId != null && !contestVOS.isEmpty()) {
            // 1. 提取当前页所有比赛 ID
            List<Long> contestIds = contestVOS.stream().map(ContestVO::getContestId).collect(Collectors.toList());

            // 2. 批量查询该用户在这些比赛中的报名记录
            LambdaQueryWrapper<ContestRegistrationEntity> regWrapper = new LambdaQueryWrapper<>();
            regWrapper.eq(ContestRegistrationEntity::getUserId, userId)
                    .in(ContestRegistrationEntity::getContestId, contestIds);
            List<ContestRegistrationEntity> registrations = contestRegistrationMapper.selectList(regWrapper);

            // 3. 转为 Set 方便快速查找
            Set<Long> registeredContestIds = registrations.stream()
                    .map(ContestRegistrationEntity::getContestId)
                    .collect(Collectors.toSet());

            // 4. 赋值
            for (ContestVO vo : contestVOS) {
                vo.setRegistered(registeredContestIds.contains(vo.getContestId()));
            }
        } else {
            // 未登录或列表为空，全部设为 false
            for (ContestVO vo : contestVOS) {
                vo.setRegistered(false);
            }
        }

        contestVOPage.setRecords(contestVOS);
        return contestVOPage;
    }

    /**
     * 根据竞赛ID查询竞赛详情
     */
    @Override
    public ContestVO getContestVO(Long contestId) {
        String cacheKey = Constants.CONTEST_DETAIL_CACHE_PREFIX + contestId;

        // 1. 尝试从 Redis 获取 Entity (注意：是 Entity，不是 VO)
        ContestEntity contestEntity = redisUtil.get(cacheKey, ContestEntity.class);

        // 2. 如果缓存没命中，查 DB 并写入缓存
        if (contestEntity == null) {
            contestEntity = this.getById(contestId);
            if (contestEntity == null) {
                throw new ContestException(ResultCode.CONTEST_NOT_FOUND);
            }
            // 缓存 Entity，它是静态的，只要管理员不改比赛信息，它就不会变
            redisUtil.set(cacheKey, contestEntity, Constants.CONTEST_DETAIL_CACHE_EXPIRE_TIME);
        }

        // 3. 【核心修复】无论数据来自 Redis 还是 DB，都在这里现场计算状态
        // 这样就能保证 status 永远是基于 LocalDateTime.now() 的最新值
        ContestVO vo = this.convertContestEntity2ContestVO(contestEntity);

        // 补充：检查当前用户是否已报名
        Long userId = UserContext.getUserId();
        if (userId != null) {
            vo.setRegistered(this.isUserRegistered(contestId, userId));
        } else {
            vo.setRegistered(false);
        }

        return vo;
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
        // 1. 获取当前用户和比赛信息
        Long userId = UserContext.getUserId();
        String userRole = UserContext.getUserRole();
        boolean isAdmin = "admin".equals(userRole);

        ContestEntity contest = this.getById(contestId);
        if (contest == null) {
            throw new ContestException(ResultCode.CONTEST_NOT_FOUND);
        }

        // 2. 权限校验 (非管理员需要校验)
        if (!isAdmin) {
            LocalDateTime now = LocalDateTime.now();

            // A. 比赛未开始：绝对禁止查看
            if (contest.getStartTime().isAfter(now)) {
                throw new ContestException(ResultCode.CONTEST_NOT_STARTED);
            }

            // B. 比赛进行中：必须报名才能查看
            if (contest.getStartTime().isBefore(now) && contest.getEndTime().isAfter(now)) {
                // 未登录
                if (userId == null) {
                    throw new ContestException(ResultCode.UNAUTHORIZED);
                }
                // 未报名
                if (!isUserRegistered(contestId, userId)) {
                    throw new ContestException(ResultCode.USER_NOT_REGISTERED_CONTEST);
                }
            }
            // C. 比赛已结束：默认公开 (不做限制)
        }


        // 3. 查出该比赛所有的题目关联
        LambdaQueryWrapper<ContestProblemEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestProblemEntity::getContestId, contestId);
        wrapper.orderByAsc(ContestProblemEntity::getDisplayId); // 按 A, B, C 排序
        List<ContestProblemEntity> entities = contestProblemMapper.selectList(wrapper);

        if (entities.isEmpty()) {
            return Collections.emptyList();
        }

        // 4. 转换为 VO 并填充题目详情
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

        // 5. 更新缓存（报名成功后缓存为 true）
        if (insert == 1) {
            String cacheKey = Constants.CONTEST_REGISTRATION_CACHE_PREFIX + contestId + ":" + userId;
            redisUtil.set(cacheKey, true, Constants.CONTEST_REGISTRATION_CACHE_EXPIRE_TIME);
        }

        return insert == 1;
    }

    /**
     * 校验用户是否有参赛资格 (供提交代码远程调用)
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
     * 判断用户是否有访问比赛的权限 (供查看题目详情远程调用)
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
            log.info("比赛结束了，允许查看");
            return true;
        }

        // 2.2 比赛未开始 -> 禁止查看
        if (contest.getStartTime().isAfter(now)) {
            log.info("比赛未开始，禁止查看题目");
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
     * 获取比赛排名
     */
    @Override
    public List<ContestRankVO> getContestRank(Long contestId) {
        // 1. 取前 50 名 (Tuple 包含 value=userId, score=totalScore)
        // 使用 rangeWithScores 可以直接拿到分数，不用再查一次
        Set<ZSetOperations.TypedTuple<Object>> topUsers =
                redisTemplate.opsForZSet().reverseRangeWithScores(Constants.RANK_CONTEST_PREFIX + contestId, 0, 50);

        if (topUsers == null || topUsers.isEmpty()) {
            return new ArrayList<>();
        }

        List<ContestRankVO> resultList = new ArrayList<>();
        int rank = 1;

        // 2. 收集所有 UserId 准备批量查用户信息 (优化点)
        List<Long> userIds = new ArrayList<>();

        for (ZSetOperations.TypedTuple<Object> tuple : topUsers) {
            Long userId = Long.valueOf(tuple.getValue().toString());
            userIds.add(userId);

            ContestRankVO vo = new ContestRankVO();
            vo.setUserId(userId);
            vo.setRank(rank++);
            vo.setTotalScore(tuple.getScore().intValue()); // ZSet 的分数即为总分

            // 3. 【核心】获取该用户的题目得分详情 (查 Redis Hash)
            Map<Object, Object> detailMap = rankingManager.getUserScoreDetail(contestId, userId);

            // 转换 Map<Object, Object> -> Map<Long, Integer>
            Map<Long, Integer> scoreMap = new HashMap<>();
            if (detailMap != null) {
                detailMap.forEach((k, v) -> {
                    scoreMap.put(Long.valueOf(k.toString()), Integer.parseInt(v.toString()));
                });
            }
            vo.setProblemScores(scoreMap);

            resultList.add(vo);
        }

        // 4. 填充用户信息 (昵称、头像)
        // 批量查询用户信息
        Result<List<UserBasicInfoDTO>> userRes = userInterface.getBatchUserBasicInfo(userIds);

        if (Result.isSuccess(userRes) && userRes.getData() != null) {
            // 【核心步骤】将 List 转为 Map<UserId, UserDTO>，实现 O(1) 查找
            Map<Long, UserBasicInfoDTO> userMap = userRes.getData().stream()
                    .collect(Collectors.toMap(
                            UserBasicInfoDTO::getId,  // Key: 用户ID
                            user -> user,                 // Value: 用户对象本身
                            (existing, replacement) -> existing // 假如(万一)有重复key，保留旧的
                    ));

            // 遍历榜单，从 Map 中快速填入信息
            for (ContestRankVO vo : resultList) {
                UserBasicInfoDTO userInfo = userMap.get(vo.getUserId());
                if (userInfo != null) {
                    vo.setNickname(userInfo.getNickname());
                    vo.setAvatar(userInfo.getAvatar());
                } else {
                    // 兜底逻辑：查不到就给默认名
                    vo.setNickname("用户" + vo.getUserId());
                }
            }
        } else {
            // 假如批量查询服务挂了，也要给个默认值，别让接口崩
            for (ContestRankVO vo : resultList) {
                vo.setNickname("用户" + vo.getUserId());
            }
        }

        return resultList;
    }

    /**
     * 判断比赛是否已结束
     */
    @Override
    public Boolean isContestEnded(Long contestId) {
        ContestEntity contest = this.getById(contestId);
        if(contest == null) {
            return false; // 或者抛异常，视业务而定，这里返回 false 比较安全
        }
        return contest.getEndTime().isBefore(LocalDateTime.now());
    }


    /**
     * 判断用户是否已报名
     */
    private boolean isUserRegistered(Long contestId, Long userId) {
        if (userId == null) {
            return false;
        }

        // 1. 先从缓存中查询报名状态
        String cacheKey = Constants.CONTEST_REGISTRATION_CACHE_PREFIX + contestId + ":" + userId;
        Object cached = redisUtil.get(cacheKey);
        if (cached != null) {
            // 缓存命中
            return Boolean.parseBoolean(cached.toString());
        }

        // 2. 缓存未命中，查询数据库
        LambdaQueryWrapper<ContestRegistrationEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ContestRegistrationEntity::getUserId, userId)
                .eq(ContestRegistrationEntity::getContestId, contestId);
        ContestRegistrationEntity registration = contestRegistrationMapper.selectOne(wrapper);
        boolean isRegistered = registration != null;

        // 3. 写入缓存（1小时过期）
        redisUtil.set(cacheKey, isRegistered, Constants.CONTEST_REGISTRATION_CACHE_EXPIRE_TIME);

        return isRegistered;
    }

    /**
     * 转换ContestEntity到ContestVO
     */
    private ContestVO convertContestEntity2ContestVO(ContestEntity entity) {
        ContestVO contestVO = new ContestVO();
        BeanUtil.copyProperties(entity, contestVO);

        // 2. 设置状态描述
        // ✅ 必须基于时间重新计算，确保和 list 筛选逻辑一致
        LocalDateTime startTime = entity.getStartTime();
        LocalDateTime endTime = entity.getEndTime();
        LocalDateTime now = LocalDateTime.now();
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

        // 3. 计算持续时间
        if (entity.getStartTime() != null && entity.getEndTime() != null) {
            Duration duration = Duration.between(entity.getStartTime(), entity.getEndTime());
            long hours = duration.toHours();
            long minutes = duration.toMinutes() % 60;
            contestVO.setDuration(String.format("%d小时%d分", hours, minutes));
        }

        return contestVO;
    }
}
