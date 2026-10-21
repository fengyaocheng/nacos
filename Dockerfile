FROM openjdk:8-jre-slim

LABEL maintainer="nacos-2fa"

# 设置时区
ENV TZ=Asia/Shanghai
RUN ln -snf /usr/share/zoneinfo/$TZ /etc/localtime && echo $TZ > /etc/timezone

WORKDIR /home/nacos

# 拷贝构建好的 Nacos Server 与启动入口
COPY distribution/target/nacos-server-2.4.3/nacos /home/nacos
COPY distribution/bin/docker-startup.sh /home/nacos/bin/docker-startup.sh

RUN chmod +x /home/nacos/bin/docker-startup.sh

# 暴露端口：8848 (HTTP 控制台/OpenAPI), 9848 (客户端 gRPC), 9849 (服务端 gRPC)
EXPOSE 8848 9848 9849

ENTRYPOINT ["/bin/bash", "/home/nacos/bin/docker-startup.sh"]
