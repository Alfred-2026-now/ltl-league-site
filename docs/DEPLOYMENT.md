# LTL 联赛网站 - 部署说明

## 服务器信息

- **地址**：123.57.19.160
- **系统**：Ubuntu 22.04 LTS
- **Web服务器**：Nginx
- **Java**：OpenJDK 17

---

## 1. 部署架构

当前部署支持生产环境和本地测试环境并行运行。生产环境保持服务器根路径访问不变，测试环境在本机启动前端和后端，只连接服务器上的测试数据库。

| 环境 | 访问地址 | 前端目录 | 后端服务 | 后端端口 | 数据库 |
|------|----------|----------|----------|----------|--------|
| 生产 | `http://123.57.19.160/` | `/var/www/ltl-league` | `ltl-league-backend` | `8080` | `ltl_league` |
| 测试 | `http://127.0.0.1:4173/` | 本地仓库目录 | 本地 Java 进程 | `8080` | `ltl_league_test` |

本地测试前端访问 `127.0.0.1` 时会自动请求 `http://127.0.0.1:8080/api`。测试后端连接服务器上的 `ltl_league_test`，不会写生产库，也不占用服务器测试端口。

```
生产：浏览器 → 123.57.19.160:80 → Nginx → 127.0.0.1:8080/api → ltl_league

测试：浏览器 → 127.0.0.1:4173 → 127.0.0.1:8080/api → 123.57.19.160:3306/ltl_league_test
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

本地测试运行时目录：

```
.local-test/
├── backend.pid                 # 本地测试后端 PID
├── frontend.pid                # 本地测试前端 PID
└── logs/
    ├── backend.log
    └── frontend.log

uploads-test/                   # 本地测试上传文件目录
```

---

## 3. 部署脚本

### 3.1 后端部署脚本 (`scripts/deploy.sh`)

**功能**：
1. 本地编译打包 JAR
2. 上传 JAR 到服务器
3. 按环境生成后端配置和 systemd 服务
4. 重启对应环境的 systemd 服务

**使用**：
```bash
# 部署生产环境
bash scripts/deploy.sh
```

**强制重新编译**：
```bash
FORCE_REBUILD=1 bash scripts/deploy.sh
```

### 3.2 测试后端部署脚本 (`scripts/deploy-test.sh`)

**功能**：
1. 本地编译打包 JAR
2. 停止旧的本地测试后端进程
3. 以 `test` profile 在本地启动后端
4. 默认监听 `127.0.0.1:8080`
5. 连接服务器上的 `ltl_league_test` 数据库

**使用**：
```bash
bash scripts/deploy-test.sh
```

**强制重新编译**：
```bash
FORCE_REBUILD=1 bash scripts/deploy-test.sh
```

可选环境变量：
```bash
DB_NAME=ltl_league_test bash scripts/deploy-test.sh
```

### 3.3 前端部署脚本 (`scripts/deploy-frontend.sh`)

**功能**：
1. 检查本地文件
2. 上传 HTML/JS/CSS 到服务器
3. 配置 Nginx
4. 验证部署

**使用**：
```bash
# 部署生产前端
bash scripts/deploy-frontend.sh
```

### 3.4 测试前端部署脚本 (`scripts/deploy-frontend-test.sh`)

**功能**：
1. 检查本地前端文件
2. 停止旧的本地测试前端进程
3. 使用 `tools/static-server.mjs` 启动本地静态服务
4. 默认访问地址 `http://127.0.0.1:4173/`
5. 前端在本地访问时自动请求 `http://127.0.0.1:8080/api`

**使用**：
```bash
bash scripts/deploy-frontend-test.sh
```

这个脚本只启动本地前端，不会上传远程目录、不会修改 Nginx、不会占用服务器端口。

### 3.5 联盟资产迁移脚本 (`backend/src/main/resources/db/migration_league_assets.sql`)

联盟资产功能新增 `league_asset_ledger` 表。首次上线前需要在目标数据库执行一次迁移脚本，脚本会创建联盟资产流水表，并把历史已上交/销毁的 P 币汇总为一条 `history_seed` 初始化流水。

