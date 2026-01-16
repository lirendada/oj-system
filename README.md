# Liren OJ System Server - 服务端

基于 Spring Cloud Alibaba 的微服务在线判题（OJ）系统后端服务。

## 技术栈

### 核心框架
- **Spring Boot**: 3.0.1
- **Spring Cloud**: 2022.0.0
- **Spring Cloud Alibaba**: 2022.0.0.0-RC2
- **Java**: 17
- **Maven**: 3.x

### 微服务组件
- **服务注册/配置中心**: Nacos
- **网关**: Spring Cloud Gateway
- **服务调用**: OpenFeign
- **消息队列**: RabbitMQ

### 数据存储
- **数据库**: MySQL (MyBatis Plus 3.5.5)
- **缓存**: Redis (Lettuce)

### 代码执行
- **容器**: Docker
- **沙箱**: 自定义沙箱镜像 (liren-oj-sandbox:v1)

### 工具库
- **Hutool**: 5.8.39
- **Lombok**: 1.18.30

## 系统架构

### 整体架构

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

### 模块结构

```
oj-system/
├── gateway/              # 网关服务
├── common/               # 公共模块
│   ├── common-core       # 核心工具类、枚举、常量
│   ├── common-web        # Web 配置、拦截器
│   ├── common-redis      # Redis 配置、排行榜管理器
│   └── common-swagger    # API 文档配置
├── api/                  # Feign 客户端接口
└── modules/              # 业务服务模块
    ├── user              # 用户服务
    ├── problem           # 题目服务
    ├── judge             # 判题服务
    ├── contest           # 竞赛服务
    ├── system            # 系统服务
    └── job               # 定时任务
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

## 核心功能模块

### 1. 用户服务 (User Service)
- 用户注册/登录
- JWT Token 生成
- 用户信息管理
- 用户统计信息(提交数、通过数)
- 批量获取用户基本信息(Feign 内部接口)

### 2. 题目服务 (Problem Service)
- 题目 CRUD 操作
- 测试用例管理
- 代码提交
- 提交记录查询
- 判题结果更新(Feign 内部接口)
- 排行榜(总榜/日榜/周榜/月榜)

### 3. 判题服务 (Judge Service)
- Docker 沙箱代码执行
- 判题策略模式(普通判题/竞赛判题)
- 支持多语言(Java/C++/Python)
- RabbitMQ 消息消费
- 异步判题回调

### 4. 竞赛服务 (Contest Service)
- 竞赛 CRUD 操作
- 竞赛报名管理
- 竞赛题目关联
- 竞赛权限验证
- 竞赛排行榜
- 竞赛状态管理

### 5. 系统服务 (System Service)
- 管理员登录
- 后台管理功能

### 6. 定时任务 (Job Service)
- 定时任务调度

## 快速开始

### 环境要求

- **JDK**: 17+
- **Maven**: 3.x
- **MySQL**: 8.0+
- **Redis**: 6.0+
- **RabbitMQ**: 3.x
- **Nacos**: 2.x
- **Docker**: 20.x+

### 基础设施配置

#### 1. Nacos
```bash
# 启动 Nacos
# 地址: 请在配置文件中设置您的 Nacos 服务器地址
# Namespace: 请在配置文件中设置您的命名空间 ID
```

#### 2. MySQL
```bash
# 创建数据库
mysql -u root -p -e "CREATE DATABASE oj_system CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 导入初始化脚本
mysql -u root -p oj_system < deploy/oj_system.sql
```

#### 3. Redis
```bash
# 启动 Redis
redis-server

# 默认配置
# Host: localhost
# Port: 6379
# Database: 0
```

#### 4. RabbitMQ
```bash
# 启动 RabbitMQ
rabbitmq-server

# 默认配置
# Host: localhost
# Port: 5672
# Username: guest
# Password: guest
```

#### 5. Docker 沙箱
```bash
# 构建沙箱镜像
docker build -t liren-oj-sandbox:v1 .

# 或拉取已有镜像
docker pull liren-oj-sandbox:v1
```

### 构建项目

```bash
# 进入项目根目录
cd oj-system-server

# 清理并打包
mvn clean package

