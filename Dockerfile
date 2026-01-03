# 1. 指定基础镜像（保留 Java 环境）
FROM openjdk:8-alpine

# 2. 修改 Alpine 镜像源为阿里云（加速国内构建，可选）
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 3. 安装 C/C++ 编译器和 Python3
# apk 是 Alpine 的包管理器
# gcc, g++: C/C++ 编译器
# python3: Python 运行环境
RUN apk update && apk add --no-cache \
    gcc \
    g++ \
    libc-dev \
    python3

# 4. 验证安装（构建时会打印版本信息，确保安装成功）
RUN echo "Checking versions..." && \
    java -version && \
    g++ --version && \
    python3 --version