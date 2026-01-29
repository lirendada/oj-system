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
import jakarta.annotation.PostConstruct;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


/**
 * Docker 通用代码沙箱 (支持 Java, C++, Python)
 */
@Slf4j
@Component
public class DockerCodeSandbox implements CodeSandbox {

    @Autowired
    private DockerClient dockerClient;

    @Value("${oj.judge.docker.pool-size}")
    private int poolSize;

    // 容器池
    private final BlockingQueue<String> containerPool = new LinkedBlockingQueue<>();

    /**
     * 系统启动时初始化容器池
     */
    @PostConstruct
    public void init() {
        log.info("开始初始化 Docker 容器池，大小: {}", poolSize);
        for (int i = 0; i < poolSize; i++) {
            String containerId = createAndStartContainer();
            containerPool.offer(containerId);
        }
        log.info("Docker 容器池初始化完成");
    }

    /**
     * 创建并启动一个常驻容器
     */
    private String createAndStartContainer() {
        HostConfig hostConfig = new HostConfig();
        hostConfig.withMemory(Constants.SANDBOX_MEMORY_LIMIT);
        hostConfig.withCpuCount(Constants.SANDBOX_CPU_COUNT);
        hostConfig.withPidsLimit(64L); // 防止 Fork 炸弹

        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(Constants.SANDBOX_IMAGE)
                .withHostConfig(hostConfig)
                .withNetworkDisabled(true) // 禁网
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .withWorkingDir("/app") // 指定固定工作目录
                .withCmd("tail", "-f", "/dev/null"); // 关键：让容器死循环运行，不退出

        CreateContainerResponse response = containerCmd.exec();
        String containerId = response.getId();
        dockerClient.startContainerCmd(containerId).exec();
        log.info("创建新容器: {}", containerId);
        return containerId;
    }

    // 分语言黑名单 (Key: 语言, Value: 黑名单正则列表)
    private static final Map<String, List<Pattern>> SECURITY_RULES = new HashMap<>();

    static {
        // Java 黑名单: 封禁文件读写、网络、反射、运行时执行
        List<String> javaRegex = Arrays.asList(
                "\\bFiles\\b", "\\bFile\\b", "\\bFileInputStream\\b", "\\bFileOutputStream\\b", // 文件IO
                "\\bRuntime\\b", "\\bexec\\b", "\\bProcessBuilder\\b", "\\bProcess\\b",         // 进程执行
                "\\bnet\\b", "\\bSocket\\b", "\\bServerSocket\\b",                              // 网络
                "\\breflect\\b", "\\bMethod\\b", "\\bClass\\.forName\\b"                        // 反射(防绕过)
        );
        SECURITY_RULES.put("java", compileRegex(javaRegex));

        // C++ 黑名单: 封禁系统调用
        List<String> cppRegex = Arrays.asList(
                "\\bsystem\\b", "\\bfork\\b", "\\bopen\\b", "\\bexec\\b", "\\bsocket\\b"
        );
        SECURITY_RULES.put("cpp", compileRegex(cppRegex));
        SECURITY_RULES.put("c++", compileRegex(cppRegex));

        // Python 黑名单
        List<String> pythonRegex = Arrays.asList(
                "\\bos\\.system\\b", "\\bos\\.popen\\b", "\\bsubprocess\\b", "\\bexec\\b", "\\beval\\b", "\\bopen\\b",
                "\\bsocket\\b", "\\burllib\\b", "\\bhttp\\.client\\b", "\\brequests\\b"
        );
        SECURITY_RULES.put("python", compileRegex(pythonRegex));
        SECURITY_RULES.put("python3", compileRegex(pythonRegex));
    }

