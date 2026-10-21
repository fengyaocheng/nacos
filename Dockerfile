#
# Copyright 1999-2023 Alibaba Group Holding Ltd.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

FROM eclipse-temurin:8-jre

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
