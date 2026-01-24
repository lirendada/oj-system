# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 提供在此代码仓库中工作的指导。

## 项目概述

基于 Spring Cloud Alibaba 的微服务在线判题（OJ）系统，处理题目提交、Docker 沙箱异步判题、竞赛管理和用户排行榜。

## 基础设施

运行服务前确保以下组件已启动：
- **Nacos** (服务注册/配置中心)
- **MySQL** (初始化脚本: `deploy/mysql/oj_system.sql`)
- **Redis** (排行榜、缓存)
- **RabbitMQ** (异步判题队列)
- **Docker** (代码执行沙箱，镜像: `liren-oj-sandbox:v1`)

## 服务端口

| 服务 | 端口 |
|-----|------|
| Gateway | 10020 |
| User Service | 8004 |
| Problem Service | 8006 |
| Judge Service | 8002 |
| Contest Service | 8005 |
| System Service | 8003 |
| Job Service | 8001 |

## 项目架构

```
┌─────────────────────────────────────────────────────────────┐
│                     Spring Cloud Gateway                     │
│                      (Port 10020)                            │
│              JWT 认证 + 路由转发 + 限流                        │
└──────────────────────┬──────────────────────────────────────┘
                       │
       ┌───────────────┼───────────────┐
       │               │               │
       ▼               ▼               ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ User Service │ │Problem       │ │Contest       │
│  (8004)      │ │Service (8006)│ │Service (8005)│
└──────┬───────┘ └──────┬───────┘ └──────┬───────┘
       │                │                 │
       │                │                 │
       ▼                ▼                 ▼
┌─────────────────────────────────────────────┐
│         Feign Client (api 模块)              │
│   服务间通信：/inner 路径约定                │
└─────────────────────────────────────────────┘
       │                │                 │
       ▼                ▼                 ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│    MySQL     │ │    Redis     │ │   RabbitMQ   │
└──────────────┘ └──────────────┘ └──────┬───────┘
                                          │
                                          ▼
                                   ┌──────────────┐
                                   │Judge Service │
                                   │  (8002)      │
                                   │  Docker 沙箱  │
                                   └──────────────┘
```

## 模块架构