    // 辅助方法：预编译正则
    private static List<Pattern> compileRegex(List<String> rules) {
        return rules.stream().map(Pattern::compile).collect(Collectors.toList());
    }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {
        String code = executeCodeRequest.getCode();
        String language = executeCodeRequest.getLanguage();

        // 1. 恶意代码静态安全检查
        FoundWord found = checkMaliciousCode(code, language); // 传入语言
        if (found != null) {
            return ExecuteCodeResponse.builder()
                    .status(SandboxRunStatusEnum.SYSTEM_ERROR.getCode()) // 保持约定
                    .message("MaliciousCode detected: Sensitive operation [" + found.word + "]")
                    .build();
        }
        // ----------------------------------

        List<String> inputList = executeCodeRequest.getInputList();
        String containerId = null;

        try {
            // 2. 从池中获取容器 (阻塞等待)
            log.info("等待获取容器...");
            containerId = containerPool.take();
            log.info("获取到容器: {}", containerId);

            // 3. 预处理：根据语言确定文件名和命令
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

            // 4. 清理环境 (复用前先清理上次遗留的文件)
            cleanContainer(containerId);

            // 5. 上传代码
            uploadFileToContainer(containerId, sourceFileName, code.getBytes(StandardCharsets.UTF_8));

            // 6. 编译
            if (StrUtil.isNotBlank(compileCmd)) {
                ExecMessage compileMsg = execCmd(containerId, compileCmd.split(" "));
                if (compileMsg.getExitValue() != 0) {
                    return ExecuteCodeResponse.builder()
                            .status(SandboxRunStatusEnum.COMPILE_ERROR.getCode())
                            .message("编译错误:\n" + compileMsg.getErrorMessage() + "\n" + compileMsg.getMessage())
                            .build();
                }
            }

            // 7. 执行代码 (针对每个输入用例)
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

            // 8. 封装结果
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
            // 如果容器坏了(比如OOM死掉)，需要尝试重建
            if (containerId != null) {
                try {
                    // 简单检查容器状态，或者直接销毁旧的创建一个新的
                    dockerClient.removeContainerCmd(containerId).withForce(true).exec();
                    containerId = createAndStartContainer(); // 替换为新容器
                } catch (Exception ex) {
                    log.error("容器重建失败", ex);
                    containerId = null; // 防止finally归还坏容器
                }
            }
            return ExecuteCodeResponse.builder()
                    .status(SandboxRunStatusEnum.SYSTEM_ERROR.getCode())
                    .message(e.getMessage())
                    .build();
        } finally {
            // 8. 归还容器
            if (containerId != null) {
                // 再次清理以防万一，或者留给下次使用前清理(推荐使用前清理效率更高，这里为了安全可以双重清理)
                // 这里我们选择直接归还，下次使用时在步骤 4 清理，减少一次exec交互
                boolean offerSuccess = containerPool.offer(containerId);
                if (!offerSuccess) {
                    log.error("容器归还失败，队列已满? ID: {}", containerId);
                }
            }
        }
    }

    /**
     * 清理容器内的文件 (删除工作目录下的所有文件)
     */
    private void cleanContainer(String containerId) {
        try {
            // 简单的 rm -rf ./* (注意工作目录是 /app)
            execCmd(containerId, new String[]{"sh", "-c", "rm -rf ./*"});
        } catch (Exception e) {
            log.error("清理容器失败", e);
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

    /**
     * 简单的静态代码检查辅助类
     */
    @Data
    private static class FoundWord {
        String word;
        public FoundWord(String word) { this.word = word; }
    }

    /**
     * 检查方法：支持正则、分语言
     */
    private FoundWord checkMaliciousCode(String code, String language) {
        if (StrUtil.isBlank(code)) {
            return null;
        }
        String langKey = language.toLowerCase().trim();
        List<Pattern> patterns = SECURITY_RULES.get(langKey);

        // 如果没有该语言的规则，默认不检查或使用通用规则（视策略而定）
        if (patterns == null) {
            return null;
        }

        for (Pattern pattern : patterns) {
            // 使用 Matcher 进行正则匹配
            if (pattern.matcher(code).find()) {
                return new FoundWord(pattern.pattern()); // 返回匹配到的正则模式
            }
        }
        return null;
    }
}