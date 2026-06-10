#!/bin/bash
# LTL League 本地测试后端启动脚本。
# 后端运行在本机，数据库连接服务器上的 ltl_league_test。

set -euo pipefail

DB_HOST="${DB_HOST:-123.57.19.160}"
DB_PORT="${DB_PORT:-3306}"
DB_USERNAME="${DB_USERNAME:-ltl_user}"
DB_PASSWORD="${DB_PASSWORD:-a5201314}"
DB_NAME="${DB_NAME:-ltl_league_test}"
DEEPSEEK_API_KEY="${DEEPSEEK_API_KEY:-sk-6494c3c0cf9c485d8238fb65b778a613}"
BACKEND_HOST="${BACKEND_HOST:-127.0.0.1}"
BACKEND_PORT="${BACKEND_PORT:-8080}"
FRONTEND_PORT="${FRONTEND_PORT:-4173}"
JAR_NAME="league-backend-1.0.0.jar"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOCAL_JAR="${REPO_ROOT}/backend/target/${JAR_NAME}"
RUNTIME_DIR="${REPO_ROOT}/.local-test"
LOG_DIR="${RUNTIME_DIR}/logs"
PID_FILE="${RUNTIME_DIR}/backend.pid"
UPLOAD_DIR="${TEST_UPLOAD_DIR:-${REPO_ROOT}/uploads-test}"
UPLOAD_URL_PREFIX="${TEST_UPLOAD_URL_PREFIX:-http://127.0.0.1:${FRONTEND_PORT}/uploads-test}"

echo "========================================="
echo "  LTL League 本地测试后端"
echo "========================================="
echo "监听地址: http://${BACKEND_HOST}:${BACKEND_PORT}/api"
echo "测试数据库: ${DB_HOST}:${DB_PORT}/${DB_NAME}"
echo "上传目录: ${UPLOAD_DIR}"
echo ""

cd "$REPO_ROOT"
mkdir -p "$RUNTIME_DIR" "$LOG_DIR" "$UPLOAD_DIR"

if [ -f "$PID_FILE" ]; then
    OLD_PID="$(cat "$PID_FILE")"
    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" >/dev/null 2>&1; then
        echo "停止旧的本地测试后端进程: ${OLD_PID}"
        kill "$OLD_PID"
        for _ in $(seq 1 20); do
            if ! kill -0 "$OLD_PID" >/dev/null 2>&1; then
                break
            fi
            sleep 0.2
        done
    fi
    rm -f "$PID_FILE"
fi

SOURCE_CHANGED=0
if [ -f "$LOCAL_JAR" ]; then
    if find backend/pom.xml backend/src/main backend/src/test -type f -newer "$LOCAL_JAR" -print -quit | grep -q .; then
        SOURCE_CHANGED=1
    fi
fi

if [ ! -f "$LOCAL_JAR" ] || [ "${FORCE_REBUILD:-0}" = "1" ] || [ "$SOURCE_CHANGED" = "1" ]; then
    echo "[1/3] 本地编译打包..."
    cd backend
    if [ -z "${JAVA_HOME:-}" ] && command -v /usr/libexec/java_home >/dev/null 2>&1; then
        export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
        export PATH="$JAVA_HOME/bin:$PATH"
    fi
    mvn clean package -DskipTests
    cd "$REPO_ROOT"
    echo "编译完成"
else
    echo "[1/3] JAR 文件已存在，跳过编译: ${LOCAL_JAR}"
    echo "如需强制重新编译: FORCE_REBUILD=1 bash scripts/deploy-test.sh"
fi

JAVA_BIN="${JAVA_HOME:+${JAVA_HOME}/bin/}java"
if ! command -v "$JAVA_BIN" >/dev/null 2>&1; then
    JAVA_BIN="java"
fi

echo ""
echo "[2/3] 启动本地测试后端..."
SPRING_DATASOURCE_URL="jdbc:mysql://${DB_HOST}:${DB_PORT}/${DB_NAME}?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&connectTimeout=5000&socketTimeout=10000&tcpKeepAlive=true"

nohup "$JAVA_BIN" -jar "$LOCAL_JAR" \
    --spring.profiles.active=test \
    --server.address="$BACKEND_HOST" \
    --server.port="$BACKEND_PORT" \
    --spring.datasource.url="$SPRING_DATASOURCE_URL" \
    --spring.datasource.username="$DB_USERNAME" \
    --spring.datasource.password="$DB_PASSWORD" \
    --spring.datasource.hikari.max-lifetime=900000 \
    --spring.datasource.hikari.keepalive-time=300000 \
    --spring.datasource.hikari.validation-timeout=5000 \
    --ltl.upload.dir="$UPLOAD_DIR" \
    --ltl.upload.url-prefix="$UPLOAD_URL_PREFIX" \
    --ltl.ai.deepseek.api-key="$DEEPSEEK_API_KEY" \
    --ltl.ai.deepseek.timeout-ms=60000 \
    > "${LOG_DIR}/backend.log" 2>&1 &

BACKEND_PID="$!"
echo "$BACKEND_PID" > "$PID_FILE"
echo "进程 PID: ${BACKEND_PID}"
echo "日志: ${LOG_DIR}/backend.log"

echo ""
echo "[3/3] 验证本地测试后端..."
HTTP_CODE="000"
for _ in $(seq 1 30); do
    HTTP_CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://${BACKEND_HOST}:${BACKEND_PORT}/api/teams" || echo "000")"
    if [ "$HTTP_CODE" = "200" ]; then
        break
    fi
    sleep 1
done

if [ "$HTTP_CODE" != "200" ]; then
    echo "本地测试后端启动失败或暂未就绪 (HTTP ${HTTP_CODE})"
    echo "查看日志: tail -80 ${LOG_DIR}/backend.log"
    if kill -0 "$BACKEND_PID" >/dev/null 2>&1; then
        kill "$BACKEND_PID"
    fi
    rm -f "$PID_FILE"
    exit 1
fi

echo ""
echo "========================================="
echo "  本地测试后端已启动"
echo "========================================="
echo "API 测试: curl http://${BACKEND_HOST}:${BACKEND_PORT}/api/teams"
echo "停止后端: kill \$(cat ${PID_FILE})"
