package com.liren.judge.mq;

import com.liren.api.problem.api.problem.ProblemInterface;
import com.liren.api.problem.api.user.UserInterface;
import com.liren.api.problem.dto.problem.ProblemSubmitUpdateDTO;
import com.liren.api.problem.dto.problem.SubmitRecordDTO;
import com.liren.api.problem.dto.problem.TestCaseDTO;
import com.liren.common.core.constant.Constants;
import com.liren.common.core.enums.JudgeResultEnum;
import com.liren.common.core.enums.SandboxRunStatusEnum;
import com.liren.common.core.enums.SubmitStatusEnum;
import com.liren.common.core.enums.UserStatusEnum;
import com.liren.common.core.result.Result;
import com.liren.common.core.result.ResultCode;
import com.liren.judge.exception.JudgeException;
import com.liren.judge.sandbox.CodeSandbox;
import com.liren.judge.sandbox.model.ExecuteCodeRequest;
import com.liren.judge.sandbox.model.ExecuteCodeResponse;
import com.liren.judge.strategy.JudgeContext;
import com.liren.judge.strategy.JudgeManager;
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

    @Autowired
    private JudgeManager judgeManager;

    @Autowired
    private UserInterface userInterface;

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
            Long userId = submitRecord.getData().getUserId();

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

            // --- 恶意代码检测与处理 ---
            // 检查沙箱返回的消息是否标记为恶意代码 (根据 Sandbox 约定的特定字符串前缀)
            if (executeResponse.getMessage() != null && executeResponse.getMessage().startsWith("MaliciousCode")) {
                log.warn("检测到恶意代码提交！SubmitId: {}, UserId: {}, Info: {}", submitId, userId, executeResponse.getMessage());

                // 1. 调用 User 服务封禁用户
                userInterface.updateUserStatus(userId, UserStatusEnum.FORBIDDEN.getCode());

                // 2. 更新判题状态为失败 (或者定义一个新的状态 "SE-Malicious")
                ProblemSubmitUpdateDTO failDTO = new ProblemSubmitUpdateDTO();
                failDTO.setSubmitId(submitId);
                failDTO.setStatus(SubmitStatusEnum.FAILED.getCode());
                failDTO.setErrorMessage("系统检测到恶意代码，您的账号已被封禁。原因: " + executeResponse.getMessage());
                problemService.updateSubmitResult(failDTO);

                // 3. 确认消息并直接返回，不再继续判题
                channel.basicAck(deliveryTag, false);
                return;
            }
            // ------------------------------------

            log.info("沙箱执行结束, 状态: {}", SandboxRunStatusEnum.getByCode(executeResponse.getStatus()).getMessage());

            // ------------------------------------------
            // 3. 构造上下文
            // ------------------------------------------
            JudgeContext judgeContext = new JudgeContext();
            judgeContext.setLanguage(language);
            judgeContext.setSubmitId(submitId);
            judgeContext.setExecuteCodeResponse(executeResponse);
            judgeContext.setTestCases(testCases);
            log.info("构造 JudgeContext 完成, judgeContext: {}", judgeContext);

            // ------------------------------------------
            // 4. 交给管家处理，拿到最终结果
            // ------------------------------------------
            ProblemSubmitUpdateDTO updateDTO = judgeManager.doJudge(judgeContext);

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
}