# 跳过测试打包
mvn clean package -DskipTests
```

### 启动服务

#### 方式一: 使用 Maven 插件启动
```bash
# 启动网关
cd gateway
mvn spring-boot:run

# 启动用户服务
cd modules/user
mvn spring-boot:run

# 启动其他服务...
```

#### 方式二: 使用 JAR 包启动
```bash
# 启动网关
java -jar gateway/target/gateway-1.0-SNAPSHOT.jar

# 启动用户服务
java -jar modules/user/target/user-service-1.0-SNAPSHOT.jar

# 启动其他服务...
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
创建提交记录(状态: PENDING)
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
JudgeManager 判题(策略模式)
    ↓
回调 Problem Service 更新结果
    ↓
更新提交状态(AC/WA/TLE/MLE/CE等)
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
生成 JWT Token(密钥: Constants.JWT_SECRET)
    ↓
返回 Token 和用户信息
    ↓
后续请求携带 Token
    ↓
Gateway 验证 Token
    ↓
提取 userId 并放入请求头(X-User-Id)
    ↓
UserInterceptor 拦截请求
    ↓
从请求头读取 userId 到 UserContext(ThreadLocal)
    ↓
业务代码从 UserContext 获取当前用户
```

### 3. 排行榜系统

#### 排行榜维度
- **总榜**(Total): 所有历史 AC 记录
- **日榜**(Daily): 当天 AC 记录(过期时间: 3天)
- **周榜**(Weekly): 本周 AC 记录(过期时间: 7天)
- **月榜**(Monthly): 本月 AC 记录(过期时间: 30天)
- **竞赛榜**(Contest): 竞赛期间的 AC 记录

#### Redis Key 设计
```
oj:rank:total                    # 总榜 ZSet
oj:rank:daily:yyyyMMdd           # 日榜 ZSet
oj:rank:weekly:yyyyww            # 周榜 ZSet
oj:rank:monthly:yyyyMM           # 月榜 ZSet
oj:contest:score:{contestId}     # 竞赛排名 ZSet
oj:user:solved:{userId}          # 用户已解决的题目 Set(去重用)
```

### 4. 竞赛提交流程

```
用户报名竞赛
    ↓
Contest Service 验证权限
    ↓
创建报名记录(tb_contest_registration)
    ↓
用户查看竞赛题目
    ↓
验证: 是否已报名 + 竞赛是否进行中
    ↓
用户提交代码
    ↓
额外校验: 是否在竞赛时间内
    ↓
判题后更新竞赛排行榜(oj:contest:score:{contestId})
    ↓
实时排名查询
```

## API 接口文档

### 用户服务 (User Service: 8004)

| 方法 | 路径 | 功能 |
|-----|------|-----|
| POST | `/user/login` | 用户登录 |
| GET | `/user/info` | 获取用户信息 |
| POST | `/user/register` | 用户注册 |

### 题目服务 (Problem Service: 8006)

| 方法 | 路径 | 功能 |
|-----|------|-----|
| POST | `/problem/add` | 新增题目 |
| POST | `/problem/list/page` | 分页获取题目列表 |
| GET | `/problem/detail/{problemId}` | 获取题目详情 |
| POST | `/problem/submit` | 提交代码 |
| GET | `/problem/submit/result/{submitId}` | 查询提交记录详情 |
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
| POST | `/contest/register/{contestId}` | 报名比赛 |
| GET | `/contest/rank/{contestId}` | 获取比赛排名 |

## 配置说明

### 重要提示

⚠️ **敏感配置管理**: 本项目中的以下配置需要根据您的实际环境进行修改，请勿在生产环境使用默认值：

- **Nacos 地址和命名空间**: 请在 `bootstrap.yml` 中配置您自己的 Nacos 服务器
- **数据库连接**: 请修改为您的数据库地址、用户名和密码
- **Redis 连接**: 请修改为您的 Redis 服务器地址
- **RabbitMQ 连接**: 请修改为您的 RabbitMQ 服务器地址和认证信息
- **JWT 密钥**: 请在生产环境使用强密钥，不要使用默认值
- **Docker 沙箱镜像**: 请确保使用您自己构建或信任的沙箱镜像

### Nacos 配置

所有服务使用 `bootstrap.yml` 配置:

```yaml
spring:
  cloud:
    nacos:
      server-addr: ${NACOS_ADDR:localhost:8848}  # 请根据实际情况配置
      config:
        namespace: ${NACOS_NAMESPACE:}            # 请根据实际情况配置
        group: DEFAULT_GROUP
        file-extension: yaml
