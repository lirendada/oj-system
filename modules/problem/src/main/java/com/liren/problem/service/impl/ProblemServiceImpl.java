package com.liren.problem.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.liren.api.problem.api.ContestInterface;
import com.liren.api.problem.dto.ProblemBasicInfoDTO;
import com.liren.api.problem.dto.ProblemSubmitUpdateDTO;
import com.liren.api.problem.dto.SubmitRecordDTO;
import com.liren.api.problem.dto.TestCaseDTO;
import com.liren.common.core.constant.Constants;
import com.liren.common.core.context.UserContext;
import com.liren.common.core.enums.ProblemStatusEnum;
import com.liren.common.core.exception.BizException;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
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
            for(ProblemTagRelationEntity relation : relationList) {
                problemTagRelationMapper.insert(relation);
            }
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
        // 1. 先查出problemEntity
        ProblemEntity problemEntity = this.getById(problemId);
        if(problemEntity == null) {
            throw new ProblemException(ResultCode.SUBJECT_NOT_FOUND);
        }

        // 2. 检查题目状态 (如果是C端用户，不能看 hidden 的题目)
        // 暂时假设所有调这个接口的都是C端，或者是管理员预览。
        // 如果严格一点，可以结合 UserContext 判断：如果是普通用户 且 status=0 -> 抛异常
        if (problemEntity.getStatus().equals(ProblemStatusEnum.HIDDEN.getCode())) {
             throw new ProblemException(ResultCode.SUBJECT_NOT_FOUND);
            // 这里先留个 TODO，是否允许管理员预览
        }

        // 3. 转换 Bean (Entity -> DetailVO)
        ProblemDetailVO detailVO = new ProblemDetailVO();
        BeanUtil.copyProperties(problemEntity, detailVO);

        // 4. 填充标签 (单个题目，查一次关联表即可)
        // 4.1 查关联关系
        LambdaQueryWrapper<ProblemTagRelationEntity> relationWrapper = new LambdaQueryWrapper<>();
        relationWrapper.eq(ProblemTagRelationEntity::getProblemId, problemId);
        List<ProblemTagRelationEntity> relations = problemTagRelationMapper.selectList(relationWrapper);

        // 4.2 如果有标签，查详情
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

        // 2. 如果是比赛题目，需要进行权限校验
        if(submitDTO.getContestId() != null) {
            Result<Boolean> result = contestService.validateContestPermission(submitDTO.getContestId(), UserContext.getUserId());

            // 逻辑判断需要适配：
            // 1. 远程调用本身失败 (网络故障等) -> !isSuccess
            // 2. 远程业务逻辑返回 false (无权限) -> getData() == false
            if(!result.isSuccess() || result.getData() == null || result.getData() == false) {
                if(result.getMessage() == null) {
                    throw new ProblemException(ResultCode.USER_NOT_REGISTERED_CONTEST);
                } else {
                    throw new ProblemException(result.getCode(), result.getMessage());
                }
            }
        }

        // 3. 保存提交记录
        ProblemSubmitRecordEntity submitRecord = new ProblemSubmitRecordEntity();
        submitRecord.setProblemId(submitDTO.getProblemId());
        submitRecord.setCode(submitDTO.getCode());
        submitRecord.setLanguage(submitDTO.getLanguage());
        submitRecord.setContestId(submitDTO.getContestId() == null ? 0L : submitDTO.getContestId());

        // 从 UserContext 获取当前登录用户
        Long userId = UserContext.getUserId();
        if(userId == null) {
            throw new ProblemException(ResultCode.UNAUTHORIZED); // 实际上网关会拦截，这里是兜底
        }
        submitRecord.setUserId(userId);

        submitRecord.setStatus(10); // 10-Wait
        submitRecord.setJudgeResult(null); // 尚未出结果
        problemSubmitMapper.insert(submitRecord);

        // 4. 发送消息到MQ
        // 消息内容通常发 ID 即可，消费者再去查库。或者把关键信息都发过去减少查库。
        // 这里我们发 submitId 过去
        rabbitTemplate.convertAndSend(Constants.JUDGE_EXCHANGE, Constants.JUDGE_ROUTING_KEY, submitRecord.getSubmitId());
        log.info("Send submitId={} to MQ", submitRecord.getSubmitId());
        return submitRecord.getSubmitId();
    }


    /**
     * 更新提交结果
     */
    @Override
    public Boolean updateSubmitResult(ProblemSubmitUpdateDTO updateDTO) {
        ProblemSubmitRecordEntity entity = new ProblemSubmitRecordEntity();
        entity.setSubmitId(updateDTO.getSubmitId());

        if (updateDTO.getStatus() != null) entity.setStatus(updateDTO.getStatus());
        if (updateDTO.getJudgeResult() != null) entity.setJudgeResult(updateDTO.getJudgeResult());
        if (updateDTO.getTimeCost() != null) entity.setTimeCost(updateDTO.getTimeCost());
        if (updateDTO.getMemoryCost() != null) entity.setMemoryCost(updateDTO.getMemoryCost());
        if (updateDTO.getErrorMessage() != null) entity.setErrorMessage(updateDTO.getErrorMessage());

        // MybatisPlus 根据 ID 更新非空字段
        return problemSubmitMapper.updateById(entity) > 0;
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