### 基础设施层
- **gateway/**: Spring Cloud Gateway (JWT 认证过滤器、全局异常处理)
- **api/**: Feign 客户端接口 (UserInterface, ProblemInterface, ContestInterface)
- **common/**:
  - `common-core`: 基础实体、枚举、Result 包装类、JWT 工具、常量
  - `common-web`: 全局异常处理、UserInterceptor 拦截器
  - `common-redis`: Redis 配置、RankingManager 排行榜管理器
  - `common-swagger`: Swagger 配置

### 业务服务层
- **user** (8004): 用户管理、注册、登录、统计
- **problem** (8006): 题目 CRUD、测试用例、提交记录、排行榜
- **judge** (8002): Docker 沙箱代码执行、判题策略（无 REST API）
- **contest** (8005): 竞赛管理、报名、权限验证、排名
- **system** (8003): 后台管理
- **job** (8001): 定时任务（无 REST API）

## 核心业务流程

### 1. 异步判题流程

```
用户提交代码
    ↓
Problem Service 接收请求
    ↓
创建提交记录（状态：WAITING）
    ↓
发送消息到 RabbitMQ (oj.judge.queue)
    ↓
返回 submitId 给前端
    ↓
Judge Service 消费消息
    ↓
获取提交记录和测试用例
    ↓
Docker 沙箱执行代码
    ↓
JudgeManager 判题（策略模式）
    ↓
回调 Problem Service 更新结果
    ↓
更新提交状态（AC/WA/TLE/MLE/CE 等）
    ↓
如果是 AC → 更新用户统计 → 更新排行榜
    ↓
前端轮询获取最终结果
```

### 2. 用户认证流程

```
用户登录
    ↓
User Service 验证用户名密码
    ↓
生成 JWT Token
    ↓
返回 Token 和用户信息
    ↓
后续请求携带 Token
    ↓
Gateway 验证 Token
    ↓
提取 userId 并放入请求头（X-User-Id）
    ↓
UserInterceptor 拦截请求
    ↓
从请求头读取 userId 到 UserContext（ThreadLocal）
    ↓
业务代码从 UserContext 获取当前用户
```

### 3. 排行榜系统

#### 排行榜维度
- **总榜**: 所有历史 AC 记录
- **日榜**: 当天 AC 记录（过期时间：3天）
- **周榜**: 本周 AC 记录（过期时间：7天）
- **月榜**: 本月 AC 记录（过期时间：30天）
- **竞赛榜**: 竞赛期间的 AC 记录

#### Redis Key 设计
```
oj:rank:total                    # 总榜 ZSet
oj:rank:daily:yyyyMMdd           # 日榜 ZSet
oj:rank:weekly:yyyyww            # 周榜 ZSet
oj:rank:monthly:yyyyMM           # 月榜 ZSet
oj:rank:contest:{contestId}      # 竞赛排名 ZSet
oj:solved:{userId}               # 用户已解决的题目 Set（去重用）
```

#### 排行榜去重机制
```java
String solvedKey = "oj:solved:" + userId;
Boolean isFirstAC = redisTemplate.opsForSet().isMember(solvedKey, problemId);

if (Boolean.FALSE.equals(isFirstAC)) {
    redisTemplate.opsForSet().add(solvedKey, problemId);
    rankingManager.addUserScore(userId, "total", 1);
}
```

### 4. 竞赛提交流程

```
用户报名竞赛
    ↓
Contest Service 验证权限
    ↓
创建报名记录（tb_contest_registration）
    ↓
用户查看竞赛题目
    ↓
验证：是否已报名 + 竞赛是否进行中
    ↓
用户提交代码
    ↓
额外校验：是否在竞赛时间内
    ↓
判题后更新竞赛排行榜（oj:rank:contest:{contestId}）
    ↓
实时排名查询
```

## 数据库设计

### 核心数据表

| 表名 | 说明 | 主要字段 |
|-----|------|---------|
| tb_user | 用户表 | user_id, user_account, password, nick_name, email, phone, school, status, submitted_count, accepted_count, rating, password_version |
| tb_problem | 题目表 | problem_id, title, difficulty, submit_num, accepted_num, description, input_description, output_description, time_limit, memory_limit, stack_limit, sample_input, sample_output, hint, source, status |
| tb_test_case | 测试用例表 | case_id, problem_id, input, output |
| tb_submit_record | 提交记录表 | submit_id, problem_id, contest_id, user_id, code, language, status, judge_result, time_cost, memory_cost, error_message, case_result, score, pass_case_count, total_case_count |
| tb_contest | 竞赛表 | contest_id, title, description, status, start_time, end_time |
| tb_contest_problem | 竞赛题目关联表 | id, contest_id, problem_id, display_id, display_title |
| tb_contest_registration | 竞赛报名表 | id, contest_id, user_id |
| tb_problem_tag | 题目标签表 | tag_id, tag_name, tag_color |
| tb_problem_tag_relation | 题目标签关联表 | id, problem_id, tag_id |
| tb_solution | 题解表 | solution_id, problem_id, user_id, title, content, cover, visit_count, like_count, reply_count, type, status |
| tb_sys_user | 系统管理员表 | user_id, user_account, password, nick_name, password_version |

### 初始化数据

**默认管理员账号**: `admin` / `123456`

## 核心枚举类型

### JudgeResultEnum（判题结果）
```java
ACCEPTED(1, "通过 (AC)")
WRONG_ANSWER(2, "答案错误 (WA)")
TIME_LIMIT_EXCEEDED(3, "运行超时 (TLE)")
MEMORY_LIMIT_EXCEEDED(4, "内存超限 (MLE)")
RUNTIME_ERROR(5, "运行错误 (RE)")
COMPILE_ERROR(6, "编译错误 (CE)")
SYSTEM_ERROR(7, "系统错误 (SE)")
```

### SubmitStatusEnum（提交状态）
```java
WAITING(10, "等待判题")
JUDGING(20, "判题中")
SUCCEED(30, "判题完成")
FAILED(40, "判题失败")
```

### ContestStatusEnum（竞赛状态）
```java
NOT_STARTED(0, "未开始")
ONGOING(1, "进行中")
ENDED(2, "已结束")
```

### ProblemDifficultyEnum（题目难度）
```java
EASY(1, "简单")
MEDIUM(2, "中等")
HARD(3, "困难")
```

### ProblemStatusEnum（题目状态）
```java
HIDDEN(0, "隐藏")
NORMAL(1, "正常")
```

### UserStatusEnum（用户状态）
```java
DISABLED(0, "禁用")
NORMAL(1, "正常")
```

### SolutionStatusEnum（题解状态）
```java
DRAFT(0, "草稿")
PUBLISHED(1, "发布")
OFFLINE(2, "下架")
```

### SolutionTypeEnum（题解类型）
```java
USER(0, "用户题解")
OFFICIAL(1, "官方题解")
```

## 配置说明

### Nacos 配置
```yaml
spring:
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR}
      config:
        namespace: ${NACOS_NAMESPACE}
        group: DEFAULT_GROUP
        file-extension: yaml
        shared-configs:
          - data-id: common.yaml
            refresh: true
          - data-id: jwt.yaml
            refresh: true
```

### RabbitMQ 配置
```yaml
spring:
  rabbitmq:
    host: localhost
    port: 5672
    username: guest
    password: guest
    virtual-host: oj-system

# 队列配置（Constants 类中定义）
oj.judge.queue        # 判题队列
oj.judge.exchange     # 交换机（Direct）
oj.judge.routing.key  # 路由键
```

### Docker 沙箱配置
```java
// Constants 类中定义
SANDBOX_IMAGE = "liren-oj-sandbox:v1"
SANDBOX_TIME_OUT = 10000L      // 超时时间：10秒
SANDBOX_MEMORY_LIMIT = 100 * 1000 * 1000L  // 内存限制：100MB
SANDBOX_CPU_COUNT = 1L         // CPU 核心数：1核
```

## 完整 REST API 接口

### 用户服务 (User Service: 8004)

#### UserController `/user`
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| POST | `/user/login` | 用户登录 | `UserLoginDTO` | `Result<UserLoginVO>` |
| GET | `/user/info` | 获取当前用户信息 | - | `Result<UserVO>` |
| POST | `/user/register` | 用户注册 | `UserRegisterDTO` | `Result<Long>` |
| POST | `/user/forget/send-code` | 发送忘记密码验证码 | `UserSendCodeDTO` | `Result<Void>` |
| POST | `/user/forget/reset` | 重置密码 | `UserResetPassDTO` | `Result<Void>` |
| POST | `/user/update/my` | 更新个人信息 | `UserUpdateMyDTO` | `Result<Boolean>` |

#### UserInnerController `/user/inner`（Feign 内部调用）
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| GET | `/user/inner/getBatchBasicInfo` | 批量获取用户基本信息 | `userIds` (List<Long>) | `Result<List<UserBasicInfoDTO>>` |
| POST | `/user/inner/update/stats` | 更新用户统计信息 | `userId` (Long), `isAc` (Boolean) | `Result<Boolean>` |

### 题目服务 (Problem Service: 8006)

#### ProblemController `/problem`
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| POST | `/problem/add` | 新增题目 | `ProblemAddDTO` | `Result<Boolean>` |
| POST | `/problem/list/page` | 分页获取题目列表 | `ProblemQueryRequest` | `Result<Page<ProblemVO>>` |
| GET | `/problem/detail/{problemId}` | 获取题目详情 | `problemId` (Path) | `Result<ProblemDetailVO>` |
| POST | `/problem/submit` | 提交代码 | `ProblemSubmitDTO` | `Result<String>` (submitId) |
| GET | `/problem/submit/result/{submitId}` | 查询提交记录详情 | `submitId` (Path) | `Result<SubmitRecordVO>` |
| POST | `/problem/submit/result/list` | 获取当前题目的提交记录列表 | `ProblemSubmitQueryRequest` | `Result<Page<SubmitRecordVO>>` |
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
| GET | `/contest/inner/isContestEnded` | 判断比赛是否已经结束 | `contestId` (Query) | `Result<Boolean>` |

### 系统服务 (System Service: 8003)

#### SystemUserController `/system/user`
| 方法 | 路径 | 功能 | 参数 | 返回值 |
|-----|------|-----|------|--------|
| POST | `/system/user/login` | 管理员登录 | `SystemUserLoginDTO` | `Result<SystemUserLoginVO>` |

### Feign 内部接口定义

#### UserInterface（api 模块）
```java
@FeignClient(value = "user-service", path = "/user/inner")
```
- `getBatchUserBasicInfo(@RequestParam("userIds") List<Long> userIds)` - 批量获取用户基本信息
- `updateUserStats(@RequestParam("userId") Long userId, @RequestParam("isAc") Boolean isAc)` - 更新用户统计

#### ProblemInterface（api 模块）
```java
@FeignClient(name = "problem-service", path = "/problem/inner")
```
- `updateSubmitResult(ProblemSubmitUpdateDTO problemSubmitUpdateDTO)` - 更新提交结果
- `getTestCases(@PathVariable("problemId") Long problemId)` - 获取测试用例
- `getSubmitRecord(@PathVariable("submitId") Long submitId)` - 获取提交记录
- `getProblemBasicInfo(@PathVariable("problemId") Long problemId)` - 获取题目基本信息

#### ContestInterface（api 模块）
```java
@FeignClient(name = "contest-service", path = "/contest/inner")
```
- `validateContestPermission(@RequestParam("contestId") Long contestId, @RequestParam("userId") Long userId)` - 验证竞赛权限
- `hasAccess(@RequestParam("contestId") Long contestId, @RequestParam("userId") Long userId)` - 校验题目查看权限
- `getContestIdByProblemId(@RequestParam("problemId") Long problemId)` - 根据题目获取比赛ID
- `isContestOngoing(@RequestParam("contestId") Long contestId)` - 判断比赛是否进行中
- `isContestEnded(@RequestParam("contestId") Long contestId)` - 判断比赛是否已经结束

### 接口统计
| 服务模块 | 公开接口 | 内部接口 | 总计 |
|---------|---------|---------|------|
| 用户服务 | 6 | 2 | 8 |
| 题目服务 | 11 | 4 | 15 |
| 竞赛服务 | 8 | 5 | 13 |
| 系统服务 | 1 | 0 | 1 |
| **总计** | **26** | **11** | **37** |

## 关键设计模式

### 1. 策略模式（判题）
```java
// JudgeManager 使用策略模式
public class JudgeManager {
    private JudgeStrategy judgeStrategy;

    public JudgeContext doJudge(JudgeContext judgeContext) {
        return judgeStrategy.doJudge(judgeContext);
    }
}
```

### 2. 异步消息模式
```java
// Problem Service 发送消息
rabbitTemplate.convertAndSend(
    "oj.judge.exchange",
    "oj.judge.routing.key",
    submitId
);

// Judge Service 消费消息
@RabbitListener(queues = "oj.judge.queue")
public void receiveMessage(Long submitId) {
    // 判题逻辑
}
```

### 3. 模板方法模式
```java
// RankingManager 定义排行榜操作模板
public void addRankingEntry(String rankType, Long userId, Long score);
public List<RankItemVO> getRanking(String rankType, int limit);
```

### 4. 责任链模式
```java
// Gateway Filter Chain
GlobalFilter -> JWT Filter -> Route Filter
```

## 异常处理

### 统一异常处理
所有服务使用 `BizException` 抛出业务异常：

```java
// 抛出异常
throw new BizException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");

// 全局异常处理器（ExceptionAdvice）
@ExceptionHandler(Exception.class)
public Result handleException(Exception e) {
    // 记录日志
    // 返回统一格式
}
```

### 错误码定义（ResultCode）
```java
SUCCESS(0, "成功")
PARAM_ERROR(40000, "请求参数错误")
NOT_FOUND_ERROR(40400, "请求数据不存在")
UNAUTHORIZED(40100, "未登录")
FORBIDDEN(40300, "无权限")
OPERATION_ERROR(50000, "操作失败")
```

## 文件位置参考

### 目录结构
```
oj-system-server/
├── api/
│   └── src/main/java/com/liren/api/
│       ├── user/api/UserInterface.java
│       ├── problem/api/ProblemInterface.java
│       └── contest/api/ContestInterface.java
│
├── common/
│   ├── common-core/
│   │   └── src/main/java/com/liren/common/core/
│   │       ├── enums/           # 枚举定义
│   │       ├── constants/       # 常量定义
│   │       ├── exception/       # 异常定义
│   │       ├── context/         # UserContext
│   │       ├── result/          # Result 包装类
│   │       └── utils/           # 工具类
│   ├── common-web/
│   │   └── src/main/java/com/liren/common/web/
│   │       ├── advice/          # 全局异常处理
│   │       ├── config/          # WebMvc 配置
│   │       └── interceptor/     # UserInterceptor
│   ├── common-redis/
│   │   └── src/main/java/com/liren/common/redis/
│   │       ├── config/          # Redis 配置
│   │       └── RankingManager.java
│   └── common-swagger/
│       └── src/main/java/com/liren/common/swagger/
│           └── config/          # Swagger 配置
│
├── gateway/
│   └── src/main/java/com/liren/gateway/
│       ├── filter/              # JWT 过滤器
│       └── properties/          # 配置属性类
│
└── modules/
    ├── user/
    │   └── src/main/java/com/liren/user/
    │       ├── controller/      # UserController, UserInnerController
    │       ├── service/         # IUserService
    │       ├── service/impl/    # UserServiceImpl
    │       ├── entity/          # UserEntity
    │       ├── dto/             # 请求 DTO
    │       └── vo/              # 返回 VO
    │
    ├── problem/
    │   └── src/main/java/com/liren/problem/
    │       ├── controller/
    │       ├── service/
    │       ├── entity/
    │       ├── dto/
    │       └── vo/
    │
    ├── judge/
    │   └── src/main/java/com/liren/judge/
    │       ├── config/          # Docker 配置
    │       ├── sandbox/         # 沙箱接口和实现
    │       ├── strategy/        # 判题策略
    │       └── receiver/        # RabbitMQ 消费者
    │
    ├── contest/
    │   └── src/main/java/com/liren/contest/
    │       ├── controller/
    │       ├── service/
    │       └── entity/
    │
    ├── system/
    │   └── src/main/java/com/liren/system/
    │       └── controller/
    │
    └── job/
        └── src/main/java/com/liren/job/
```

### 应用入口
- 位置：`modules/*/src/main/java/com/liren/{module}/*Application.java`

### 控制器
- 位置：`modules/*/src/main/java/com/liren/{module}/controller/`
- 命名：`*Controller.java`
- 内部控制器：`*InnerController.java`

### 服务层
- 位置：`modules/*/src/main/java/com/liren/{module}/service/`
- 接口：`I*Service.java`
- 实现：`impl/*ServiceImpl.java`

### 实体类
- 位置：`modules/*/src/main/java/com/liren/{module}/entity/`
- 命名：`*Entity.java`

### DTO/VO
- DTO（请求参数）：各模块的 `dto/` 包下
- VO（返回对象）：各模块的 `vo/` 包下

## 常见开发任务

### 1. 添加新的 REST API

```java
// 1. 创建 DTO（请求参数）
@Data
public class YourDTO {
    private Long id;
    private String name;
}

