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

---

## 完整 REST API 接口列表

### 服务端口映射
| 服务 | 端口 | 说明 |
|-----|------|-----|
| Gateway 网关 | 10020 | 统一入口，JWT 认证 |
| User Service 用户服务 | 8004 | 用户管理、登录 |
| Problem Service 题目服务 | 8006 | 题目 CRUD、提交、排行榜 |
| Judge Service 判题服务 | 8002 | Docker 沙箱判题（无 REST API） |
| Contest Service 竞赛服务 | 8005 | 比赛管理、报名、排名 |
| System Service 系统服务 | 8003 | 后台管理 |
| Job Service 定时任务 | 8001 | 定时任务（无 REST API） |

---

### 用户服务 (User Service: 8004)

#### UserController `/user`
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| POST | `/user/login` | 用户登录 | `UserLoginDTO` (username, password) | `Result<UserLoginVO>` |

#### UserInnerController `/user/inner`（Feign 内部调用）
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| GET | `/user/inner/getBatchBasicInfo` | 批量获取用户基本信息 | `userIds` (List<Long>) | `Result<List<UserBasicInfoDTO>>` |
| POST | `/user/inner/update/stats` | 更新用户统计信息 | `userId` (Long), `isAc` (Boolean) | `Result<Boolean>` |

---

### 题目服务 (Problem Service: 8006)

#### ProblemController `/problem`
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| POST | `/problem/add` | 新增题目 | `ProblemAddDTO` | `Result<Boolean>` |
| POST | `/problem/list/page` | 分页获取题目列表 | `ProblemQueryRequest` | `Result<Page<ProblemVO>>` |
| GET | `/problem/detail/{problemId}` | 获取题目详情 | `problemId` (Path) | `Result<ProblemDetailVO>` |
| POST | `/problem/submit` | 提交代码 | `ProblemSubmitDTO` | `Result<Long>` (submitId) |
| GET | `/problem/submit/result/{submitId}` | 查询提交记录详情 | `submitId` (Path) | `Result<SubmitRecordVO>` |
| GET | `/problem/rank/total` | 获取总榜 Top 10 | - | `Result<List<RankItemVO>>` |
| GET | `/problem/rank/daily` | 获取日榜 Top 10 | - | `Result<List<RankItemVO>>` |
| GET | `/problem/rank/weekly` | 获取周榜 Top 10 | - | `Result<List<RankItemVO>>` |
| GET | `/problem/rank/monthly` | 获取月榜 Top 10 | - | `Result<List<RankItemVO>>` |
| POST | `/problem/test/update-result` | 【测试】模拟判题回调 | `ProblemSubmitUpdateDTO` | `Result<Boolean>` |

#### ProblemInnerController `/problem/inner`（Feign 内部调用）
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| POST | `/problem/inner/submit/update` | 更新提交结果 | `ProblemSubmitUpdateDTO` | `Result<Boolean>` |
| GET | `/problem/inner/test-case/{problemId}` | 获取测试用例 | `problemId` (Path) | `Result<List<TestCaseDTO>>` |
| GET | `/problem/inner/submit/{submitId}` | 获取提交记录 | `submitId` (Query) | `Result<SubmitRecordDTO>` |
| GET | `/problem/inner/contest/brief/{problemId}` | 获取题目基本信息 | `problemId` (Path) | `Result<ProblemBasicInfoDTO>` |

---

### 竞赛服务 (Contest Service: 8005)

