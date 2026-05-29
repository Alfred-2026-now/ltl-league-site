# LTL 联赛网站 - 部署说明

## 服务器信息

- **地址**：123.57.19.160
- **系统**：Ubuntu 22.04 LTS
- **Web服务器**：Nginx
- **Java**：OpenJDK 17

---

## 1. 部署架构

```
                    ┌─────────────┐
                    │   Nginx     │
                    │   :80       │
                    └──────┬──────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌───────────────┐  ┌───────────────┐  ┌───────────────┐
│  静态资源     │  │   API代理     │  │   上传文件     │
│  /var/www/    │  │   /api/ →     │  │   /uploads/    │
│  ltl-league/  │  │   :8080      │  │                │
└───────────────┘  └───────────────┘  └───────────────┘
                           │
                           ▼
                  ┌────────────────┐
                  │  Spring Boot   │
                  │  :8080         │
                  └────────────────┘
```

---

## 2. 目录结构

### 2.1 服务器目录

```
/opt/ltl-league/
├── backend/                    # 后端服务
│   ├── league-backend-1.0.0.jar
│   ├── start.sh               # 启动脚本
│   └── application-prod.yml   # 生产配置（自动生成）
└── uploads/                    # 上传文件（截图等）

/var/www/ltl-league/            # 前端静态文件
├── *.html                      # 页面文件
├── src/                        # 源代码
│   ├── styles/
│   ├── features/
│   ├── services/
│   ├── admin/
│   └── data/
└── assets/                     # 静态资源
```

---

## 3. 部署脚本

### 3.1 后端部署脚本 (`scripts/deploy.sh`)

**功能**：
1. 本地编译打包 JAR
2. 上传 JAR 到服务器
3. 生成生产环境配置
4. 重启 systemd 服务

**使用**：
```bash
cd /Users/a58/xinghe/ltl-league-site
bash scripts/deploy.sh
```

**强制重新编译**：
```bash
FORCE_REBUILD=1 bash scripts/deploy.sh
```

### 3.2 前端部署脚本 (`scripts/deploy-frontend.sh`)

**功能**：
1. 检查本地文件
2. 上传 HTML/JS/CSS 到服务器
3. 配置 Nginx
4. 验证部署

**使用**：
```bash
bash scripts/deploy-frontend.sh
```

---

## 4. 服务配置

### 4.1 Systemd 服务

**服务文件**：`/etc/systemd/system/ltl-league-backend.service`

```ini
[Unit]
Description=LTL League Backend Service
After=network.target

[Service]
Type=simple
User=ltl
Group=ltl
WorkingDirectory=/opt/ltl-league/backend
ExecStart=/opt/ltl-league/backend/start.sh
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

**服务管理**：
```bash
# 启动服务
systemctl start ltl-league-backend

# 停止服务
systemctl stop ltl-league-backend

# 重启服务
systemctl restart ltl-league-backend

# 查看状态
systemctl status ltl-league-backend

# 查看日志
journalctl -u ltl-league-backend -f
```

### 4.2 Nginx 配置

**配置文件**：`/etc/nginx/sites-available/ltl-league`

```nginx
server {
    listen 80;
    server_name _;

    root /var/www/ltl-league;
    index index.html;

    # HTML 不缓存
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
        client_max_body_size 20m;
    }

    # 赛果截图上传目录
    location /uploads/ {
        alias /var/www/ltl-league/uploads/;
        expires 30d;
        add_header Cache-Control "public, max-age=2592000";
    }

    # JS/CSS 不做强缓存
    location ~* \.(js|css)$ {
        add_header Cache-Control "no-cache, no-store, must-revalidate";
        expires -1;
    }

    # 图片/字体可缓存
    location ~* \.(png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 30d;
        add_header Cache-Control "public, max-age=2592000";
    }

    # SPA 路由支持
    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

**Nginx 管理**：
```bash
# 测试配置
nginx -t

# 重载配置
nginx -s reload

# 重启服务
systemctl restart nginx
```

---

## 5. 数据库配置

### 5.1 数据库信息

- **地址**：123.57.19.160:3306
- **数据库**：ltl_league
- **用户**：ltl_user
- **字符集**：utf8mb4
- **时区**：Asia/Shanghai

### 5.2 初始化脚本

**建表脚本**：`backend/src/main/resources/db/schema.sql`
**初始数据**：`backend/src/main/resources/db/data.sql`

---

## 6. 开发环境

### 6.1 本地启动后端

```bash
cd backend
mvn spring-boot:run
```

访问：http://localhost:8080

### 6.2 本地启动前端

```bash
npm run serve
# 或
node tools/static-server.mjs
```

访问：http://localhost:8081

---

## 7. 生产环境变量

### 7.1 后端配置

**application-prod.yml**（自动生成）：
```yaml
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
```

### 7.2 环境变量

- `ltl.match.bo5-enabled`：是否启用BO5（默认false）
- `ltl.upload.dir`：上传目录（默认/var/www/ltl-league/uploads）
- `ltl.upload.url-prefix`：上传URL前缀（默认/uploads）

---

## 8. 部署检查清单

### 8.1 首次部署

- [ ] 创建服务器目录
- [ ] 创建 ltl 用户
- [ ] 配置 MySQL 数据库
- [ ] 执行数据库脚本
- [ ] 配置 systemd 服务
- [ ] 配置 Nginx
- [ ] 上传 SSH 密钥
- [ ] 部署后端
- [ ] 部署前端
- [ ] 验证功能

### 8.2 日常更新

- [ ] 本地测试通过
- [ ] 编译后端
- [ ] 部署后端
- [ ] 部署前端
- [ ] 验证功能
- [ ] 检查日志

---

## 9. 常见问题

### 9.1 后端启动失败

**检查服务状态**：
```bash
systemctl status ltl-league-backend
```

**查看日志**：
```bash
journalctl -u ltl-league-backend -n 50
```

**常见原因**：
- Java版本不匹配（需要JDK 17）
- 数据库连接失败
- 端口被占用

### 9.2 前端显示异常

**检查静态文件**：
```bash
ls -la /var/www/ltl-league/
```

**检查Nginx配置**：
```bash
nginx -t
```

**清除浏览器缓存**

### 9.3 API请求失败

**检查后端运行**：
```bash
curl http://127.0.0.1:8080/api/teams
```

**检查Nginx代理**：
```bash
curl http://123.57.19.160/api/teams
```

---

## 10. 备份与恢复

### 10.1 数据库备份

```bash
mysqldump -h 123.57.19.160 -u ltl_user -p ltl_league > backup.sql
```

### 10.2 文件备份

```bash
# 备份上传文件
tar -czf uploads-backup.tar.gz /var/www/ltl-league/uploads/

# 备份前端
tar -czf frontend-backup.tar.gz /var/www/ltl-league/
```

### 10.3 恢复

```bash
# 恢复数据库
mysql -h 123.57.19.160 -u ltl_user -p ltl_league < backup.sql

# 恢复文件
tar -xzf uploads-backup.tar.gz -C /
```

---

## 11. 监控

### 11.1 服务监控

**后端健康检查**：
```bash
curl http://127.0.0.1:8080/api/teams
```

**Nginx状态**：
```bash
systemctl status nginx
```

### 11.2 日志查看

**后端日志**：
```bash
journalctl -u ltl-league-backend -f
```

**Nginx访问日志**：
```bash
tail -f /var/log/nginx/access.log
```

**Nginx错误日志**：
```bash
tail -f /var/log/nginx/error.log
```