// 2. 创建 VO（返回对象）
@Data
public class YourVO {
    private Long id;
    private String result;
}

// 3. 创建 Controller
@RestController
@RequestMapping("/your-path")
public class YourController {

    @PostMapping("/action")
    public Result<YourVO> yourAction(@RequestBody YourDTO yourDTO) {
        // 业务逻辑
        return Result.success(yourVO);
    }
}
```

### 2. 添加新的 Feign 客户端

```java
// 1. 在 api 模块创建 Feign 接口
@FeignClient(name = "target-service", path = "/target-service/inner")
public interface TargetServiceInterface {
    @GetMapping("/api")
    Result<TargetVO> getTarget(@RequestParam("id") Long id);
}

// 2. 在目标服务实现内部接口
@RestController
@RequestMapping("/target-service/inner")
public class TargetInnerController {

    @GetMapping("/api")
    public Result<TargetVO> getTarget(@RequestParam("id") Long id) {
        // 实现
    }
}
```

### 3. 使用排行榜

```java
// 注入 RankingManager
@Autowired
private RankingManager rankingManager;

// 添加用户分数（AC 后）
Long userId = UserContext.getUserId();
Long problemId = ...;

// 检查是否首次 AC（去重）
String solvedKey = "oj:solved:" + userId;
Boolean isFirstAC = redisTemplate.opsForSet()
    .isMember(solvedKey, problemId);