#### ContestController `/contest`
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| POST | `/contest/add` | 创建/更新比赛 | `ContestAddDTO` | `Result<Boolean>` |
| POST | `/contest/list` | 分页查询比赛列表 | `ContestQueryRequest` | `Result<Page<ContestVO>>` |
| GET | `/contest/{id}` | 获取比赛详情 | `id` (Path) | `Result<ContestVO>` |
| POST | `/contest/problem/add` | 添加题目到比赛 | `ContestProblemAddDTO` | `Result<Void>` |
| GET | `/contest/{contestId}/problems` | 获取比赛题目列表 | `contestId` (Path) | `Result<List<ContestProblemVO>>` |
| POST | `/contest/problem/remove` | 移除比赛题目 | `contestId`, `problemId` (Query) | `Result<Void>` |
| POST | `/contest/register/{contestId}` | 报名比赛 | `contestId` (Path) | `Result<Boolean>` |
| GET | `/contest/rank/{contestId}` | 获取比赛排名 | `contestId` (Path) | `Result<List<ContestRankVO>>` |

#### ContestInnerController `/contest/inner`（Feign 内部调用）
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| GET | `/contest/inner/validate-permission` | 验证竞赛权限 | `contestId`, `userId` (Query) | `Result<Boolean>` |
| GET | `/contest/inner/hasAccess` | 校验题目查看权限 | `contestId`, `userId` (Query) | `Result<Boolean>` |
| GET | `/contest/inner/getContestIdByProblemId` | 根据题目获取比赛ID | `problemId` (Query) | `Result<Long>` |
| GET | `/contest/inner/isContestOngoing` | 判断比赛是否进行中 | `contestId` (Query) | `Result<Boolean>` |

---

### 系统服务 (System Service: 8003)

#### SystemUserController `/system/user`
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| POST | `/system/user/login` | 管理员登录 | `SystemUserLoginDTO` | `Result<SystemUserLoginVO>` |

---

### Feign 内部接口定义

#### UserInterface（api 模块）
```java
@FeignClient(value = "user-service", path = "/user/inner")
```
- `getBatchBasicInfo(@RequestParam("userIds") List<Long> userIds)` - 批量获取用户基本信息
- `updateStats(@RequestParam("userId") Long userId, @RequestParam("isAc") Boolean isAc)` - 更新用户统计

#### ProblemInterface（api 模块）
```java
@FeignClient(name = "problem-service", path = "/problem/inner")
```
- `updateSubmitResult(ProblemSubmitUpdateDTO problemSubmitUpdateDTO)` - 更新提交结果
- `getTestCase(@PathVariable("problemId") Long problemId)` - 获取测试用例
- `getSubmitById(@RequestParam("submitId") Long submitId)` - 获取提交记录
- `getContestBrief(@PathVariable("problemId") Long problemId)` - 获取题目基本信息

#### ContestInterface（api 模块）
```java
@FeignClient(name = "contest-service", path = "/contest/inner")
```
- `validatePermission(@RequestParam("contestId") Long contestId, @RequestParam("userId") Long userId)` - 验证竞赛权限
- `hasAccess(@RequestParam("contestId") Long contestId, @RequestParam("userId") Long userId)` - 校验题目查看权限
- `getContestIdByProblemId(@RequestParam("problemId") Long problemId)` - 根据题目获取比赛ID
- `isContestOngoing(@RequestParam("contestId") Long contestId)` - 判断比赛是否进行中

---

### 接口统计
| 服务模块 | 公开接口 | 内部接口 | 总计 |
|---------|---------|---------|------|
| 用户服务 | 1 | 2 | 3 |
| 题目服务 | 10 | 4 | 14 |
| 竞赛服务 | 8 | 4 | 12 |
| 系统服务 | 1 | 0 | 1 |
| **总计** | **20** | **10** | **30** |

---

### 关键设计特点
1. **分层架构**：外部接口使用普通路径，内部接口统一使用 `/inner` 前缀
2. **服务通信**：所有跨服务调用通过 Feign Client 实现
3. **权限控制**：竞赛相关接口需要验证报名状态和权限
4. **排行榜**：支持日榜、周榜、月榜、总榜等多个维度
5. **异步处理**：代码提交通过 RabbitMQ 异步判题，通过回调接口更新结果
6. **去重机制**：每道题目只有首次 AC 才会计入排行榜
