# CLAUDE.md

此文件为 Claude Code (claude.ai/code) 提供在此代码仓库中工作的指导。

## 项目概述

这是一个基于 Spring Cloud Alibaba 的微服务在线判题（OJ）系统。系统处理题目提交、使用 Docker 沙箱进行异步代码判题、竞赛管理和用户排行榜。

### 技术栈
- **框架**：Spring Cloud Alibaba、Spring Boot
- **服务注册/配置**：Nacos
- **网关**：Spring Cloud Gateway
- **消息队列**：RabbitMQ
- **缓存**：Redis（排行榜、用户AC记录、会话缓存）
- **代码执行**：Docker 沙箱
- **数据库**：MySQL
- **服务调用**：OpenFeign
- **API 文档**：Swagger/Knife4j

## 基础设施要求

运行任何服务前，请确保以下组件已启动：
- **Nacos**（服务注册与配置中心）：`lirendada.art:8848`
- **MySQL**：数据库初始化脚本位于 `deploy/oj_system.sql`
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

## 项目架构

### 整体架构图

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

### 模块架构

#### 基础设施层
- **gateway/**：Spring Cloud Gateway
  - JWT 认证过滤器
  - 路由转发
  - 全局异常处理

- **api/**：Feign 客户端接口和跨服务 DTO
  - `UserInterface` - 用户服务 Feign 接口
  - `ProblemInterface` - 题目服务 Feign 接口
  - `ContestInterface` - 竞赛服务 Feign 接口

- **common/**：公共组件
  - `common-core`：基础实体、枚举、Result 包装类、JWT 工具、常量定义
  - `common-web`：全局异常处理、用户上下文拦截器（UserInterceptor）
  - `common-redis`：Redis 配置、`RankingManager` 排行榜管理器
  - `common-swagger`：API 文档配置

#### 业务服务层
- **user**（8004）：用户管理、注册、登录、统计信息
- **problem**（8006）：题目 CRUD、测试用例、提交记录、排行榜
- **judge**（8002）：Docker 沙箱代码执行、判题策略（无 REST API）
- **contest**（8005）：竞赛管理、报名、权限验证、排名
- **system**（8003）：后台管理
- **job**（8001）：定时任务（无 REST API）

## 核心业务流程

### 1. 异步判题流程

```
用户提交代码
    ↓
Problem Service 接收请求
    ↓
创建提交记录（状态：PENDING）
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
更新提交状态（AC/WA/TLE/MLE/CE等）
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
生成 JWT Token（密钥：Constants.JWT_SECRET）
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
- **总榜**（Total）：所有历史 AC 记录
- **日榜**（Daily）：当天 AC 记录（过期时间：3天）
- **周榜**（Weekly）：本周 AC 记录（过期时间：7天）
- **月榜**（Monthly）：本月 AC 记录（过期时间：30天）
- **竞赛榜**（Contest）：竞赛期间的 AC 记录

#### Redis Key 设计
```
oj:rank:total                    # 总榜 ZSet
oj:rank:daily:yyyyMMdd           # 日榜 ZSet（如：oj:rank:daily:20250112）
oj:rank:weekly:yyyyww            # 周榜 ZSet（如：oj:rank:weekly:202502）
oj:rank:monthly:yyyyMM           # 月榜 ZSet（如：oj:rank:monthly:202501）
oj:contest:score:{contestId}     # 竞赛排名 ZSet
oj:user:solved:{userId}          # 用户已解决的题目 Set（去重用）
```

#### 排行榜去重机制
```java
// 每个用户维护一个已解决题目的 Set
String solvedKey = "oj:user:solved:" + userId;

// 判断是否首次 AC
Boolean isFirstAC = redisTemplate.opsForSet()
    .isMember(solvedKey, problemId);

if (!isFirstAC) {
    // 只有首次 AC 才加入 Set 并更新排行榜
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
判题后更新竞赛排行榜（oj:contest:score:{contestId}）
    ↓
实时排名查询
```

## 数据库设计

### 核心数据表

#### 1. tb_user（用户表）
```sql
主要字段：
- user_id          BIGINT       用户ID（主键）
- user_account     VARCHAR(50)  账号（唯一）
- password         VARCHAR(255) 密码（加密）
- nick_name        VARCHAR(50)  昵称
- avatar           VARCHAR(500) 头像URL
- email            VARCHAR(100) 邮箱（唯一）
- phone            VARCHAR(20)  手机号
- school           VARCHAR(100) 学校
- status           TINYINT      状态（0-禁用 1-正常）
- submitted_count  INT          提交次数
- accepted_count   INT          通过次数
- rating           INT          Rating分数（默认1500）
- create_time      DATETIME     创建时间
- update_time      DATETIME     更新时间
- deleted          TINYINT      逻辑删除（0-未删除 1-已删除）

索引：
- UNIQUE KEY uk_user_account (user_account)
- UNIQUE KEY uk_email (email)
```

#### 2. tb_problem（题目表）
```sql
主要字段：
- problem_id       BIGINT       题目ID（主键）
- title            VARCHAR(200) 题目标题
- difficulty       TINYINT      难度（1-简单 2-中等 3-困难）
- submit_num       INT          提交总数
- accepted_num     INT          通过总数
- description      LONGTEXT     题目描述（支持Markdown）
- input_description TEXT        输入描述
- output_description TEXT       输出描述
- time_limit       INT          时间限制（ms，默认1000）
- memory_limit     INT          内存限制（MB，默认128）
- stack_limit      INT          栈限制（MB，默认128）
- sample_input     TEXT         样例输入（展示用）
- sample_output    TEXT         样例输出（展示用）
- hint             TEXT         提示
- source           VARCHAR(200) 来源
- status           TINYINT      状态（0-隐藏 1-正常）
- create_by        BIGINT       创建人
- update_by        BIGINT       更新人
- create_time      DATETIME     创建时间
- update_time      DATETIME     更新时间
- deleted          TINYINT      逻辑删除

索引：
- KEY idx_difficulty (difficulty)
```

#### 3. tb_test_case（测试用例表）
```sql
主要字段：
- case_id          BIGINT       用例ID（主键）
- problem_id       BIGINT       题目ID
- input            LONGTEXT     输入数据（大文本）
- output           LONGTEXT     期望输出（大文本）
- create_time      DATETIME     创建时间
- update_time      DATETIME     更新时间

索引：
- KEY idx_problem_id (problem_id)
```

#### 4. tb_submit_record（提交记录表）
```sql
主要字段：
- submit_id        BIGINT       提交ID（主键）
- problem_id       BIGINT       题目ID
- contest_id       BIGINT       竞赛ID（0表示非竞赛提交）
- user_id          BIGINT       用户ID
- code             LONGTEXT     提交的代码
- language         VARCHAR(20)  编程语言（Java/C++/Python）
- status           TINYINT      判题状态（10-待判题 20-判题中 30-结束）
- judge_result     TINYINT      判题结果（0-AC, 1-WA, 2-TLE, 3-MLE, 4-RE, 5-CE等）
- time_cost        INT          最大耗时（ms）
- memory_cost      INT          最大内存（KB）
- error_message    TEXT         错误信息（编译错误/运行错误）
- case_result      JSON         每个测试点的详细结果JSON
- score            INT          得分
- pass_case_count  INT          通过用例数
- total_case_count INT          总用例数
- create_time      DATETIME     提交时间
- update_time      DATETIME     更新时间

索引：
- KEY idx_problem_id (problem_id)
- KEY idx_user_id (user_id)
- KEY idx_contest_id (contest_id)
```

#### 5. tb_contest（竞赛表）
```sql
主要字段：
- contest_id       BIGINT       竞赛ID（主键）
- title            VARCHAR(200) 竞赛标题
- description      TEXT         竞赛描述
- status           TINYINT      状态（0-未开始 1-进行中 2-已结束）
- start_time       DATETIME     开始时间
- end_time         DATETIME     结束时间
- create_by        BIGINT       创建人
- update_by        BIGINT       更新人
- create_time      DATETIME     创建时间
- update_time      DATETIME     更新时间
- deleted          TINYINT      逻辑删除
```

#### 6. tb_contest_problem（竞赛题目关联表）
```sql
主要字段：
- id               BIGINT       主键ID（自增）
- contest_id       BIGINT       竞赛ID
- problem_id       BIGINT       题目ID
- display_id       VARCHAR(10)  展示编号（A, B, C...）
- display_title    VARCHAR(200) 竞赛中展示的标题（可选）

索引：
- UNIQUE KEY uk_contest_problem (contest_id, problem_id)
- UNIQUE KEY uk_contest_display (contest_id, display_id)
```

#### 7. tb_contest_registration（竞赛报名表）
```sql
主要字段：
- id               BIGINT       主键ID（自增）
- contest_id       BIGINT       竞赛ID
- user_id          BIGINT       用户ID
- create_time      DATETIME     报名时间

索引：
- UNIQUE KEY uk_contest_user (contest_id, user_id)
```

#### 8. tb_problem_tag（题目标签表）
```sql
主要字段：
- tag_id           BIGINT       标签ID（主键）
- tag_name         VARCHAR(50)  标签名称
- tag_color        VARCHAR(20)  标签颜色（如#FF0000）

索引：
- UNIQUE KEY uk_tag_name (tag_name)
```

#### 9. tb_problem_tag_relation（题目标签关联表）
```sql
主要字段：
- id               BIGINT       主键ID（自增）
- problem_id       BIGINT       题目ID
- tag_id           BIGINT       标签ID

索引：
- UNIQUE KEY uk_problem_tag (problem_id, tag_id)
- KEY idx_tag_id (tag_id)
```

#### 10. tb_solution（题解表）
```sql
主要字段：
- solution_id      BIGINT       题解ID（主键）
- problem_id       BIGINT       题目ID
- user_id          BIGINT       发布用户ID
- title            VARCHAR(100) 题解标题
- content          LONGTEXT     题解内容（Markdown）
- cover            VARCHAR(500) 封面图
- visit_count      INT          浏览量
- like_count       INT          点赞数
- reply_count      INT          评论数
- type             TINYINT      类型（0-用户题解 1-官方题解）
- status           TINYINT      状态（0-草稿 1-发布 2-下架）
- create_time      DATETIME     创建时间
- update_time      DATETIME     更新时间
- deleted          TINYINT      逻辑删除

索引：
- KEY idx_problem_id (problem_id)
- KEY idx_user_id (user_id)
```

#### 11. tb_sys_user（系统管理员表）
```sql
主要字段：
- user_id          BIGINT       管理员ID（主键）
- user_account     VARCHAR(50)  账号（唯一）
- password         VARCHAR(255) 密码
- nick_name        VARCHAR(50)  昵称
- create_by        BIGINT       创建人
- update_by        BIGINT       更新人
- create_time      DATETIME     创建时间
- update_time      DATETIME     更新时间
- deleted          TINYINT      逻辑删除（0-未删除 1-已删除）

索引：
- UNIQUE KEY uk_user_account (user_account)
```

### 初始化数据

**默认管理员账号**：
- 账号：`admin`
- 密码：`123456`

**默认测试用户**：
- `user001` / `123456`
- `user002` / `123456`

### 数据库初始化

执行以下 SQL 脚本初始化数据库：
```bash
mysql -u root -p oj_system < deploy/oj_system.sql
```

## 核心枚举类型

### 1. JudgeResultEnum（判题结果）
```java
// 对应数据库字段：tb_submit_record.judge_result (TINYINT)
ACCEPTED(0, "成功", "Accepted"),
WRONG_ANSWER(1, "答案错误", "Wrong Answer"),
TIME_LIMIT_EXCEEDED(2, "超时", "Time Limit Exceeded"),
MEMORY_LIMIT_EXCEEDED(3, "超内存", "Memory Limit Exceeded"),
RUNTIME_ERROR(4, "运行错误", "Runtime Error"),
COMPILE_ERROR(5, "编译错误", "Compile Error"),
SYSTEM_ERROR(6, "系统错误", "System Error");
```

### 2. SubmitStatusEnum（提交状态）
```java
// 对应数据库字段：tb_submit_record.status (TINYINT)
PENDING(10, "等待判题"),
JUDGING(20, "判题中"),
SUCCESS(30, "判题完成");
```

### 3. LanguageEnum（编程语言）
```java
// 对应数据库字段：tb_submit_record.language (VARCHAR)
JAVA("java", "Java"),
CPP("cpp", "C++"),
PYTHON("python", "Python");
```

### 4. ContestStatusEnum（竞赛状态）
```java
// 对应数据库字段：tb_contest.status (TINYINT)
NOT_STARTED(0, "未开始"),
ONGOING(1, "进行中"),
ENDED(2, "已结束");
```

### 5. ProblemDifficultyEnum（题目难度）
```java
// 对应数据库字段：tb_problem.difficulty (TINYINT)
EASY(1, "简单"),
MEDIUM(2, "中等"),
HARD(3, "困难");
```

### 6. ProblemStatusEnum（题目状态）
```java
// 对应数据库字段：tb_problem.status (TINYINT)
HIDDEN(0, "隐藏"),
NORMAL(1, "正常");
```

### 7. SolutionStatusEnum（题解状态）
```java
// 对应数据库字段：tb_solution.status (TINYINT)
DRAFT(0, "草稿"),
PUBLISHED(1, "发布"),
OFFLINE(2, "下架");
```

### 8. SolutionTypeEnum（题解类型）
```java
// 对应数据库字段：tb_solution.type (TINYINT)
USER(0, "用户题解"),
OFFICIAL(1, "官方题解");
```

## 配置说明

### Nacos 配置

所有服务使用 `bootstrap.yml` 配置：

```yaml
spring:
  cloud:
    nacos:
      server-addr: lirendada.art:8848
      config:
        namespace: 3e8d6cf0-32cd-43c0-8279-9fda3da2265f
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

# 队列配置
oj.judge.queue        # 判题队列
oj.judge.exchange     # 交换机（Direct）
oj.judge.routing.key  # 路由键
```

### Docker 沙箱配置

```java
// 定义在 Constants 类中
SANDBOX_TIME_OUT = 10000       // 超时时间：10秒
SANDBOX_MEMORY_LIMIT = 100000  // 内存限制：100MB
SANDBOX_CPU_COUNT = 1          // CPU 核心数：1核

// 沙箱镜像
liren-oj-sandbox:v1
```

### Redis 配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 5000ms
```

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

### 错误码定义（ErrorCode）

```java
SUCCESS(0, "成功"),
PARAMS_ERROR(40000, "请求参数错误"),
NOT_FOUND_ERROR(40400, "请求数据不存在"),
NOT_LOGIN(40100, "未登录"),
NO_AUTH(40300, "无权限"),
OPERATION_ERROR(50000, "操作失败");
```

## 文件位置参考

### 目录结构
```
oj-system/
├── api/                          # Feign 接口定义
│   └── src/main/java/com/liren/api/
│       ├── user/api/UserInterface.java
│       ├── problem/api/ProblemInterface.java
│       └── contest/api/ContestInterface.java
│
├── common/                       # 公共模块
│   ├── common-core/             # 核心工具类、枚举、常量
│   │   └── src/main/java/com/liren/common/core/
│   │       ├── enums/           # 枚举定义
│   │       ├── constants/       # 常量定义
│   │       └── exception/       # 异常定义
│   ├── common-web/              # Web 配置、拦截器
│   ├── common-redis/            # Redis 配置、RankingManager
│   └── common-swagger/          # Swagger 配置
│
├── gateway/                     # 网关服务
│   └── src/main/java/com/liren/gateway/
│       └── filter/              # 过滤器（JWT、日志等）
│
└── modules/                     # 业务服务模块
    ├── user/                    # 用户服务
    │   └── src/main/java/com/liren/user/
    │       ├── controller/      # 控制器
    │       ├── service/         # 服务层
    │       │   └── impl/        # 服务实现
    │       └── entity/          # 实体类
    │
    ├── problem/                 # 题目服务
    │   └── src/main/java/com/liren/problem/
    │       ├── controller/
    │       ├── service/
    │       └── entity/
    │
    ├── judge/                   # 判题服务
    │   └── src/main/java/com/liren/judge/
    │       ├── config/          # Docker 配置
    │       ├── sandbox/         # 沙箱接口和实现
    │       ├── strategy/        # 判题策略
    │       └── receiver/        # RabbitMQ 消费者
    │
    ├── contest/                 # 竞赛服务
    │   └── src/main/java/com/liren/contest/
    │       ├── controller/
    │       ├── service/
    │       └── entity/
    │
    ├── system/                  # 系统服务
    │   └── src/main/java/com/liren/system/
    │
    └── job/                     # 定时任务
        └── src/main/java/com/liren/job/
```

### 应用入口
- 位置：`modules/*/src/main/java/com/liren/{module}/*Application.java`
- 示例：`modules/user/src/main/java/com/liren/user/UserApplication.java`

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
// 1. 在对应模块的 controller 包下创建或编辑 Controller
@RestController
@RequestMapping("/your-path")
public class YourController {

    @PostMapping("/action")
    public Result<YourVO> yourAction(@RequestBody YourDTO yourDTO) {
        // 业务逻辑
        return Result.success(yourVO);
    }
}

// 2. 创建 DTO（请求参数）
@Data
public class YourDTO {
    private Long id;
    private String name;
}

// 3. 创建 VO（返回对象）
@Data
public class YourVO {
    private Long id;
    private String result;
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
String solvedKey = "oj:user:solved:" + userId;
Boolean isFirstAC = redisTemplate.opsForSet()
    .isMember(solvedKey, problemId);

if (Boolean.TRUE.equals(isFirstAC)) {
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
public enum YourEnum {
    VALUE1(1, "值1"),
    VALUE2(2, "值2");

    private final int code;
    private final String desc;

    YourEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }
}
```

### 5. 异常处理

```java
// 抛出业务异常
if (user == null) {
    throw new BizException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
}

// 自定义错误码（在 ErrorCode 枚举中添加）
YOUR_ERROR(50001, "自定义错误");
```

## 性能优化要点

### 1. Redis 缓存策略
- 排行榜使用 ZSet，O(log N) 复杂度
- 用户已解决题目使用 Set 去重
- 合理设置过期时间（日榜3天、周榜7天、月榜30天）

### 2. 数据库优化
- 提交记录表按时间分表（如需要）
- 建立合适的索引（user_id、problem_id、create_time）
- 使用批量查询减少数据库交互

### 3. 异步处理
- 判题使用 RabbitMQ 异步处理
- 避免长时间阻塞请求
- 前端轮询获取结果

### 4. 连接池配置
- 数据库连接池（HikariCP）
- Redis 连接池（Lettuce）
- RabbitMQ 连接工厂

## 测试建议

### 1. 单元测试
- Service 层业务逻辑测试
- 使用 Mock 模拟依赖

### 2. 集成测试
- API 接口测试
- Feign 调用测试

### 3. 判题测试
- 准备标准测试用例
- 测试边界条件（时间、内存限制）
- 测试异常情况（编译错误、运行时错误）

## 部署注意事项

1. **环境变量**：确保 Nacos、MySQL、Redis、RabbitMQ 地址正确
2. **Docker 镜像**：确保判题沙箱镜像已构建并可用
3. **JWT 密钥**：生产环境使用强密钥
4. **数据库初始化**：执行 `deploy/oj_system.sql`
5. **服务启动顺序**：先启动基础设施，再启动业务服务

---

## 关键设计特点总结

1. **微服务架构**：Spring Cloud Alibaba，服务拆分合理
2. **分层架构**：外部接口使用普通路径，内部接口统一使用 `/inner` 前缀
3. **服务通信**：所有跨服务调用通过 Feign Client 实现
4. **权限控制**：JWT 认证 + Gateway 统一鉴权 + 竞赛权限验证
5. **异步处理**：代码提交通过 RabbitMQ 异步判题
6. **高性能排行榜**：Redis ZSet 实现多维度排行榜，支持实时更新
7. **去重机制**：每道题目只有首次 AC 才会计入排行榜
8. **策略模式**：判题使用策略模式，易于扩展
9. **Docker 沙箱**：安全的代码执行环境
10. **统一异常处理**：全局异常捕获，统一返回格式
