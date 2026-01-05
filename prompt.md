根据对整个项目的探索分析，这是一个基于Spring Cloud Alibaba的微服务OJ（在线判题）系统。以下是架构和模块职责概括：

##   📋 整体架构

  微服务架构 - 采用Spring Cloud Alibaba技术栈：
  - 服务注册/配置中心：Nacos
  - API网关：Spring Cloud Gateway (10020端口)
  - 服务间通信：OpenFeign
  - 消息队列：RabbitMQ (异步判题)
  - 代码沙箱：Docker容器隔离执行

##   🏗️ 模块职责

  基础设施层

  - Gateway: API网关，JWT鉴权、路由转发
  - Common: 公共组件（core/web/swagger/redis）
  - API: Feign接口定义和跨服务DTO

  业务服务层

  1. User服务 (8004) - 用户管理、登录注册
  2. Problem服务 (8006) - 题目管理、提交处理、排行榜（日/周/月/总榜）
  3. Judge服务 (8002) - 代码判题、Docker沙箱执行（支持Java/C++/Python）
  4. Contest服务 (8005) - 竞赛管理、报名、权限验证
  5. System服务 (8003) - 后台管理
  6. Job服务 (8001) - 定时任务

##   🔄 核心流程

  异步判题流程:
  用户提交代码 → Problem服务 → RabbitMQ → Judge服务(Docker沙箱) → 结果回写 → 更新排行榜

  服务调用链:
  - Problem → User (获取用户信息)
  - Problem → Contest (竞赛权限验证)
  - Judge → Problem (获取测试用例、回写结果)

##   💡 技术亮点

  1. Docker沙箱多语言支持（资源限制、安全隔离）
  2. Redis ZSet实现高性能排行榜系统
  3. 策略模式实现灵活的判题策略
  4. 统一的异常处理和结果封装
  5. 完善的微服务治理（网关鉴权、服务发现、配置管理）

  项目结构清晰，职责分明，是一个设计合理、可扩展性强的现代化OJ系统。