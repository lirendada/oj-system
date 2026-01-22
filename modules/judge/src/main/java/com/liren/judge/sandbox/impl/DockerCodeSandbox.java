package com.liren.judge.sandbox.impl;

import cn.hutool.core.util.StrUtil;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.InspectExecResponse;
import com.github.dockerjava.api.model.*;
import com.liren.common.core.constant.Constants;
import com.liren.common.core.enums.SandboxRunStatusEnum;
import com.liren.judge.sandbox.CodeSandbox;
import com.liren.judge.sandbox.model.ExecuteCodeRequest;
import com.liren.judge.sandbox.model.ExecuteCodeResponse;
import com.liren.judge.sandbox.model.JudgeInfo;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;


/**
 * Docker 通用代码沙箱 (支持 Java, C++, Python)
 */
@Slf4j
@Component
public class DockerCodeSandbox implements CodeSandbox {

    @Autowired
    private DockerClient dockerClient;

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();
        List<String> inputList = executeCodeRequest.getInputList();
        String containerId = null;

        try {
            // 1. 预处理：根据语言确定文件名和命令
            String sourceFileName;
            String compileCmd;
            String runCmdFormat; // 格式化字符串，%s 为文件名或类名

            // 简单的归一化处理 (防止传过来是 "JAVA" 或 "java ")
            String lang = language.toLowerCase().trim();

            switch (lang) {
                case "java":
                    sourceFileName = "Main.java";
                    compileCmd = "javac -encoding utf-8 Main.java";
                    runCmdFormat = "java -cp . Main < input.txt";
                    break;
                case "cpp":
                case "c++":
                    sourceFileName = "main.cpp";
                    compileCmd = "g++ -o Main main.cpp"; // 编译输出为 Main 可执行文件
                    runCmdFormat = "./Main < input.txt";
                    break;
                case "python":
                case "python3":
                    sourceFileName = "main.py";
                    compileCmd = null; // Python 不需要编译
                    runCmdFormat = "python3 main.py < input.txt";
                    break;
                default:
                    return ExecuteCodeResponse.builder()
                            .status(SandboxRunStatusEnum.SYSTEM_ERROR.getCode())
                            .message("不支持的编程语言: " + language)
                            .build();
            }

            // 2. 创建容器
            log.info("创建容器中... 语言: {}", language);
            HostConfig hostConfig = new HostConfig();
            hostConfig.withMemory(Constants.SANDBOX_MEMORY_LIMIT); // 限制内存 100MB
            hostConfig.withCpuCount(Constants.SANDBOX_CPU_COUNT); // 限制 CPU 1核

            CreateContainerCmd containerCmd = dockerClient.createContainerCmd(Constants.SANDBOX_IMAGE)
                    .withHostConfig(hostConfig)
                    .withAttachStdin(true)
                    .withAttachStderr(true)
                    .withAttachStdout(true)
                    .withTty(true) // 保持后台运行
                    .withWorkingDir("/");

            CreateContainerResponse createContainerResponse = containerCmd.exec();
            containerId = createContainerResponse.getId();

            // 3. 启动容器
            dockerClient.startContainerCmd(containerId).exec();
            log.info("容器已启动, ID: {}", containerId);

            // 4. 将用户代码上传到容器
            // 需要先把 String 存为 Main.java 字节数组，然后打成 tar 包上传
            // 这里我们用一个辅助方法处理
            uploadFileToContainer(containerId, sourceFileName, code.getBytes(StandardCharsets.UTF_8));

            // 5. 编译代码(如果需要)
            if(StrUtil.isNotBlank(compileCmd)) {
                log.info("编译中: {}", compileCmd);

                // split(" ") 简单切分，对于复杂命令可能不够，但在当前场景够用
                ExecMessage compileMsg = execCmd(containerId, compileCmd.split(" "));
                if (compileMsg.getExitValue() != 0) {
                    return ExecuteCodeResponse.builder()
                            .status(SandboxRunStatusEnum.COMPILE_ERROR.getCode())
                            .message("编译错误:\n" + compileMsg.getErrorMessage() + "\n" + compileMsg.getMessage())
                            .build();
                }
            }

            // 6. 执行代码 (针对每个输入用例)
            List<String> outputList = new ArrayList<>();
            long maxTime = 0;

            for (String input : inputList) {
                // 上传输入数据（在文件中）
                uploadFileToContainer(containerId, "input.txt", input.getBytes(StandardCharsets.UTF_8));

                // 构造运行命令 (使用 sh -c 支持 < 重定向)
                // 注意：runCmdFormat 已经在上面 switch 里定义好了
                String fullRunCmd = "sh -c " + runCmdFormat;
                log.info("执行命令: {}", fullRunCmd); // 打印实际执行的命令

                StopWatch stopWatch = new StopWatch();
                stopWatch.start();

                // 执行代码
                ExecMessage runMsg = execCmd(containerId, new String[]{"sh", "-c", runCmdFormat});

                stopWatch.stop();
                long time = stopWatch.getLastTaskTimeMillis();
                maxTime = Math.max(maxTime, time);

                if (runMsg.getExitValue() != 0) {
                    return ExecuteCodeResponse.builder()
                            .status(SandboxRunStatusEnum.RUNTIME_ERROR.getCode()) // 2-运行错误
                            .message("运行错误: " + runMsg.getErrorMessage())
                            .build();
                }
                outputList.add(runMsg.getMessage().trim()); // 收集输出
            }

            // 7. 封装结果
            JudgeInfo judgeInfo = new JudgeInfo();
            judgeInfo.setTime(maxTime);
            judgeInfo.setMemory(0L); // TODO:Docker 较难精确获取每次运行内存，暂存0，后续可优化

            return ExecuteCodeResponse.builder()
                    .status(SandboxRunStatusEnum.NORMAL.getCode()) // 1-正常
                    .outputList(outputList)
                    .judgeInfo(judgeInfo)
                    .build();

        } catch (Exception e) {
            log.error("沙箱执行异常", e);
            return ExecuteCodeResponse.builder()
                    .status(SandboxRunStatusEnum.SYSTEM_ERROR.getCode())
                    .message(e.getMessage())
                    .build();
        } finally {
            // 8. 销毁容器 (非常重要！否则服务器内存会炸)
            if (containerId != null) {
                try {
                    dockerClient.stopContainerCmd(containerId).exec();
                    dockerClient.removeContainerCmd(containerId).exec();
                    log.info("容器已销毁: {}", containerId);
                } catch (Exception e) {
                    log.error("销毁容器失败", e);
                }
            }
        }
    }

    // === 辅助方法 ===

    /**
     * 将文件内容上传到容器 (解决远程 Docker 文件传输问题)
     */
    private void uploadFileToContainer(String containerId, String fileName, byte[] content) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             TarArchiveOutputStream tar = new TarArchiveOutputStream(bos)) {

            TarArchiveEntry entry = new TarArchiveEntry(fileName);
            entry.setSize(content.length);
            tar.putArchiveEntry(entry);
            tar.write(content);
            tar.closeArchiveEntry();
            tar.finish(); // 必须 finish

            // 上传 tar 流
            dockerClient.copyArchiveToContainerCmd(containerId)
                    .withTarInputStream(new ByteArrayInputStream(bos.toByteArray()))
                    .withRemotePath("/") // 放到根目录
                    .exec();
        }
    }

    /**
     * 执行命令辅助类
     */
    @Data
    private static class ExecMessage {
        private int exitValue;
        private String message;
        private String errorMessage;
    }

    /**
     * 在容器内执行命令
     */
    private ExecMessage execCmd(String containerId, String[] cmd) {
        ExecMessage result = new ExecMessage();
        final StringBuilder stdout = new StringBuilder();
        final StringBuilder stderr = new StringBuilder();

        try {
            // 1. 创建 Exec
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withAttachStdout(true)
                    .withAttachStderr(true)
                    .withCmd(cmd)
                    .exec();

            // 2. 启动执行并等待
            dockerClient.execStartCmd(execCreateCmdResponse.getId())
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            if (frame.getStreamType() == StreamType.STDERR) {
                                stderr.append(new String(frame.getPayload()));
                            } else {
                                stdout.append(new String(frame.getPayload()));
                            }
                        }
                    }).awaitCompletion(Constants.SANDBOX_TIME_OUT, TimeUnit.SECONDS);

            // 3. 获取退出码
            InspectExecResponse response = dockerClient.inspectExecCmd(execCreateCmdResponse.getId()).exec();
            result.setExitValue(response.getExitCodeLong().intValue());
            result.setMessage(stdout.toString());
            result.setErrorMessage(stderr.toString());

        } catch (Exception e) {
            result.setExitValue(-1);
            result.setErrorMessage(e.getMessage());
        }
        return result;
    }
}