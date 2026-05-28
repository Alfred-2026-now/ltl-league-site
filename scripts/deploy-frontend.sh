#!/bin/bash
# LTL League 前端部署脚本
# 部署静态文件到远程服务器

set -e

# 配置变量
SERVER_HOST="123.57.19.160"
SERVER_USER="root"
DEPLOY_PATH="/var/www/ltl-league"
LOCAL_DIR="/Users/a58/xinghe/ltl-league-site"
SCRIPT_DIR="$(dirname "$0")"
SSH_KEY="$SCRIPT_DIR/deploy-key.pem"

echo "========================================="
echo "  LTL League 前端部署脚本"
echo "========================================="
echo ""

# 检查 SSH 密钥文件
if [ ! -f "$SSH_KEY" ]; then
    echo "错误: SSH 密钥文件不存在: $SSH_KEY"
    exit 1
fi

# 1. 检查本地文件
echo "[1/4] 检查本地文件..."
if [ ! -d "$LOCAL_DIR" ]; then
    echo "错误: 目录不存在: $LOCAL_DIR"
    exit 1
fi

REQUIRED_FILES=("index.html" "src/" "src/styles/" "src/features/" "src/services/")
for file in "${REQUIRED_FILES[@]}"; do
    if [ ! -e "$LOCAL_DIR/$file" ]; then
        echo "错误: 缺少文件: $file"
        exit 1
    fi
done
echo "本地文件检查通过"

# 2. 上传文件到服务器
echo ""
echo "[2/4] 上传文件到服务器..."
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_HOST} << 'EOF'
# 创建目录结构
mkdir -p /var/www/ltl-league/{src,src/styles,src/features,src/services,src/data,assets}
chown -R www-data:www-data /var/www/ltl-league
EOF

# 上传 HTML 文件
for html in $(find $LOCAL_DIR -maxdepth 1 -name "*.html"); do
    echo "上传: $(basename $html)"
    scp -i "$SSH_KEY" -o StrictHostKeyChecking=no "$html" ${SERVER_USER}@${SERVER_HOST}:${DEPLOY_PATH}/
done

# 上传 src 目录
echo "上传: src/*"
scp -i "$SSH_KEY" -r -o StrictHostKeyChecking=no $LOCAL_DIR/src/* ${SERVER_USER}@${SERVER_HOST}:${DEPLOY_PATH}/src/

# 上传 assets 目录（队标等静态资源）
if [ -d "$LOCAL_DIR/assets" ]; then
    echo "上传: assets/*"
    ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_HOST} "mkdir -p ${DEPLOY_PATH}/assets"
    scp -i "$SSH_KEY" -r -o StrictHostKeyChecking=no $LOCAL_DIR/assets/* ${SERVER_USER}@${SERVER_HOST}:${DEPLOY_PATH}/assets/ || true
fi

echo "文件上传完成"

# 3. 配置 Nginx
echo ""
echo "[3/4] 配置 Nginx..."
ssh -i "$SSH_KEY" -o StrictHostKeyChecking=no ${SERVER_USER}@${SERVER_HOST} << 'EOF'
# 创建 Nginx 配置
cat > /etc/nginx/sites-available/ltl-league << 'NGINX'
server {
    listen 80;
    server_name _;

    root /var/www/ltl-league;
    index index.html;

    # HTML 不缓存（避免前端更新后仍命中旧页面）
    location ~* \.html$ {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        expires -1;
    }

    # 主页面路由
    location = / {
        try_files $uri $uri/ /index.html;
    }

    # API 代理到后端
    location /api/ {
        proxy_pass http://127.0.0.1:8080/api/;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    # JS/CSS 不做强缓存（避免 ESM import 依赖被缓存导致逻辑不更新）
    location ~* \.(js|css)$ {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        expires -1;
    }

    # 图片/字体等静态资源可缓存
    location ~* \.(png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000";
    }

    # 其他路由支持 SPA
    location / {
        try_files $uri $uri/ /index.html;
    }
}
NGINX

# 启用站点
ln -sf /etc/nginx/sites-available/ltl-league /etc/nginx/sites-enabled/

# 测试 Nginx 配置
nginx -t && systemctl reload nginx
echo "Nginx 配置完成"
EOF

# 4. 验证部署
echo ""
echo "[4/4] 验证部署..."
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" http://${SERVER_HOST}/ || echo "000")
if [ "$HTTP_CODE" = "200" ]; then
    echo "✓ 前端部署成功"
    echo "✓ 访问地址: http://${SERVER_HOST}"
    echo ""
    echo "页面列表:"
    echo "  - http://${SERVER_HOST}/index.html (主页)"
    echo "  - http://${SERVER_HOST}/schedule.html (赛程)"
    echo "  - http://${SERVER_HOST}/standings.html (积分榜)"
    echo "  - http://${SERVER_HOST}/teams.html (队伍)"
    echo "  - http://${SERVER_HOST}/rules.html (规则)"
    echo "  - http://${SERVER_HOST}/tools.html (计算器)"
    echo "  - http://${SERVER_HOST}/match-history.html (战绩)"
else
    echo "✗ 部署验证失败 (HTTP $HTTP_CODE)"
    echo "请检查 Nginx 日志: ssh ${SERVER_USER}@${SERVER_HOST} 'tail -20 /var/log/nginx/error.log'"
    exit 1
fi

echo ""
echo "========================================="
echo "  部署完成！"
echo "========================================="