**测试库执行**：
```bash
mysql -u ltl_user -p ltl_league_test < backend/src/main/resources/db/migration_league_assets.sql
```

**生产库执行**：
```bash
mysql -u ltl_user -p ltl_league < backend/src/main/resources/db/migration_league_assets.sql
```

先在测试库验证后台 `资产监测` 页面数据无误，再执行生产库迁移。迁移脚本只新增/初始化联盟资产表，不会修改现有队伍、选手和赛果数据。

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
# 生产服务
systemctl start ltl-league-backend
systemctl stop ltl-league-backend
systemctl restart ltl-league-backend
systemctl status ltl-league-backend
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

测试环境不再配置服务器 Nginx。`scripts/deploy-frontend-test.sh` 会在本机启动静态服务，默认监听 `127.0.0.1:4173`。

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
- **生产数据库**：ltl_league
- **测试数据库**：ltl_league_test
- **用户**：ltl_user
- **字符集**：utf8mb4
- **时区**：Asia/Shanghai

### 5.2 初始化脚本

**建表脚本**：`backend/src/main/resources/db/schema.sql`
**初始数据**：`backend/src/main/resources/db/data.sql`

测试库已独立维护，常规测试环境部署不需要同步生产库。如需刷新测试库数据，请手工执行并确认不会影响正在测试的内容：

```bash
mysql -h 123.57.19.160 -u ltl_user -p -e \
  "DROP DATABASE IF EXISTS ltl_league_test; CREATE DATABASE ltl_league_test DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

mysqldump -h 123.57.19.160 -u ltl_user -p --single-transaction ltl_league \
  | mysql -h 123.57.19.160 -u ltl_user -p ltl_league_test
```

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

访问：http://127.0.0.1:4173

### 6.3 本地启动测试 profile

```bash
bash scripts/deploy-test.sh
bash scripts/deploy-frontend-test.sh
```

访问：http://127.0.0.1:4173

测试后端访问：http://127.0.0.1:8080/api/teams

---

## 7. 环境变量

### 7.1 后端配置

**生产配置由部署脚本生成，测试配置使用 `backend/src/main/resources/application-test.yml` 并可通过环境变量覆盖**：
```yaml
spring:
  datasource:
    url: jdbc:mysql://123.57.19.160:3306/<环境对应数据库>?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
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
- `ltl.upload.dir`：上传目录（生产默认 `/var/www/ltl-league/uploads`，本地测试默认 `uploads-test`）
- `ltl.upload.url-prefix`：上传URL前缀（生产默认 `/uploads`，本地测试默认 `http://127.0.0.1:4173/uploads-test`）
- `SERVER_HOST`：部署服务器，默认 `123.57.19.160`。
- `DB_HOST` / `DB_PORT` / `DB_NAME`：本地测试后端连接的数据库地址、端口和库名。
- `DB_USERNAME` / `DB_PASSWORD`：数据库账号密码。
- `BACKEND_PORT` / `FRONTEND_PORT`：本地测试后端和前端端口，默认分别为 `8080` 和 `4173`。

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

### 8.3 测试环境发布

- [ ] 启动本地测试后端：`bash scripts/deploy-test.sh`
- [ ] 启动本地测试前端：`bash scripts/deploy-frontend-test.sh`
- [ ] 访问 `http://127.0.0.1:4173/`
- [ ] 验证测试页面请求的是 `http://127.0.0.1:8080/api/...`
- [ ] 确认测试服务日志：`tail -f .local-test/logs/backend.log`

---

## 9. 常见问题

### 9.1 后端启动失败

**检查服务状态**：
```bash
systemctl status ltl-league-backend
kill -0 "$(cat .local-test/backend.pid)"
```

**查看日志**：
```bash
journalctl -u ltl-league-backend -n 50
tail -80 .local-test/logs/backend.log
```

**常见原因**：
- Java版本不匹配（需要JDK 17）
- 数据库连接失败
- 端口被占用

### 9.2 前端显示异常

**检查静态文件**：
```bash
ls -la /var/www/ltl-league/
ls -la .
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
