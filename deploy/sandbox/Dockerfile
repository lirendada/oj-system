# OJ 系统代码执行沙箱 Dockerfile
# 支持语言: Java, C++, Python3

# 1. 使用 Alpine Linux 作为基础镜像（体积小、安全性高）
FROM openjdk:8-alpine

# 2. 修改 Alpine 镜像源为阿里云（加速国内构建）
RUN sed -i 's/dl-cdn.alpinelinux.org/mirrors.aliyun.com/g' /etc/apk/repositories

# 3. 安装 C/C++ 编译器和 Python3
# gcc: C 编译器
# g++: C++ 编译器
# libc-dev: C 标准库
# python3: Python 3 运行环境
# bash: Bash shell（用于执行脚本）
RUN apk update && apk add --no-cache \
    gcc \
    g++ \
    libc-dev \
    python3 \
    bash \
    curl \
    && rm -rf /var/cache/apk/*

# 4. 创建工作目录
WORKDIR /app

# 5. 验证安装（构建时打印版本信息）
RUN echo "=== Environment Versions ===" && \
    echo "Java: $(java -version 2>&1 | head -n 1)" && \
    echo "GCC: $(gcc --version | head -n 1)" && \
    echo "G++: $(g++ --version | head -n 1)" && \
    echo "Python: $(python3 --version)" && \
    echo "Bash: $(bash --version | head -n 1)" && \
    echo "=============================="

# 6. 设置默认命令
CMD ["/bin/bash"]