```

### Docker 沙箱配置

```java
// 定义在 Constants 类中
SANDBOX_TIME_OUT = 10000       // 超时时间: 10秒
SANDBOX_MEMORY_LIMIT = 100000  // 内存限制: 100MB
SANDBOX_CPU_COUNT = 1          // CPU 核心数: 1核

// 沙箱镜像
liren-oj-sandbox:v1
```

## 核心枚举类型

### 判题结果 (JudgeResultEnum)
- `ACCEPTED`(1, "通过 (AC)")
- `WRONG_ANSWER`(2, "答案错误 (WA)")
- `TIME_LIMIT_EXCEEDED`(3, "运行超时 (TLE)")
- `MEMORY_LIMIT_EXCEEDED`(4, "内存超限 (MLE)")
- `RUNTIME_ERROR`(5, "运行错误 (RE)")
- `COMPILE_ERROR`(6, "编译错误 (CE)")
- `SYSTEM_ERROR`(7, "系统错误 (SE)")

### 提交状态 (SubmitStatusEnum)
- `WAITING`(10, "等待判题")
- `JUDGING`(20, "判题中")
- `SUCCEED`(30, "判题完成")
- `FAILED`(40, "判题失败")

### 编程语言 (LanguageEnum)
- `JAVA`
- `CPP`
- `PYTHON`

## 数据库设计

### 核心数据表

- `tb_user` - 用户表
- `tb_problem` - 题目表
- `tb_test_case` - 测试用例表
- `tb_submit_record` - 提交记录表
- `tb_contest` - 竞赛表
- `tb_contest_problem` - 竞赛题目关联表
- `tb_contest_registration` - 竞赛报名表
- `tb_problem_tag` - 题目标签表
- `tb_solution` - 题解表
- `tb_sys_user` - 系统管理员表

### 初始化数据

**默认管理员账号**:
- 账号: `admin`
- 密码: `123456`

## 开发指南

### 添加新的 REST API

```java
// 1. 创建 DTO(请求参数)
@Data
public class YourDTO {
    private Long id;
    private String name;
}

// 2. 创建 VO(返回对象)
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

### 添加新的 Feign 客户端

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

### 异常处理

```java
// 抛出业务异常
if (user == null) {
    throw new BizException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
}

// 全局异常处理器会自动捕获并返回统一格式
```

## 注意事项

1. **微服务通信**: 所有跨服务调用通过 Feign Client 实现，内部接口统一使用 `/inner` 路径
2. **JWT 认证**: 所有业务接口需要通过 Gateway 认证，内部接口通过 Feign 调用无需认证
3. **雪花算法 ID**: 所有 ID 使用雪花算法生成，前端使用 String 类型接收
4. **异步处理**: 判题使用 RabbitMQ 异步处理，避免阻塞
5. **排行榜去重**: 每道题目只有首次 AC 才会计入排行榜
6. **事务管理**: 涉及多表操作时注意事务管理

## 性能优化

1. **Redis 缓存**: 排行榜使用 ZSet，O(log N) 复杂度
2. **数据库索引**: 建立合适的索引(user_id、problem_id、create_time)
3. **批量查询**: 使用批量查询减少数据库交互
4. **异步处理**: 判题使用 RabbitMQ 异步处理
5. **连接池配置**: 合理配置数据库、Redis、RabbitMQ 连接池

## 常见问题

### 1. 服务启动失败
- 检查 Nacos 是否正常启动
- 检查配置文件是否正确
- 查看日志排查具体错误

### 2. 判题失败
- 检查 Docker 是否正常运行
- 检查沙箱镜像是否存在
- 检查 RabbitMQ 是否正常

### 3. 排行榜不更新
- 检查 Redis 是否正常
- 检查去重 Set 是否正常工作

## 许可证

MIT License
