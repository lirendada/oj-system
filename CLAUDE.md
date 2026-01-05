# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 提供在此代码仓库中工作的指导。

## 项目概述

这是一个基于 Spring Cloud Alibaba 的微服务在线判题（OJ）系统。系统处理题目提交、使用 Docker 沙箱进行异步代码判题、竞赛管理和用户排行榜。

## 基础设施要求

运行任何服务前，请确保以下组件已启动：
- **Nacos**（服务注册与配置中心）：`lirendada.art:8848`
- **MySQL**：数据库初始化脚本位于 `deploy/system_init.sql`
- **Redis**：用于排行榜和缓存
- **RabbitMQ**：用于异步判题队列
- **Docker**：用于代码执行沙箱（镜像：`liren-oj-sandbox:v1`）

## 构建和运行

```bash
# 构建整个项目
mvn clean package

# 运行单个服务（在各模块目录下）
mvn spring-boot:run

# 或直接运行 JAR
java -jar modules/user/target/user-service-1.0-SNAPSHOT.jar
```

**服务端口**：
- 网关 Gateway：10020
- 用户服务 User Service：8004
- 题目服务 Problem Service：8006
- 判题服务 Judge Service：8002
- 竞赛服务 Contest Service：8005
- 系统服务 System Service：8003
- 定时任务 Job Service：8001

## 模块架构

### 基础设施层
- **gateway/**：Spring Cloud Gateway - JWT 认证、路由转发
- **api/**：Feign 客户端接口和跨服务 DTO
- **common/**：公共组件
  - `common-core`：基础实体、枚举、Result 包装类、JWT 工具、常量
  - `common-web`：全局异常处理、用户上下文拦截器
  - `common-redis`：Redis 配置、`RankingManager` 排行榜管理器
  - `common-swagger`：API 文档

### 业务服务层
- **user**（8004）：用户管理、注册、登录
- **problem**（8006）：题目 CRUD、测试用例、提交记录、排行榜（日榜/周榜/月榜/总榜）
- **judge**（8002）：通过 Docker 沙箱执行代码、判题策略
- **contest**（8005）：竞赛管理、报名、权限验证
- **system**（8003）：后台管理
- **job**（8001）：定时任务

## 核心模式与流程

### 异步判题流程
```
用户提交代码 → Problem 服务 → RabbitMQ (oj.judge.queue) → Judge 服务（Docker 沙箱）→ 结果回调 → 更新排行榜
```

### 服务间通信（Feign）
- Feign 接口定义在 `api/` 模块中
- 内部 API 路径遵循 `/inner` 约定用于服务间调用
- 示例：`UserInterface` 位于 `/user/inner`，提供批量用户信息和统计更新

### 排行榜系统
- `RankingManager` 使用 Redis ZSet 实现高性能排行榜
- 支持维度：日榜、周榜、月榜、总榜
- 自动去重：每道题目只有首次 AC 才会计入
- Redis Key：`oj:rank:total`、`oj:rank:daily:yyyyMMdd`、`oj:rank:weekly:yyyyww`、`oj:rank:monthly:yyyyMM`

### Docker 沙箱
- 支持 Java、C++、Python
- 资源限制定义在 `Constants` 中：`SANDBOX_TIME_OUT`、`SANDBOX_MEMORY_LIMIT`、`SANDBOX_CPU_COUNT`
- 策略模式：`JudgeManager` 委托给 `DefaultJudgeStrategy`
- 配置文件：`judge/config/DockerConfig.java`

### 认证与授权
- JWT 令牌，密钥在 `Constants.JWT_SECRET`
- 网关验证 JWT 并通过请求头传递 `userId`
- `UserInterceptor` 从请求头提取 `userId` 到 `UserContext` 线程本地变量
- 竞赛题目提交需要验证报名状态

## 关键配置

所有模块使用 `bootstrap.yml` 配置：
- Nacos 服务发现/配置中心地址
- 命名空间：`3e8d6cf0-32cd-43c0-8279-9fda3da2265f`
- 共享配置：从 Nacos 加载 `common.yaml`

## 数据库

- 数据库名：`oj_system`
- 核心表：`tb_user`、`tb_problem`、`tb_test_case`、`tb_submit_record`、`tb_contest`、`tb_contest_registration`
- 默认管理员（后台）：`admin` / `123456`
- 默认测试用户：`user001` / `123456`、`user002` / `123456`

## 常见开发任务

- **添加新的 Feign 客户端**：在 `api/` 中创建接口，在目标服务中使用 `/inner` 路径实现
- **添加新的枚举**：放在 `common/common-core/src/main/java/com/liren/common/core/enums/` 下
- **排行榜操作**：注入 `RankingManager` 并使用其方法进行 AC 追踪
- **异常处理**：抛出 `BizException`（来自 common-core）；由 `ExceptionAdvice` 全局处理

## 文件位置参考

- 应用入口：`modules/*/src/main/java/com/liren/{module}/*Application.java`
- 控制器：`modules/*/src/main/java/com/liren/{module}/controller/`
- 服务层：`modules/*/src/main/java/com/liren/{module}/service/`
- Feign 接口：`api/src/main/java/com/liren/api/{module}/api/`
