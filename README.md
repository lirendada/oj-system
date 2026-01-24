# Liren OJ System Server

基于 Spring Cloud Alibaba 的微服务在线判题（OJ）系统后端服务。

## 技术栈

| 类别 | 技术 | 版本 |
|------|------|------|
| 核心框架 | Spring Boot | 3.0.1 |
| | Spring Cloud | 2022.0.0 |
| | Spring Cloud Alibaba | 2022.0.0.0-RC2 |
| | Java | 17 |
| 微服务组件 | Nacos | 2.x (服务注册/配置中心) |
| | Spring Cloud Gateway | 网关 |
| | OpenFeign | 服务调用 |
| | RabbitMQ | 消息队列 |
| 数据存储 | MySQL | 8.0+ (MyBatis Plus 3.5.5) |
| | Redis | 6.0+ (Lettuce) |
| 代码执行 | Docker | 沙箱环境 (liren-oj-sandbox:v1) |
| 工具库 | Hutool | 5.8.39 |
| | Lombok | 1.18.30 |
| API 文档 | Swagger/Knife4j | - |

## 系统架构

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

## 模块结构

```
oj-system-server/
├── gateway/                  # 网关服务
│   └── filter/              # JWT 认证过滤器
├── common/                   # 公共模块
│   ├── common-core/         # 核心工具类、枚举、常量、Result 包装类
│   ├── common-web/          # Web 配置、全局异常处理、UserInterceptor
│   ├── common-redis/        # Redis 配置、RankingManager 排行榜管理器
│   └── common-swagger/      # Swagger API 文档配置
├── api/                      # Feign 客户端接口
│   ├── UserInterface        # 用户服务 Feign 接口
│   ├── ProblemInterface     # 题目服务 Feign 接口
│   └── ContestInterface     # 竞赛服务 Feign 接口
├── modules/                  # 业务服务模块
│   ├── user/                # 用户服务 (8004)
│   ├── problem/             # 题目服务 (8006)
│   ├── judge/               # 判题服务 (8002)
│   ├── contest/             # 竞赛服务 (8005)
│   ├── system/              # 系统服务 (8003)
│   └── job/                 # 定时任务 (8001)
└── deploy/                   # 部署相关
    ├── mysql/               # 数据库初始化脚本
    ├── nacos-config/        # Nacos 配置文件
    ├── sandbox/             # Docker 沙箱相关
    └── xxl_job.sql          # XXL-Job 任务调度表
```

## 服务列表

| 服务 | 端口 | 功能描述 |
|-----|------|---------|
| Gateway | 10020 | 网关服务、JWT 认证、路由转发 |
| User Service | 8004 | 用户管理、注册、登录、统计 |
| Problem Service | 8006 | 题目 CRUD、测试用例、提交、排行榜 |
| Judge Service | 8002 | Docker 沙箱代码执行、判题策略 |
| Contest Service | 8005 | 竞赛管理、报名、权限验证、排名 |
| System Service | 8003 | 后台管理 |
| Job Service | 8001 | 定时任务 |

## 快速开始

### 环境要求

- **JDK**: 17+
- **Maven**: 3.x
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **RabbitMQ**: 3.x
- **Nacos**: 2.x
- **Docker**: 20.x+ (用于判题沙箱)

### 基础设施配置

#### 1. Nacos
启动 Nacos 服务端，并导入 `deploy/nacos-config/` 目录下的配置文件。

#### 2. MySQL
```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE oj_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入初始化脚本
mysql -u root -p oj_system < deploy/mysql/oj_system.sql
```

#### 3. Redis & RabbitMQ
确保 Redis 和 RabbitMQ 服务正常运行。

### 配置修改

⚠️ **重要**: 修改 `deploy/nacos-config/common.yaml` 中的以下配置：

- MySQL 连接信息 (url, username, password)
- Redis 连接信息 (host, port, password)
- RabbitMQ 连接信息 (host, port, username, password, virtual-host)
- 邮件服务配置 (可选)

### 构建和启动

```bash
# 构建整个项目
mvn clean package

# 启动网关
cd gateway && mvn spring-boot:run

# 启动各业务服务
cd modules/user && mvn spring-boot:run
cd modules/problem && mvn spring-boot:run
# ... 其他服务
```

### 启动顺序

1. 基础设施: Nacos → MySQL → Redis → RabbitMQ
2. 网关服务: Gateway
3. 业务服务: User → Problem → Judge → Contest → System → Job

## 核心业务流程

### 1. 异步判题流程

```
用户提交代码
    ↓
Problem Service 接收请求
    ↓
创建提交记录 (状态: WAITING)
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
JudgeManager 判题 (策略模式)
    ↓
回调 Problem Service 更新结果
    ↓
更新提交状态 (AC/WA/TLE/MLE/CE 等)
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
Gateway 验证 Token 并提取 userId
    ↓
放入请求头 (X-User-Id)
    ↓
UserInterceptor 拦截请求
    ↓
从请求头读取 userId 到 UserContext (ThreadLocal)
    ↓
业务代码从 UserContext 获取当前用户
```

### 3. 排行榜系统

**排行榜维度**:
- **总榜**: 所有历史 AC 记录
- **日榜**: 当天 AC 记录 (过期时间: 3天)
- **周榜**: 本周 AC 记录 (过期时间: 7天)
- **月榜**: 本月 AC 记录 (过期时间: 30天)
- **竞赛榜**: 竞赛期间的 AC 记录