if (Boolean.FALSE.equals(isFirstAC)) {
    // 首次 AC，更新排行榜
    redisTemplate.opsForSet().add(solvedKey, problemId);
    rankingManager.addUserScore(userId, "total", 1);
}

// 获取排行榜
List<RankItemVO> rankings = rankingManager.getRanking("total", 10);
```

### 4. 添加新的枚举

```java
// 在 common/common-core/src/main/java/com/liren/common/core/enums/ 下创建
package com.liren.common.core.enums;

@Getter
@AllArgsConstructor
public enum YourEnum {
    VALUE1(1, "值1"),
    VALUE2(2, "值2");

    private final int code;
    private final String desc;
}
```

### 5. 异常处理

```java
// 抛出业务异常
if (user == null) {
    throw new BizException(ResultCode.NOT_FOUND_ERROR, "用户不存在");
}
```

## 性能优化要点

### 1. Redis 缓存策略
- 排行榜使用 ZSet，O(log N) 复杂度
- 用户已解决题目使用 Set 去重
- 合理设置过期时间（日榜3天、周榜7天、月榜30天）

### 2. 数据库优化
- 建立合适的索引（user_id、problem_id、create_time）
- 使用批量查询减少数据库交互

### 3. 异步处理
- 判题使用 RabbitMQ 异步处理
- 避免长时间阻塞请求

### 4. 连接池配置
- 数据库连接池（HikariCP）
- Redis 连接池（Lettuce）
- RabbitMQ 连接工厂

## 关键设计特点总结

1. **微服务架构**: Spring Cloud Alibaba，服务拆分合理
2. **分层架构**: 外部接口使用普通路径，内部接口统一使用 `/inner` 前缀
3. **服务通信**: 所有跨服务调用通过 Feign Client 实现
4. **权限控制**: JWT 认证 + Gateway 统一鉴权 + 竞赛权限验证
5. **异步处理**: 代码提交通过 RabbitMQ 异步判题
6. **高性能排行榜**: Redis ZSet 实现多维度排行榜，支持实时更新
7. **去重机制**: 每道题目只有首次 AC 才会计入排行榜
8. **策略模式**: 判题使用策略模式，易于扩展
9. **Docker 沙箱**: 安全的代码执行环境
10. **统一异常处理**: 全局异常捕获，统一返回格式
