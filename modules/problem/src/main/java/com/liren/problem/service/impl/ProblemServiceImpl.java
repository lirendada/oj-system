package com.liren.problem.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liren.api.problem.api.contest.ContestInterface;
import com.liren.api.problem.api.user.UserInterface;
import com.liren.api.problem.dto.problem.ProblemBasicInfoDTO;
import com.liren.api.problem.dto.problem.ProblemSubmitUpdateDTO;
import com.liren.api.problem.dto.problem.SubmitRecordDTO;
import com.liren.api.problem.dto.problem.TestCaseDTO;
import com.liren.common.core.constant.Constants;
import com.liren.common.core.context.UserContext;
import com.liren.common.core.enums.JudgeResultEnum;
import com.liren.common.core.enums.ProblemStatusEnum;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.common.redis.RankingManager;
import com.liren.problem.dto.ProblemAddDTO;
import com.liren.problem.dto.ProblemQueryRequest;
import com.liren.problem.dto.ProblemSubmitDTO;
import com.liren.problem.entity.*;
import com.liren.problem.mapper.*;
import com.liren.problem.vo.ProblemDetailVO;
import com.liren.problem.exception.ProblemException;
import com.liren.problem.service.IProblemService;
import com.liren.problem.vo.ProblemTagVO;
import com.liren.problem.vo.ProblemVO;
import com.liren.problem.vo.SubmitRecordVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ProblemServiceImpl extends ServiceImpl<ProblemMapper, ProblemEntity> implements IProblemService {
    @Autowired
    private ProblemTagRelationMapper problemTagRelationMapper;

    @Autowired
    private ProblemTagMapper problemTagMapper;

    @Autowired
    private ProblemSubmitMapper problemSubmitMapper;

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private TestCaseMapper testCaseMapper;

    @Autowired
    private ContestInterface contestService;

    @Autowired
    private RankingManager rankingManager;
    @Autowired
    private UserInterface userInterface;

    /**
     * 新增题目
     */
    @Override
    @Transactional(rollbackFor = Exception.class) // 开启事务，保证原子性
    public boolean addProblem(ProblemAddDTO problemAddDTO) {
        // =============== 检查标题是否已存在 ===============
        // 如果是新增（ID为空），或者更新（ID不为空），都需要校验标题唯一性
        LambdaQueryWrapper<ProblemEntity> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ProblemEntity::getTitle, problemAddDTO.getTitle());

        // 如果是更新操作，需要排除掉“自己”，否则自己改自己会报错
        if (problemAddDTO.getProblemId() != null) {
            queryWrapper.ne(ProblemEntity::getProblemId, problemAddDTO.getProblemId());
        }

        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new ProblemException(ResultCode.SUBJECT_TITLE_EXIST);
        }
        // ===========================================

        // 1. DTO转Entity，需要单独设置状态
        ProblemEntity problemEntity = new ProblemEntity();
        BeanUtil.copyProperties(problemAddDTO, problemEntity);
        if(problemEntity.getStatus() == null) {
            problemEntity.setStatus(1);
        }

        // 2. 保存实体
        boolean isSave = this.save(problemEntity);
        if (!isSave) {
            throw new ProblemException(ResultCode.SUBJECT_NOT_FOUND);
        }

        // 3. 保存标签关系(如果有)
        Long problemId = problemEntity.getProblemId();
        List<Long> tagIds = problemAddDTO.getTagIds();
        if(CollectionUtil.isNotEmpty(tagIds)) {
            List<ProblemTagRelationEntity> relationList = tagIds.stream().map(tagId -> {
                ProblemTagRelationEntity problemTagRelationEntity = new ProblemTagRelationEntity();
                problemTagRelationEntity.setProblemId(problemId);
                problemTagRelationEntity.setTagId(tagId);
                return problemTagRelationEntity;
            }).collect(Collectors.toList());

            // 保存标签关系
            problemTagRelationMapper.saveBatch(relationList);
        }

        // 4. 保存测试样例
        List<TestCaseDTO> testCases = problemAddDTO.getTestCases();
        if(CollectionUtil.isNotEmpty(testCases)) {
            List<TestCaseEntity> testCaseEntities = testCases.stream().map(entity -> {
                TestCaseEntity testCaseEntity = new TestCaseEntity();
                testCaseEntity.setProblemId(problemId);
                testCaseEntity.setInput(entity.getInput());
                testCaseEntity.setOutput(entity.getOutput());
                return testCaseEntity;
            }).collect(Collectors.toList());

            testCaseMapper.saveBatch(testCaseEntities);
        }

        return true;
    }


    /**
     * 分页查询题目列表 (支持多标签筛选 + 批量填充)
     */
    @Override
    public Page<ProblemVO> getProblemList(ProblemQueryRequest queryRequest) {
        // === 1. 找出所有满足tags条件的题目id ===
        Set<Long> filterProblemIds = null; // 之所以不用List，是因为可能有重复的题目
        if(CollectionUtil.isNotEmpty(queryRequest.getTags())) {
            List<String> tags = queryRequest.getTags();

            // 1.1 查出所有标签id
            LambdaQueryWrapper<ProblemTagEntity> tagWrapper = new LambdaQueryWrapper<>();
            tagWrapper.in(ProblemTagEntity::getTagName, tags);
            List<ProblemTagEntity> tagEntities = problemTagMapper.selectList(tagWrapper);

            // 如果数据库里存在的标签数量 < 用户请求的数量，说明必然无法满足 "包含所有标签" 的条件，直接返回空数据
            if(tagEntities.size() < tags.size()) {
                return new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize());
            }
            List<Long> tagIds = tagEntities.stream().map(ProblemTagEntity::getTagId).collect(Collectors.toList());

            // 1.2 找出同时包含这些 tagId 的题目 problemId
            // 查出所有关联记录
            LambdaQueryWrapper<ProblemTagRelationEntity> relationWrapper = new LambdaQueryWrapper<>();
            relationWrapper.in(ProblemTagRelationEntity::getTagId, tagIds);
            List<ProblemTagRelationEntity> problemTagRelationEntities = problemTagRelationMapper.selectList(relationWrapper);

            // 分组统计每个problemId的出现次数：Map<ProblemId, 命中标签次数>
            Map<Long, Long> collect = problemTagRelationEntities.stream()
                    .collect(Collectors.groupingBy(ProblemTagRelationEntity::getProblemId, Collectors.counting()));

            // 只有命中标签次数 == 请求标签数量的题目，才是符合条件的题目
            filterProblemIds = collect.entrySet().stream()
                    .filter(entry -> entry.getValue() == tags.size())
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toSet());
            if(filterProblemIds.isEmpty()) {
                return new Page<>(queryRequest.getCurrent(), queryRequest.getPageSize());
            }
        }

        // === 2. 构建其它查询条件 ===
        long current = queryRequest.getCurrent();
        long size = queryRequest.getPageSize();
        LambdaQueryWrapper<ProblemEntity> wrapper = new LambdaQueryWrapper<>();

        // 根据上面标签筛选得到的题目id集合，加入查询条件
        if(filterProblemIds != null) {
            wrapper.in(ProblemEntity::getProblemId, filterProblemIds);
        }

        // 题目id匹配
        if(queryRequest.getProblemId() != null) {
            wrapper.eq(ProblemEntity::getProblemId, queryRequest.getProblemId());
        }

        // 难度匹配
        if(queryRequest.getDifficulty() != null) {
            wrapper.eq(ProblemEntity::getDifficulty, queryRequest.getDifficulty());
        }

        // 标题/内容模糊匹配(同时查标题或内容)
        String keyword = queryRequest.getKeyword();
        if(StringUtils.hasText(keyword)) {
            // 注意这里要先and，再or，而不能直接or
            wrapper.and(qw -> qw.like(ProblemEntity::getTitle, keyword))
                    .or()
                    .like(ProblemEntity::getDescription, keyword);
        }

        // 过滤掉隐藏的题目
        wrapper.eq(ProblemEntity::getStatus, ProblemStatusEnum.NORMAL.getCode());

        // 处理排序
        String sortField = queryRequest.getSortField();
        String sortOrder = queryRequest.getSortOrder();
        if (StringUtils.hasText(sortField)) {
            // 这里的 equals 要根据你具体的业务需求，比如前端传 "createTime"
            boolean isAsc = "ascend".equals(sortOrder);
            if ("createTime".equals(sortField)) {
                wrapper.orderBy(true, isAsc, ProblemEntity::getCreateTime);
            } else if ("submitNum".equals(sortField)) {
                wrapper.orderBy(true, isAsc, ProblemEntity::getSubmitNum);
            }
        } else {
            // 默认排序：按创建时间倒序
            wrapper.orderByDesc(ProblemEntity::getCreateTime);
        }

        // ================= 3. 执行分页查询 =================
        Page<ProblemEntity> problemEntityPage = this.page(new Page<>(current, size), wrapper);
        List<ProblemEntity> records = problemEntityPage.getRecords();
        if (CollectionUtil.isEmpty(records)) {
            return new Page<>(current, size, problemEntityPage.getTotal());
        }

        // ================= 4. 批量填充标签 (Filling) =================
        // 1. 找出所有题目id
        List<Long> pids = records.stream().map(ProblemEntity::getProblemId).collect(Collectors.toList());

        // 2. 找出所有题目和标签的关联记录
        LambdaQueryWrapper<ProblemTagRelationEntity> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.in(ProblemTagRelationEntity::getProblemId, pids);
        List<ProblemTagRelationEntity> relationList = problemTagRelationMapper.selectList(relationWrapper);

        // 3. 先查出所有 Tag 详情，存放到哈希表中（如果后面遍历题目再每个题目去搜索 Tag 详情，会导致性能问题）
        Map<Long, ProblemTagVO> tagVoMap = new HashMap<>(); // Key: TagId, Value: ProblemTagVO
        if (CollectionUtil.isNotEmpty(relationList)) {
            Set<Long> allTagIds = relationList.stream().map(ProblemTagRelationEntity::getTagId).collect(Collectors.toSet());
            if (CollectionUtil.isNotEmpty(allTagIds)) {
                List<ProblemTagEntity> tags = problemTagMapper.selectBatchIds(allTagIds);
                // 将 Entity 转为 VO 并存入 Map
                tagVoMap = tags.stream().collect(Collectors.toMap(
                        ProblemTagEntity::getTagId,
                        entity -> {
                            ProblemTagVO vo = new ProblemTagVO();
                            vo.setTagId(entity.getTagId());
                            vo.setTagName(entity.getTagName());
                            vo.setTagColor(entity.getTagColor()); // 关键：拿到颜色！
                            return vo;
                        }
                ));
            }
        }

        // 4. 将题目和标签关系进行分组
        Map<Long, List<ProblemTagVO>> pTagMap = new HashMap<>(); // Key: ProblemId, Value: List<ProblemTagVO>
        for (ProblemTagRelationEntity r : relationList) {
            ProblemTagVO tagVO = tagVoMap.get(r.getTagId()); // 有了哈希表，直接从里面取，效率高
            if (tagVO != null) {
                pTagMap.computeIfAbsent(r.getProblemId(), k -> new ArrayList<>()).add(tagVO);
            }
        }

        // ================= 5. 组装 VO =================
        Page<ProblemVO> problemVOPage = new Page<>(current, size, problemEntityPage.getTotal());
        List<ProblemVO> collect = records.stream()
                .map(entity -> {
                    ProblemVO problemVO = ProblemVO.objToVo(entity);
                    problemVO.setTags(pTagMap.get(entity.getProblemId()));
                    return problemVO;
                }).collect(Collectors.toList());
        problemVOPage.setRecords(collect);
        return problemVOPage;
    }


    /**
     * 获取题目详情
     */
    @Override
    public ProblemDetailVO getProblemDetail(Long problemId) {
        // 1. 先查出题目
        ProblemEntity problemEntity = this.getById(problemId);
        if(problemEntity == null) {
            throw new ProblemException(ResultCode.SUBJECT_NOT_FOUND);
        }

        // 先获取用户信息
        Long userId = UserContext.getUserId();
        String userRole = UserContext.getUserRole();
        boolean isAdmin = "admin".equals(userRole);

        // 2. 检查是否为竞赛题目，是的话需要有权限查看
        try {
            Result<Long> contestResult = contestService.getContestIdByProblemId(problemId);

            if (Result.isSuccess(contestResult)) {
                Long contestId = contestResult.getData();

                // 如果 contestId > 0，说明是比赛题
                if (contestId != null && contestId > 0) {
                    // 如果不是管理员，必须校验权限才能查看题目
                    if (!isAdmin) {
                        Result<Boolean> accessResult = contestService.hasAccess(contestId, userId);

                        // 校验不通过（无权限、未报名等）
                        if (Result.isSuccess(accessResult) || !Boolean.TRUE.equals(accessResult.getData())) {
                            throw new ProblemException(ResultCode.NOT_ACCESS_TO_CONTEST);
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 远程调用失败时的兜底策略：这里安全优先，采用报错，防止泄题
            log.error("校验题目比赛权限失败: problemId={}", problemId, e);
            throw new ProblemException(ResultCode.SYSTEM_ERROR);
        }

        // 3. 检查题目是否为隐藏状态 (如果是C端用户，不能看 hidden 的题目)
        if (ProblemStatusEnum.HIDDEN.getCode().equals(problemEntity.getStatus())) {
            // 只有管理员 ("admin") 才能预览，普通用户或未登录用户抛出异常
            if (!isAdmin) {
                throw new ProblemException(ResultCode.SUBJECT_NOT_FOUND);
            }
        }

        // 4. 转换 Bean (Entity -> DetailVO)
        ProblemDetailVO detailVO = new ProblemDetailVO();
        BeanUtil.copyProperties(problemEntity, detailVO);

        // 5. 填充标签 (单个题目，查一次关联表即可)
        // 5.1 查关联关系
        LambdaQueryWrapper<ProblemTagRelationEntity> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(ProblemTagRelationEntity::getProblemId, problemId);
        List<ProblemTagRelationEntity> relations = problemTagRelationMapper.selectList(relationWrapper);

        // 5.2 如果有标签，查详情
        if(CollectionUtil.isNotEmpty(relations)) {
            List<Long> tagIds = relations.stream().map(ProblemTagRelationEntity::getTagId).collect(Collectors.toList());

            List<ProblemTagVO> tagVOS = problemTagMapper.selectBatchIds(tagIds).stream().map(entity -> ProblemTagVO.objToVo(entity)).collect(Collectors.toList());
            detailVO.setTags(tagVOS);
        } else {
            detailVO.setTags(Collections.emptyList());
        }

        return detailVO;
    }


    /**
     * 提交题目
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long submitProblem(ProblemSubmitDTO submitDTO) {
        // 1. 校验题目是否存在
        ProblemEntity problem = this.getById(submitDTO.getProblemId());
        if(problem == null) {
            throw new ProblemException(ResultCode.SUBJECT_NOT_FOUND);
        }

        // =========================================================
        // 2. 比赛权限与反作弊校验
        // =========================================================

        // 2.1 先查一下这道题到底属于哪个比赛 (远程调用)
        Long realContestId = null;
        Result<Long> contestIdRes = contestService.getContestIdByProblemId(submitDTO.getProblemId());
        if (Result.isSuccess(contestIdRes)) {
            realContestId = contestIdRes.getData();
        }

        // 2.2 用户声称是比赛提交 (DTO 带了 contestID)
        if (submitDTO.getContestId() != null) {
            // 校验 A: 防止“张冠李戴”，用户传的 ID 必须和题目真实的比赛 ID 一致
            if (realContestId == null || !submitDTO.getContestId().equals(realContestId)) {
                // 题目不属于该比赛，或者题目根本没关联比赛
                throw new ProblemException(ResultCode.FORBIDDEN_OPERATION);
            }

            // 校验 B: 校验报名资格和比赛状态 (调用 validatePermission)
            Result<Boolean> result = contestService.validateContestPermission(submitDTO.getContestId(), UserContext.getUserId());

            // 处理远程调用的结果
            if (!Result.isSuccess(result)) {
                // 远程服务抛异常了 (比如比赛未开始、未报名)
                throw new ProblemException(result.getCode(), result.getMessage());
            }
            // 防御性编程：万一远程返回了 success 可是 data 是 false
            if (result.getData() != null && !result.getData()) {
                throw new ProblemException(ResultCode.USER_NOT_REGISTERED_CONTEST);
            }
        }

        // 2.3 用户声称是普通提交 (DTO 没带 contestID)
        else {
            // 校验 C: 【反偷渡逻辑】
            // 如果这道题确实属于某个比赛，且该比赛 “正在进行中”，则严禁当作普通题目提交！
            if (realContestId != null) {
                // 1. 排除管理员 (管理员需要调试题目，不受限制)
                if(!"admin".equals(UserContext.getUserRole())) {
                    // 2. 调用远程服务，查询比赛是否进行中
                    Result<Boolean> contestOngoing = contestService.isContestOngoing(realContestId);
                    if(Result.isSuccess(contestOngoing) && Boolean.TRUE.equals(contestOngoing.getData())) {
                        // ❌ 拦截：比赛进行中，严禁从普通入口提交！
                        // 哪怕你报名了，也必须去比赛页面提交（为了统计罚时）
                        // 哪怕你没报名，比赛中也不让你做这道题（为了公平）
                        throw new ProblemException(ResultCode.SUBMIT_LOGIC_ERROR);
                    }
                }
            }
        }

        // 3. 保存提交记录
        ProblemSubmitRecordEntity submitRecord = new ProblemSubmitRecordEntity();
        submitRecord.setProblemId(submitDTO.getProblemId());
        submitRecord.setCode(submitDTO.getCode());
        submitRecord.setLanguage(submitDTO.getLanguage());

        // 确保数据库 contest_id 字段默认值处理 (0L 表示平时训练)
        submitRecord.setContestId(submitDTO.getContestId() == null ? 0L : submitDTO.getContestId());

        // 从 UserContext 获取当前登录用户
        Long userId = UserContext.getUserId();
        if(userId == null) {
            throw new ProblemException(ResultCode.UNAUTHORIZED);
        }
        submitRecord.setUserId(userId);

        submitRecord.setStatus(10); // 10-Wait (等待判题)
        submitRecord.setJudgeResult(null); // 尚未出结果

        problemSubmitMapper.insert(submitRecord);

        // 4. 发送消息到MQ
        rabbitTemplate.convertAndSend(Constants.JUDGE_EXCHANGE, Constants.JUDGE_ROUTING_KEY, submitRecord.getSubmitId());
        log.info("Send submitId={} to MQ", submitRecord.getSubmitId());

        return submitRecord.getSubmitId();
    }


    /**
     * 更新提交结果
     */
    @Override
    public Boolean updateSubmitResult(ProblemSubmitUpdateDTO updateDTO) {
        // ================================================================
        // 1. 先计算分数 (Pre-calculate Score)
        //    必须在 entity 初始化之前算出分数，才能存入数据库
        // ================================================================
        int score = 0;
        int fullscore = Constants.CONTEST_QUESTION_SCORE;
        int passCount = updateDTO.getPassCaseCount() == null ? 0 : updateDTO.getPassCaseCount();
        int totalCount = updateDTO.getTotalCaseCount() == null ? 0 : updateDTO.getTotalCaseCount();
        // 逻辑 A: 如果直接 AC，满分
        if (JudgeResultEnum.ACCEPTED.getCode().equals(updateDTO.getJudgeResult())) {
            score = fullscore;
        }
        // 逻辑 B: 如果没 AC，但有部分通过，按比例给分
        else if (totalCount > 0 && passCount > 0) {
            double ratio = (double) passCount / totalCount;
            score = (int) (ratio * fullscore);
        }

        // ================================================================
        // 2. 准备更新数据
        // ================================================================
        ProblemSubmitRecordEntity entity = new ProblemSubmitRecordEntity();
        entity.setSubmitId(updateDTO.getSubmitId());
        entity.setScore(score);
        if (updateDTO.getStatus() != null) entity.setStatus(updateDTO.getStatus());
        if (updateDTO.getJudgeResult() != null) entity.setJudgeResult(updateDTO.getJudgeResult());
        if (updateDTO.getTimeCost() != null) entity.setTimeCost(updateDTO.getTimeCost());
        if (updateDTO.getMemoryCost() != null) entity.setMemoryCost(updateDTO.getMemoryCost());
        if (updateDTO.getErrorMessage() != null) entity.setErrorMessage(updateDTO.getErrorMessage());
        entity.setPassCaseCount(passCount);
        entity.setTotalCaseCount(totalCount);

        // ================================================================
        // 3. 执行数据库更新
        // ================================================================
        // 只有这里更新成功了，才继续往下更新其他信息，防止消息重复消费导致的重复计数
        boolean updateSuccess = problemSubmitMapper.updateById(entity) > 0;

        // ================================================================
        // 4. 后置处理 (Redis 排行榜 & User 统计)
        // ================================================================
        if (updateSuccess) {
            try {
                // 查询提交记录详情 (为了拿到 userId, problemId, contestId)
                ProblemSubmitRecordEntity submitRecord = problemSubmitMapper.selectById(updateDTO.getSubmitId());

                if (submitRecord != null) {
                    boolean isAc = JudgeResultEnum.ACCEPTED.getCode().equals(updateDTO.getJudgeResult());

                    // --- 4.1 普通排行榜 (只看 AC) ---
                    boolean isFirstAc = false; // 该用户是否是第一次解决这道题？
                    if (isAc) {
                        // userAcProblem 返回 true 表示是“首次AC”，返回 false 表示“重复AC”
                        isFirstAc = rankingManager.userAcProblem(submitRecord.getUserId(), submitRecord.getProblemId());
                    }

                    // --- 4.2 比赛排行榜 (看分数) ---
                    if (submitRecord.getContestId() != null && submitRecord.getContestId() > 0) {
                        rankingManager.updateContestScoreRank(
                                submitRecord.getContestId(),
                                submitRecord.getUserId(),
                                submitRecord.getProblemId(),
                                score // 传入上面算好的分数
                        );
                    }

                    // --- 4.3 更新统计信息 ---
                    userInterface.updateUserStats(submitRecord.getUserId(), isAc && isFirstAc);

                    // 更新题目表
                    LambdaUpdateWrapper<ProblemEntity> problemUpdateWrapper = new LambdaUpdateWrapper<>();
                    problemUpdateWrapper.eq(ProblemEntity::getProblemId, submitRecord.getProblemId());

                    String sql = "submit_num = IFNULL(submit_num, 0) + 1";
                    if (isAc) {
                        sql += ", accepted_num = IFNULL(accepted_num, 0) + 1";
                    }
                    problemUpdateWrapper.setSql(sql);

                    this.update(problemUpdateWrapper);
                }
            } catch (Exception e) {
                // 捕获异常，防止因为统计更新失败导致判题任务回滚 (判题结果才是最重要的)
                log.error("更新统计信息失败: submitId={}", updateDTO.getSubmitId(), e);
            }
        }

        return updateSuccess;
    }


    /**
     * 获取测试用例
     */
    @Override
    public List<TestCaseDTO> getTestCases(Long problemId) {
        // 把测试用例都找出来
        LambdaQueryWrapper<TestCaseEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCaseEntity::getProblemId, problemId);
        List<TestCaseEntity> caseEntities = testCaseMapper.selectList(wrapper);

        // 转化为DTO返回
        List<TestCaseDTO> caseDTOS = caseEntities.stream().map(entity -> {
            TestCaseDTO dto = new TestCaseDTO();
            dto.setInput(entity.getInput());
            dto.setOutput(entity.getOutput());
            return dto;
        }).collect(Collectors.toList());
        return caseDTOS;
    }


    /**
     * 获取提交记录（内部接口，用于MQ拿到代码和编程语言进行操作）
     */
    @Override
    public SubmitRecordDTO getInnerSubmitRecord(Long submitId) {
        ProblemSubmitRecordEntity recordEntity = problemSubmitMapper.selectById(submitId);
        if (recordEntity == null) {
            throw new ProblemException(ResultCode.SUBMIT_RECORD_NOT_FOUND);
        }

        SubmitRecordDTO submitRecordDTO = new SubmitRecordDTO();
        BeanUtil.copyProperties(recordEntity, submitRecordDTO);
        return submitRecordDTO;
    }


    /**
     * 获取提交记录（外部接口，用于展示）
     */
    @Override
    public SubmitRecordVO getSubmitRecord(Long submitId) {
        // 1. 查询数据库记录
        ProblemSubmitRecordEntity submitRecord = problemSubmitMapper.selectById(submitId);
        if (submitRecord == null) {
            throw new ProblemException(ResultCode.SUBMIT_RECORD_NOT_FOUND);
        }

        // 2. 转换为 VO
        SubmitRecordVO vo = new SubmitRecordVO();
        BeanUtil.copyProperties(submitRecord, vo);

        // 3. 安全校验：代码脱敏 (Code De-sensitization)
        // 只有 "本人" 才能查看源码和详细报错
        Long currentUserId = UserContext.getUserId(); // 从网关透传的 Header 中获取

        // 如果未登录，或者当前用户不是提交者
        if (currentUserId == null || !currentUserId.equals(submitRecord.getUserId())) {
            vo.setCode(null);          // 隐藏代码
            vo.setErrorMessage(null);  // 隐藏错误栈（防止泄题或暴露系统信息）
            // 提示：status 和 judgeResult 依然保留，别人可以看到你 "AC" 还是 "WA"，但看不到代码
        }

        return vo;
    }


    /**
     * 获取题目基本信息（contest模块调用）
     */
    @Override
    public ProblemBasicInfoDTO getProblemBasicInfo(Long problemId) {
        ProblemEntity problem = this.getById(problemId);
        if (problem == null) {
            throw new ProblemException(ResultCode.SUBJECT_NOT_FOUND);
        }

        ProblemBasicInfoDTO basicInfoDTO = new ProblemBasicInfoDTO();
        BeanUtil.copyProperties(problem, basicInfoDTO);
        return basicInfoDTO;
    }


}
