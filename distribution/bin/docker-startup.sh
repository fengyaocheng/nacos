#!/usr/bin/env bash

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

set -x

BASE_DIR="/home/nacos"
CONF_FILE="${BASE_DIR}/conf/application.properties"

MODE="${MODE:-standalone}"
JVM_XMS="${JVM_XMS:-512m}"
JVM_XMX="${JVM_XMX:-512m}"
JVM_XMN="${JVM_XMN:-256m}"

# 配置 MySQL 数据源
if [ "${SPRING_DATASOURCE_PLATFORM}" = "mysql" ]; then
    MYSQL_HOST="${MYSQL_SERVICE_HOST:-localhost}"
    MYSQL_PORT="${MYSQL_SERVICE_PORT:-3306}"
    MYSQL_DB="${MYSQL_SERVICE_DB_NAME:-nacos}"
    MYSQL_USER="${MYSQL_SERVICE_USER:-nacos}"
    MYSQL_PWD="${MYSQL_SERVICE_PASSWORD:-nacos}"
    MYSQL_PARAM="${MYSQL_SERVICE_DB_PARAM:-characterEncoding=utf8&connectTimeout=1000&socketTimeout=3000&autoReconnect=true&useUnicode=true&useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true}"
    
    echo "Configuring MySQL Data Source..."
    sed -i '/^spring.datasource.platform=/d' "${CONF_FILE}"
    sed -i '/^db.num=/d' "${CONF_FILE}"
    sed -i '/^db.url.0=/d' "${CONF_FILE}"
    sed -i '/^db.user.0=/d' "${CONF_FILE}"
    sed -i '/^db.password.0=/d' "${CONF_FILE}"
    
    cat >> "${CONF_FILE}" <<EOF
spring.datasource.platform=mysql
db.num=1
db.url.0=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT}/${MYSQL_DB}?${MYSQL_PARAM}
db.user.0=${MYSQL_USER}
db.password.0=${MYSQL_PWD}
EOF
fi

# 配置 Auth 与 2FA 核心密钥
AUTH_ENABLED="${NACOS_AUTH_ENABLE:-true}"
AUTH_TOKEN="${NACOS_AUTH_TOKEN:-SecretKey012345678901234567890123456789012345678901234567890123456789}"
AUTH_ID_KEY="${NACOS_AUTH_IDENTITY_KEY:-serverIdentity}"
AUTH_ID_VAL="${NACOS_AUTH_IDENTITY_VALUE:-security}"

sed -i '/^nacos.core.auth.enabled=/d' "${CONF_FILE}"
sed -i '/^nacos.core.auth.plugin.nacos.token.secret.key=/d' "${CONF_FILE}"
sed -i '/^nacos.core.auth.server.identity.key=/d' "${CONF_FILE}"
sed -i '/^nacos.core.auth.server.identity.value=/d' "${CONF_FILE}"

cat >> "${CONF_FILE}" <<EOF
nacos.core.auth.enabled=${AUTH_ENABLED}
nacos.core.auth.plugin.nacos.token.secret.key=${AUTH_TOKEN}
nacos.core.auth.server.identity.key=${AUTH_ID_KEY}
nacos.core.auth.server.identity.value=${AUTH_ID_VAL}
EOF

# JVM 与启动参数配置（前台执行）
JAVA_OPT="-Xms${JVM_XMS} -Xmx${JVM_XMX} -Xmn${JVM_XMN} -Djava.awt.headless=true"
if [ "${MODE}" = "standalone" ]; then
    JAVA_OPT="${JAVA_OPT} -Dnacos.standalone=true"
fi
JAVA_OPT="${JAVA_OPT} -Dnacos.home=${BASE_DIR}"
JAVA_OPT="${JAVA_OPT} -Dloader.path=${BASE_DIR}/plugins,${BASE_DIR}/plugins/health,${BASE_DIR}/plugins/cmdb,${BASE_DIR}/plugins/selector"

echo "Starting Nacos in mode: ${MODE}..."
exec java ${JAVA_OPT} \
  -jar "${BASE_DIR}/target/nacos-server.jar" \
  --spring.config.additional-location="file:${BASE_DIR}/conf/" \
  --logging.config="${BASE_DIR}/conf/nacos-logback.xml" \
  --server.max-http-header-size=524288
