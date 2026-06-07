#!/bin/bash
# LTL League 本地测试前端启动脚本。
# 前端运行在本机，API 默认请求 127.0.0.1:8080/api。

set -euo pipefail

FRONTEND_HOST="${FRONTEND_HOST:-127.0.0.1}"
FRONTEND_PORT="${FRONTEND_PORT:-4173}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
LOCAL_DIR="${LOCAL_DIR:-$REPO_ROOT}"
RUNTIME_DIR="${REPO_ROOT}/.local-test"
LOG_DIR="${RUNTIME_DIR}/logs"
PID_FILE="${RUNTIME_DIR}/frontend.pid"

echo "========================================="
echo "  LTL League 本地测试前端"
echo "========================================="
echo "访问地址: http://${FRONTEND_HOST}:${FRONTEND_PORT}/"
echo "API 地址: http://127.0.0.1:8080/api"
echo ""

echo "[1/3] 检查本地文件..."
REQUIRED_FILES=("index.html" "src/" "src/styles/" "src/features/" "src/services/" "tools/static-server.mjs")
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -e "$LOCAL_DIR/$file" ]; then
        echo "错误: 缺少文件: $file"
        exit 1
    fi
done
echo "本地文件检查通过"

mkdir -p "$RUNTIME_DIR" "$LOG_DIR"

if [ -f "$PID_FILE" ]; then
    OLD_PID="$(cat "$PID_FILE")"
    if [ -n "$OLD_PID" ] && kill -0 "$OLD_PID" >/dev/null 2>&1; then
        echo "停止旧的本地测试前端进程: ${OLD_PID}"
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

echo ""
echo "[2/3] 启动本地静态服务..."
cd "$LOCAL_DIR"
HOST="$FRONTEND_HOST" PORT="$FRONTEND_PORT" nohup node tools/static-server.mjs \
    > "${LOG_DIR}/frontend.log" 2>&1 &

FRONTEND_PID="$!"
echo "$FRONTEND_PID" > "$PID_FILE"
echo "进程 PID: ${FRONTEND_PID}"
echo "日志: ${LOG_DIR}/frontend.log"

echo ""
echo "[3/3] 验证本地测试前端..."
HTTP_CODE="000"
for _ in $(seq 1 20); do
    HTTP_CODE="$(curl -s -o /dev/null -w '%{http_code}' "http://${FRONTEND_HOST}:${FRONTEND_PORT}/" || echo "000")"
    if [ "$HTTP_CODE" = "200" ]; then
        break
    fi
    sleep 0.5
done

if [ "$HTTP_CODE" != "200" ]; then
    echo "本地测试前端启动失败或暂未就绪 (HTTP ${HTTP_CODE})"
    echo "查看日志: tail -80 ${LOG_DIR}/frontend.log"
    if kill -0 "$FRONTEND_PID" >/dev/null 2>&1; then
        kill "$FRONTEND_PID"
    fi
    rm -f "$PID_FILE"
    exit 1
fi

echo ""
echo "========================================="
echo "  本地测试前端已启动"
echo "========================================="
echo "访问地址: http://${FRONTEND_HOST}:${FRONTEND_PORT}/"
echo "停止前端: kill \$(cat ${PID_FILE})"
