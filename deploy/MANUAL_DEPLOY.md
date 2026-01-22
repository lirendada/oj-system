# OJ System Ubuntu 服务器手动部署指南

## 环境信息
- 服务器: Ubuntu 24.04 LTS
- 配置: 2核2G
- 统一账号密码: root / 123123

---

## 步骤 1: 创建 Docker 网络

```bash
docker network create oj-network
```

---

## 步骤 2: 创建 MySQL 容器

```bash
docker run -d \
  --name oj-mysql \
  --network oj-network \
  -p 3306:3306 \
  -e MYSQL_ROOT_PASSWORD=123123 \
  -e MYSQL_DATABASE=oj_system \
  -e TZ=Asia/Shanghai \
  -v mysql-data:/var/lib/mysql \
  mysql:8.0 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci \
  --default-authentication-plugin=mysql_native_password \
  --max_connections=500
```

等待 MySQL 启动（约 30 秒）

```bash
# 查看 MySQL 是否就绪
docker logs -f oj-mysql
```

看到 "ready for connections" 后按 Ctrl+C 退出

---

## 步骤 3: 初始化数据库

```bash
# 上传 oj_system.sql 到服务器后执行
docker exec -i oj-mysql mysql -uroot -p123123 oj_system < oj_system.sql

# 创建 Nacos 配置数据库
docker exec -i oj-mysql mysql -uroot -p123123 <<EOF
CREATE DATABASE IF NOT EXISTS nacos_config CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EOF
```

---

## 步骤 4: 创建 Redis 容器

```bash
docker run -d \
  --name oj-redis \
  --network oj-network \
  -p 6379:6379 \
  -v redis-data:/data \
  redis:7-alpine \
  redis-server \
  --appendonly yes \
  --requirepass 123123 \
  --maxmemory 256mb \
  --maxmemory-policy allkeys-lru
```

验证 Redis：

```bash
docker exec oj-redis redis-cli -a 123123 ping
# 应该返回 PONG
```

---

## 步骤 5: 创建 RabbitMQ 容器

```bash
docker run -d \
  --name oj-rabbitmq \
  --network oj-network \
  -p 5672:5672 \
  -p 15672:15672 \
  -e RABBITMQ_DEFAULT_USER=root \
  -e RABBITMQ_DEFAULT_PASS=123123 \
  -v rabbitmq-data:/var/lib/rabbitmq \
  rabbitmq:3.12-management-alpine
```

等待 RabbitMQ 启动（约 20 秒）

验证 RabbitMQ：

```bash
docker logs -f oj-rabbitmq
```

看到 "Server startup complete" 后按 Ctrl+C 退出

---

## 步骤 6: 创建 Nacos 容器

```bash
docker run -d \
  --name oj-nacos \
  --network oj-network \
  -p 8848:8848 \
  -p 9848:9848 \
  -e MODE=standalone \
  -e SPRING_DATASOURCE_PLATFORM=mysql \
  -e MYSQL_SERVICE_HOST=mysql \
  -e MYSQL_SERVICE_PORT=3306 \
  -e MYSQL_SERVICE_DB_NAME=nacos_config \
  -e MYSQL_SERVICE_USER=root \
  -e MYSQL_SERVICE_PASSWORD=123123 \
  -e MYSQL_SERVICE_DB_PARAM="characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true" \
  -e JVM_XMS=256m \
  -e JVM_XMX=512m \
  -e NACOS_AUTH_ENABLE=true \
  -e NACOS_AUTH_TOKEN=SecretKey012345678901234567890123456789012345678901234567890123456789 \
  -e NACOS_AUTH_IDENTITY_KEY=root \
  -e NACOS_AUTH_IDENTITY_VALUE=123123 \
  -v nacos-logs:/home/nacos/logs \
  nacos/nacos-server:v2.2.3
```

**注意**：上面的 `-e MYSQL_SERVICE_HOST=mysql` 需要改为 `oj-mysql`，因为我们的容器名是 `oj-mysql`：

**修正后的 Nacos 命令**：

