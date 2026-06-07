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

# 2. 上传 JAR 到服务器
echo ""
echo "[2/4] 上传 JAR 到服务器..."
echo "目标: $SERVER_HOST:$DEPLOY_PATH/"
scp -i "$SSH_KEY" -o StrictHostKeyChecking=no "$LOCAL_JAR" ${SERVER_USER}@${SERVER_HOST}:${DEPLOY_PATH}/

# 3. 创建生产环境配置
echo ""
echo "[3/4] 配置生产环境..."
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_HOST} << 'EOF'
# 创建生产环境配置
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
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
      logic-delete-field: deleted
      logic-delete-value: 1
      logic-not-delete-value: 0

logging:
  level:
    com.ltl.league: INFO
    com.ltl.league.mapper: DEBUG
YAML

# 设置权限
chown ltl:ltl /opt/ltl-league/backend/application-prod.yml
chown ltl:ltl /opt/ltl-league/backend/league-backend-1.0.0.jar
chmod 644 /opt/ltl-league/backend/application-prod.yml

# 赛果截图目录：后端 ltl 用户写入，Nginx 只读
mkdir -p /var/www/ltl-league/uploads
chown -R ltl:ltl /var/www/ltl-league/uploads
chmod 755 /var/www/ltl-league/uploads

echo "生产环境配置已创建"
EOF

# 4. 重启服务
echo ""
echo "[4/4] 重启服务..."
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_HOST} << 'EOF'
systemctl daemon-reload
systemctl restart ltl-league-backend
sleep 3
systemctl status ltl-league-backend --no-pager
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