**Redis Key 设计**:
```
oj:rank:total                    # 总榜 ZSet
oj:rank:daily:yyyyMMdd           # 日榜 ZSet
oj:rank:weekly:yyyyww            # 周榜 ZSet
oj:rank:monthly:yyyyMM           # 月榜 ZSet
oj:rank:contest:{contestId}      # 竞赛排名 ZSet
oj:solved:{userId}               # 用户已解决的题目 Set (去重用)
```

**去重机制**: 每个用户维护一个已解决题目的 Set，只有首次 AC 才更新排行榜。

## REST API 接口

### 用户服务 (User Service: 8004)

| 方法 | 路径 | 功能 |
|-----|------|-----|
| POST | `/user/login` | 用户登录 |
| GET | `/user/info` | 获取当前用户信息 |
| POST | `/user/register` | 用户注册 |
| POST | `/user/forget/send-code` | 发送忘记密码验证码 |
| POST | `/user/forget/reset` | 重置密码 |
| POST | `/user/update/my` | 更新个人信息 |

### 题目服务 (Problem Service: 8006)

| 方法 | 路径 | 功能 |
|-----|------|-----|
| POST | `/problem/add` | 新增题目 |
| POST | `/problem/list/page` | 分页获取题目列表 |
| GET | `/problem/detail/{problemId}` | 获取题目详情 |
| POST | `/problem/submit` | 提交代码 |
| GET | `/problem/submit/result/{submitId}` | 查询提交记录详情 |
| POST | `/problem/submit/result/list` | 获取提交记录列表 |
| GET | `/problem/rank/total` | 获取总榜 Top 10 |
| GET | `/problem/rank/daily` | 获取日榜 Top 10 |
| GET | `/problem/rank/weekly` | 获取周榜 Top 10 |
| GET | `/problem/rank/monthly` | 获取月榜 Top 10 |

### 竞赛服务 (Contest Service: 8005)

| 方法 | 路径 | 功能 |
|-----|------|-----|
| POST | `/contest/add` | 创建/更新比赛 |
| POST | `/contest/list` | 分页查询比赛列表 |
| GET | `/contest/{id}` | 获取比赛详情 |
| POST | `/contest/problem/add` | 添加题目到比赛 |
| GET | `/contest/{contestId}/problems` | 获取比赛题目列表 |
| POST | `/contest/problem/remove` | 移除比赛题目 |
| POST | `/contest/register/{contestId}` | 报名比赛 |
| GET | `/contest/rank/{contestId}` | 获取比赛排名 |

### 系统服务 (System Service: 8003)

| 方法 | 路径 | 功能 |
|-----|------|-----|
| POST | `/system/user/login` | 管理员登录 |

## 核心枚举类型

### 判题结果 (JudgeResultEnum)
| 代码 | 名称 | 说明 |
|-----|------|-----|
| 1 | ACCEPTED | 通过 (AC) |
| 2 | WRONG_ANSWER | 答案错误 (WA) |
| 3 | TIME_LIMIT_EXCEEDED | 运行超时 (TLE) |
| 4 | MEMORY_LIMIT_EXCEEDED | 内存超限 (MLE) |
| 5 | RUNTIME_ERROR | 运行错误 (RE) |
| 6 | COMPILE_ERROR | 编译错误 (CE) |
| 7 | SYSTEM_ERROR | 系统错误 (SE) |

### 提交状态 (SubmitStatusEnum)
| 代码 | 名称 | 说明 |
|-----|------|-----|
| 10 | WAITING | 等待判题 |
| 20 | JUDGING | 判题中 |
| 30 | SUCCEED | 判题完成 |
| 40 | FAILED | 判题失败 |

### 竞赛状态 (ContestStatusEnum)
| 代码 | 名称 | 说明 |
|-----|------|-----|
| 0 | NOT_STARTED | 未开始 |
| 1 | ONGOING | 进行中 |
| 2 | ENDED | 已结束 |

### 题目难度 (ProblemDifficultyEnum)
| 代码 | 名称 | 说明 |
|-----|------|-----|
| 1 | EASY | 简单 |
| 2 | MEDIUM | 中等 |
| 3 | HARD | 困难 |

## 数据库设计

### 核心数据表

| 表名 | 说明 | 主要字段 |
|-----|------|---------|
| tb_user | 用户表 | user_id, user_account, password, nick_name, email, submitted_count, accepted_count, rating |
| tb_problem | 题目表 | problem_id, title, difficulty, description, time_limit, memory_limit |
| tb_test_case | 测试用例表 | case_id, problem_id, input, output |
| tb_submit_record | 提交记录表 | submit_id, problem_id, contest_id, user_id, code, language, status, judge_result |
| tb_contest | 竞赛表 | contest_id, title, status, start_time, end_time |
| tb_contest_problem | 竞赛题目关联表 | id, contest_id, problem_id, display_id |
| tb_contest_registration | 竞赛报名表 | id, contest_id, user_id |
| tb_problem_tag | 题目标签表 | tag_id, tag_name, tag_color |
| tb_problem_tag_relation | 题目标签关联表 | id, problem_id, tag_id |
| tb_solution | 题解表 | solution_id, problem_id, user_id, title, content, status |
| tb_sys_user | 系统管理员表 | user_id, user_account, password, password_version |

### 初始化数据

**默认管理员账号**: `admin` / `123456`

## 常见问题

### 1. 服务启动失败
- 检查 Nacos 是否正常启动
- 检查配置文件是否正确
- 查看日志排查具体错误

### 2. 判题失败
- 检查 Docker 是否正常运行
- 检查沙箱镜像是否存在: `docker images | grep liren-oj-sandbox`
- 检查 RabbitMQ 是否正常

### 3. 排行榜不更新
- 检查 Redis 是否正常
- 检查去重 Set 是否正常工作

## 许可证

MIT License