```bash
docker run -d \
  --name oj-nacos \
  --network oj-network \
  -p 8848:8848 \
  -p 9848:9848 \
  -e MODE=standalone \
  -e SPRING_DATASOURCE_PLATFORM=mysql \
  -e MYSQL_SERVICE_HOST=oj-mysql \
  -e MYSQL_SERVICE_PORT=3306 \
  -e MYSQL_SERVICE_DB_NAME=nacos_config \
  -e MYSQL_SERVICE_USER=root \
  -e MYSQL_SERVICE_PASSWORD=123123 \
  -e MYSQL_SERVICE_DB_PARAM="characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useSSL=false&allowPublicKeyRetrieval=true" \
  -e JVM_XMS=256m \
  -e JVM_XMX=512m \
  -e NACOS_AUTH_ENABLE=true \
  -e NACOS_AUTH_TOKEN=SecretKey012345678901234567890123456789012345678901234567890123456789 \
  -e NACOS_AUTH_IDENTITY_KEY=root \
  -e NACOS_AUTH_IDENTITY_VALUE=123123 \
  -v nacos-logs:/home/nacos/logs \
  nacos/nacos-server:v2.2.3
```

等待 Nacos 启动（约 60 秒）

验证 Nacos：

```bash
docker logs -f oj-nacos
```

看到 "Nacos started successfully" 后按 Ctrl+C 退出

---

## 步骤 7: 构建沙箱镜像

```bash
cd ~/deploy/sandbox
docker build -t liren-oj-sandbox:v1 .
```

---

## 步骤 8: 验证所有容器

```bash
docker ps
```

应该看到以下容器都在运行：
- oj-mysql
- oj-redis
- oj-rabbitmq
- oj-nacos

---

## 服务访问地址

假设服务器 IP 为 `YOUR_SERVER_IP`

| 服务 | 地址 | 账号 | 密码 |
|------|------|------|------|
| MySQL | YOUR_SERVER_IP:3306 | root | 123123 |
| Redis | YOUR_SERVER_IP:6379 | - | 123123 |
| RabbitMQ | YOUR_SERVER_IP:5672 | root | 123123 |
| RabbitMQ 管理 | http://YOUR_SERVER_IP:15672 | root | 123123 |
| Nacos | http://YOUR_SERVER_IP:8848/nacos | root | 123123 |

---

## 常用管理命令

```bash
# 查看所有容器
docker ps

# 查看容器日志
docker logs -f oj-mysql
docker logs -f oj-redis
docker logs -f oj-rabbitmq
docker logs -f oj-nacos

# 停止容器
docker stop oj-mysql oj-redis oj-rabbitmq oj-nacos

# 启动容器
docker start oj-mysql oj-redis oj-rabbitmq oj-nacos

# 重启容器
docker restart oj-mysql oj-redis oj-rabbitmq oj-nacos

# 删除容器
docker rm -f oj-mysql oj-redis oj-rabbitmq oj-nacos

# 查看数据卷
docker volume ls

# 删除数据卷（⚠️ 会删除数据）
docker volume rm mysql-data redis-data rabbitmq-data nacos-logs
```

---

## 本地 IDEA 配置

### 1. 修改所有服务的 bootstrap.yml

将所有服务的 Nacos 地址改为服务器 IP：

```yaml
spring:
  cloud:
    nacos:
      server-addr: YOUR_SERVER_IP:8848
```

### 2. 在 Nacos 中创建配置

访问: http://YOUR_SERVER_IP:8848/nacos
登录: root / 123123

创建命名空间，然后导入 `common.yaml`：

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://YOUR_SERVER_IP:3306/oj_system?useUnicode=true&characterEncoding=utf-8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root
    password: 123123
  data:
    redis:
      host: YOUR_SERVER_IP
      port: 6379
      password: 123123
  rabbitmq:
    host: YOUR_SERVER_IP
    port: 5672
    username: root
    password: 123123
```

### 3. 在 IDEA 中启动服务

依次启动：
1. Gateway
2. User Service
3. Problem Service
4. Judge Service
5. Contest Service
6. System Service
7. Job Service

---

## 防火墙配置

```bash
# 开放端口
sudo ufw allow 3306/tcp  # MySQL
sudo ufw allow 6379/tcp  # Redis
sudo ufw allow 5672/tcp  # RabbitMQ
sudo ufw allow 15672/tcp # RabbitMQ 管理
sudo ufw allow 8848/tcp  # Nacos

# 查看状态
sudo ufw status
```
