#!/bin/bash
# LTL League 后端部署脚本

set -e

# 配置变量
SERVER_HOST="123.57.19.160"
SERVER_USER="root"
APP_USER="ltl"
DEPLOY_PATH="/opt/ltl-league/backend"
LOCAL_JAR="backend/target/league-backend-1.0.0.jar"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SSH_KEY="$SCRIPT_DIR/deploy-key.pem"

echo "========================================="
echo "  LTL League 后端部署脚本"
echo "========================================="
echo ""

# 检查 SSH 密钥文件
if [ ! -f "$SSH_KEY" ]; then
    echo "错误: SSH 密钥文件不存在: $SSH_KEY"
    exit 1
fi

# 1. 本地编译打包
echo "[1/4] 本地编译打包..."
cd "$SCRIPT_DIR/.."

# 编译打包（CI/自动化场景避免交互输入）
if [ ! -f "$LOCAL_JAR" ] || [ "${FORCE_REBUILD:-0}" = "1" ]; then
    echo "开始编译..."
    cd backend
    # 使用 Java 17 编译；macOS 优先通过 java_home 定位，其他环境使用已有 JAVA_HOME/PATH。
    if [ -x /usr/libexec/java_home ]; then
        export JAVA_HOME=$(/usr/libexec/java_home -v 17)
        export PATH=$JAVA_HOME/bin:$PATH
    fi
    mvn clean package -DskipTests
    cd ..
    echo "编译完成"
else
    echo "JAR 文件已存在: $LOCAL_JAR"
    echo "跳过编译（如需强制重新编译请运行: FORCE_REBUILD=1 bash scripts/deploy.sh）"
fi

SSH_OPTS=(-i "$SSH_KEY" -o StrictHostKeyChecking=no -o ConnectTimeout=60 -o IPQoS=none -o ServerAliveInterval=15)

# 2. 上传 JAR 到服务器
echo ""
echo "[2/4] 上传 JAR 到服务器..."
echo "目标: $SERVER_HOST:$DEPLOY_PATH/"
scp "${SSH_OPTS[@]}" "$LOCAL_JAR" ${SERVER_USER}@${SERVER_HOST}:${DEPLOY_PATH}/

# 3. 创建生产环境配置
echo ""
echo "[3/4] 配置生产环境..."
ssh "${SSH_OPTS[@]}" ${SERVER_USER}@${SERVER_HOST} << 'EOF'
# 生产配置：关闭 SQL StdOut/DEBUG，避免日志打爆磁盘与 CPU
cat > /opt/ltl-league/backend/application-prod.yml << 'YAML'
spring:
  datasource:
    url: jdbc:mysql://123.57.19.160:3306/ltl_league?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: ltl_user
    password: a5201314
    driver-class-name: com.mysql.cj.jdbc.Driver

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

logging:
  level:
    root: WARN
    com.ltl.league: INFO
    com.ltl.league.mapper: WARN
    com.baomidou.mybatisplus: WARN

ltl:
  upload:
    dir: /var/www/ltl-league/uploads
    url-prefix: http://123.57.19.160/uploads
  ai:
    deepseek:
      api-key: "sk-6494c3c0cf9c485d8238fb65b778a613"
      base-url: https://api.deepseek.com
      model: deepseek-v4-flash
      timeout-ms: 60000
YAML

# 限制 JVM 堆，避免与 MySQL 抢光 1.6G 内存
cat > /opt/ltl-league/backend/start.sh << 'START'
#!/bin/bash
cd /opt/ltl-league/backend
export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java))))
exec java -Xms128m -Xmx256m -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -jar league-backend-1.0.0.jar --spring.profiles.active=prod
START
chmod +x /opt/ltl-league/backend/start.sh

# 轮转过大的应用日志
mkdir -p /opt/ltl-league/logs
if [ -f /opt/ltl-league/logs/application.log ]; then
  ts=$(date +%Y%m%d-%H%M%S)
  mv /opt/ltl-league/logs/application.log "/opt/ltl-league/logs/application.log.$ts.bak" || true
  # 只保留最近 2 个备份，避免占盘
  ls -1t /opt/ltl-league/logs/application.log.*.bak 2>/dev/null | tail -n +3 | xargs -r rm -f
fi
: > /opt/ltl-league/logs/application.log
: > /opt/ltl-league/logs/error.log

# 设置权限
chown ltl:ltl /opt/ltl-league/backend/application-prod.yml
chown ltl:ltl /opt/ltl-league/backend/league-backend-1.0.0.jar
chown ltl:ltl /opt/ltl-league/backend/start.sh
chown -R ltl:ltl /opt/ltl-league/logs
chmod 644 /opt/ltl-league/backend/application-prod.yml

# 赛果截图目录：后端 ltl 用户写入，Nginx 只读
mkdir -p /var/www/ltl-league/uploads
chown -R ltl:ltl /var/www/ltl-league/uploads
chmod 755 /var/www/ltl-league/uploads

# 停用测试后端，减轻内存压力（需要时再手动 enable）
systemctl disable --now ltl-league-backend-test 2>/dev/null || true

echo "生产环境配置已创建（已关闭 SQL 调试日志，已限制 JVM，已停测试服务）"
EOF

# 4. 重启服务
echo ""
echo "[4/4] 重启服务..."
ssh "${SSH_OPTS[@]}" ${SERVER_USER}@${SERVER_HOST} << 'EOF'
systemctl daemon-reload
systemctl enable ltl-league-backend
systemctl restart ltl-league-backend
sleep 5
systemctl status ltl-league-backend --no-pager
ss -lntp | grep -E ':8080|:8081' || true
free -h
EOF

echo ""
echo "========================================="
echo "  部署完成！"
echo "========================================="
echo ""
echo "服务管理命令："
echo "  查看状态: ssh root@${SERVER_HOST} 'systemctl status ltl-league-backend'"
echo "  查看日志: ssh root@${SERVER_HOST} 'journalctl -u ltl-league-backend -f'"
echo "  重启服务: ssh root@${SERVER_HOST} 'systemctl restart ltl-league-backend'"
echo ""
echo "API 测试："
echo "  curl http://${SERVER_HOST}:8080/api/teams"
echo "  curl http://${SERVER_HOST}/api/teams"
