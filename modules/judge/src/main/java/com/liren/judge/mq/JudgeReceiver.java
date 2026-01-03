package com.liren.judge.mq;

import com.liren.api.problem.api.ProblemInterface;
import com.liren.api.problem.dto.ProblemSubmitUpdateDTO;
import com.liren.api.problem.dto.SubmitRecordDTO;
import com.liren.api.problem.dto.TestCaseDTO;
import com.liren.common.core.constant.Constants;
import com.liren.common.core.enums.JudgeResultEnum;
import com.liren.common.core.enums.SandboxRunStatusEnum;
import com.liren.common.core.enums.SubmitStatusEnum;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.judge.exception.JudgeException;
import com.liren.judge.sandbox.CodeSandbox;
import com.liren.judge.sandbox.model.ExecuteCodeRequest;
import com.liren.judge.sandbox.model.ExecuteCodeResponse;
import com.liren.judge.sandbox.model.JudgeInfo;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class JudgeReceiver {
    @Autowired
    private CodeSandbox codeSandbox;

    @Autowired
    private ProblemInterface problemService;

    @RabbitListener(queues = Constants.JUDGE_QUEUE, ackMode = "MANUAL")
    public void receiveJudgeMessage(Long submitId, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        log.info("接收到判题任务, submitId: {}", submitId);

        try {
            // ------------------------------------------
            // 1. 准备数据: 查提交记录(代码)、查测试用例
            // ------------------------------------------
            Result<SubmitRecordDTO> submitRecord = problemService.getSubmitRecord(submitId);
            if(submitRecord == null || submitRecord.getData() == null) {
                // 如果记录都查不到，可能是严重数据不一致，直接确认掉防止死循环
                log.error("提交记录不存在: {}", submitId);
                channel.basicAck(deliveryTag, false);
                return;
            }
            String userCode = submitRecord.getData().getCode();
            String language = submitRecord.getData().getLanguage();
            Long problemId = submitRecord.getData().getProblemId();

            // 获取测试用例
            Result<List<TestCaseDTO>> testCaseResult = problemService.getTestCases(problemId);
            List<TestCaseDTO> testCases = testCaseResult.getData();

            if (testCases == null || testCases.isEmpty()) {
                throw new JudgeException(ResultCode.TEST_CASE_NOT_FOUND);
            }

            // 提取输入列表给沙箱
            List<String> inputList = testCases.stream().map(TestCaseDTO::getInput).collect(Collectors.toList());

            // ------------------------------------------
            // 2. 调用沙箱执行
            // ------------------------------------------
            ExecuteCodeRequest executeRequest = ExecuteCodeRequest.builder()
                    .code(userCode)
                    .language(language)
                    .inputList(inputList)
                    .build();

            log.info("调用 Docker 沙箱...");
            ExecuteCodeResponse executeResponse = codeSandbox.executeCode(executeRequest);
            log.info("沙箱执行结束, 状态: {}", SandboxRunStatusEnum.getByCode(executeResponse.getStatus()).getMessage());

            // ------------------------------------------
            // 3. 结果比对 (Judge Logic)
            // ------------------------------------------
            ProblemSubmitUpdateDTO updateDTO = new ProblemSubmitUpdateDTO();
            updateDTO.setSubmitId(submitId);
            updateDTO.setStatus(SubmitStatusEnum.SUCCEED.getCode()); // 只要沙箱跑完了，对于提交记录的生命周期来说就是“完成”了

            // 将沙箱的 status 转换为枚举，来判断判题结果
            SandboxRunStatusEnum runStatus = SandboxRunStatusEnum.getByCode(executeResponse.getStatus());

            if (runStatus == null) {
                // 防御性编程
                updateDTO.setJudgeResult(JudgeResultEnum.SYSTEM_ERROR.getCode());
                updateDTO.setErrorMessage("沙箱返回了未知的状态码: " + executeResponse.getStatus());
            }

            // 情况 A: 编译错误（CE）
            else if (runStatus == SandboxRunStatusEnum.COMPILE_ERROR) {
                updateDTO.setJudgeResult(JudgeResultEnum.COMPILE_ERROR.getCode());
                updateDTO.setErrorMessage(executeResponse.getMessage()); // 编译报错详情
            }

            // 情况 B: 运行错误（RE）
            else if (runStatus == SandboxRunStatusEnum.RUNTIME_ERROR) {
                updateDTO.setJudgeResult(JudgeResultEnum.RUNTIME_ERROR.getCode());
                updateDTO.setErrorMessage(executeResponse.getMessage()); // 运行报错详情
            }

            // 情况 C: 正常运行 (可能是 AC, WA, TLE, MLE)
            else if (runStatus == SandboxRunStatusEnum.NORMAL) {
                // 此时代码跑通了，需要检查：输出对不对、是否超时超内存
                processNormalResult(executeResponse, updateDTO, testCases);
            }

            // 情况 D: 沙箱系统错误
            else {
                updateDTO.setJudgeResult(JudgeResultEnum.SYSTEM_ERROR.getCode());
                updateDTO.setErrorMessage("沙箱系统异常: " + executeResponse.getMessage());
            }

            // ------------------------------------------
            // 4. 填充性能数据 (Time/Memory)
            // ------------------------------------------
            JudgeInfo judgeInfo = executeResponse.getJudgeInfo();
            if (judgeInfo != null) {
                updateDTO.setTimeCost(judgeInfo.getTime() != null ? judgeInfo.getTime().intValue() : 0);
                updateDTO.setMemoryCost(judgeInfo.getMemory() != null ? judgeInfo.getMemory().intValue() : 0);
            }

            // ------------------------------------------
            // 5. 回写数据库
            // ------------------------------------------
            Result<Boolean> updateResult = problemService.updateSubmitResult(updateDTO);
            if (Boolean.TRUE.equals(updateResult.getData())) {
                log.info("判题完成并回写成功!，submitId: {}，代码状态：{}", submitId, JudgeResultEnum.getByCode(updateDTO.getJudgeResult()).getMessage());
            } else {
                throw new RuntimeException("receiveJudgeMessage: 数据库更新失败: " + updateResult.getMessage());
            }

            // 手动确认消息
            log.info("判题流程结束, 确认消息...");
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.error("接收到判题任务失败, submitId: {}", submitId, e);
            try {
                // 异常情况，尝试回写状态为 FAILED (40)
                ProblemSubmitUpdateDTO failDTO = new ProblemSubmitUpdateDTO();
                failDTO.setSubmitId(submitId);
                failDTO.setStatus(SubmitStatusEnum.FAILED.getCode());
                failDTO.setErrorMessage("Judge Server Error: " + e.getMessage());
                problemService.updateSubmitResult(failDTO);

                // 确认消息，避免死循环
                channel.basicAck(deliveryTag, false);
            } catch (Exception ex) {
                // 连回写都失败了，只能 Nack 或者丢弃
                try {
                    channel.basicNack(deliveryTag, false, false);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }
    }

    /**
     * 处理沙箱正常运行后的结果比对
     * @param response 沙箱返回结果
     * @param updateDTO 待更新对象
     * @param testCases 标准测试用例列表 (这里直接用 List，不用 ProblemDTO)
     */
    private void processNormalResult(ExecuteCodeResponse response,
                                     ProblemSubmitUpdateDTO updateDTO,
                                     List<TestCaseDTO> testCases) {
        List<String> userOutputs = response.getOutputList();

        // 防御性检查：如果没有输出或者输出数量不对
        if (userOutputs == null || userOutputs.size() != testCases.size()) {
            updateDTO.setJudgeResult(JudgeResultEnum.WRONG_ANSWER.getCode());
            updateDTO.setErrorMessage("用户输出数量与用例数量不一致");
            return;
        }

        // 逐个比对
        for (int i = 0; i < testCases.size(); i++) {
            // 注意：通常 OJ 需要去除行末空格和换行符来比较
            // Hutool 的 StrUtil.trim() 或 String.trim() 都可以
            String userOut = userOutputs.get(i) == null ? "" : userOutputs.get(i).trim();
            String stdOut = testCases.get(i).getOutput() == null ? "" : testCases.get(i).getOutput().trim();

            if (!userOut.equals(stdOut)) {
                // 只要有一个对不上，就是 WA
                updateDTO.setJudgeResult(JudgeResultEnum.WRONG_ANSWER.getCode());
                // 可以在 errorMessage 里记录具体的 diff 信息，方便前端展示
                // updateDTO.setErrorMessage("Case " + (i+1) + " failed.");
                return;
            }
        }

        // 全部通过 -> AC
        updateDTO.setJudgeResult(JudgeResultEnum.ACCEPTED.getCode());
    }
}
