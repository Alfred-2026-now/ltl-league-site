# LTL League Site

LTL 职业联赛管理系统 - 完整的联赛运营管理平台，支持赛程管理、赛果录入、P币经济系统、选手身管理等核心功能。

## 当前能力

### 前台功能
- **信息展示**：公告、积分榜、队伍名单、规则手册、赛程
- **数据统计**：实时积分排名、队伍总身价、工资帽参考线
- **比赛详情**：历史战绩、小局数据、阵容信息、P币流水
- **计算工具**：奢侈税计算器、租借费计算器

### 管理后台
- **赛程管理**：创建比赛、发布赛程、编辑对阵、设置状态
- **赛果录入**：比分录入、小局详情、截图上传、积分设置
- **经济结算**：奢侈税计算、租借费录入、身价调整、实时预览
- **数据管理**：P币流水查询、身价变化记录、奖励规则配置
- **草稿系统**：保存草稿、发布赛果、撤回回滚

### 经济系统
- **P币系统**：比赛奖励、奢侈税、租借费、弃赛罚款
- **身价系统**：客观变化（比赛表现）、主观调整（管理员评估）
- **累进税率**：BO2/BO3 不同税率的超税线惩罚机制
- **版本管理**：赛果版本号，支持数据回溯

## 目录结构

```text
ltl-league-site/
├── *.html                    # 前台页面（首页、赛程、积分榜等）
├── admin-*.html              # 管理后台页面
├── assets/                   # 静态资源（队徽、图片）
├── src/
│   ├── styles/               # 样式文件
│   ├── features/             # 前台功能模块
│   ├── services/             # API服务封装
│   ├── admin/                # 管理后台脚本
│   └── data/                 # 静态数据
├── backend/                  # 后端服务（Spring Boot）
│   ├── src/main/java/
│   │   └── com/ltl/league/
│   │       ├── controller/   # REST控制器
│   │       ├── service/      # 业务逻辑
│   │       ├── mapper/       # 数据访问
│   │       ├── entity/       # 实体类
│   │       └── dto/          # 数据传输对象
│   └── src/main/resources/
│       ├── application.yml   # 配置文件
│       └── db/               # 数据库脚本
├── scripts/                 # 部署脚本
│   ├── deploy.sh             # 后端部署
│   ├── deploy-frontend.sh    # 前端部署
│   └── deploy-key.pem        # SSH密钥
├── docs/                    # 项目文档
│   ├── FEATURES.md           # 功能说明
│   ├── ARCHITECTURE.md       # 系统架构
│   └── DEPLOYMENT.md         # 部署说明
└── .claude/                  # AI辅助工具配置
```

## 本地开发

### 前端开发

无需安装依赖，使用静态服务器：

```bash
npm run serve
# 访问 http://localhost:8081
```

### 后端开发

```bash
cd backend
mvn spring-boot:run
# 访问 http://localhost:8080
```

### 日常修改入口

**前端相关**：
1. 修改页面结构 → 编辑对应的 `*.html` 文件
2. 修改功能逻辑 → 编辑 `src/features/` 或 `src/admin/` 下文件
3. 修改API调用 → 编辑 `src/services/api.js` 或 `src/admin/api.js`
4. 修改样式 → 编辑 `src/styles/main.css`

**后端相关**：
1. 修改业务逻辑 → 编辑 `backend/src/main/java/com/ltl/league/service/`
2. 修改接口 → 编辑 `backend/src/main/java/com/ltl/league/controller/`
3. 修改数据结构 → 编辑 `backend/src/main/java/com/ltl/league/entity/` 或 `dto/`
4. 修改SQL映射 → 编辑 `backend/src/main/java/com/ltl/league/mapper/`

## 代码检查

```bash
# 前端语法检查
npm run check

# 后端编译检查
cd backend
mvn clean compile
```

## 协作约定

Git 分支、提交和合并规范见 [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md)。
系统边界和模块职责见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。
后续功能演进见 [docs/ROADMAP.md](docs/ROADMAP.md)。
国服战绩录入与比赛详情设计见 [docs/MATCH_RESULT_IMPORT.md](docs/MATCH_RESULT_IMPORT.md)。
战绩详情待细化项见 [docs/MATCH_DETAIL_OPEN_ITEMS.md](docs/MATCH_DETAIL_OPEN_ITEMS.md)。

---

## 系统文档

### 📚 [功能说明](docs/FEATURES.md)
详细介绍系统的所有功能模块，包括：
- 前台功能（赛程、积分榜、队伍、比赛战绩等）
- 管理后台功能（赛程管理、赛果录入、经济管理等）
- 经济系统（P币、身价、奢侈税、租借费）
- 赛制支持和特殊功能

### 🏗️ [系统架构](docs/ARCHITECTURE.md)
说明系统的技术架构和设计，包括：
- 技术栈（前端、后端、数据库）
- 项目结构
- 数据库设计
- 接口设计
- 安全机制

### 🚀 [部署说明](docs/DEPLOYMENT.md)
指导如何部署和运维系统，包括：
- 部署架构
- 目录结构
- 部署脚本使用
- 服务配置
- 常见问题解决
- 备份与恢复

---

## 快速部署

### 一键部署脚本

```bash
# 部署后端
bash scripts/deploy.sh

# 部署前端
bash scripts/deploy-frontend.sh
```

### 访问地址

- **前台首页**：http://123.57.19.160
- **赛程页面**：http://123.57.19.160/schedule.html
- **积分榜**：http://123.57.19.160/standings.html
- **管理后台**：http://123.57.19.160/admin-matches.html

---

## 核心功能

### 赛程管理
- 创建比赛、发布赛程
- 赛果录入、撤回
- 小局详情、截图上传

### 经济系统
- P币流水管理
- 奢侈税计算（超税线累进税率）
- 租借费计算（BO2: 45%, BO3: 60%）
- 身价调整（客观+主观）

### 数据展示
- 积分榜实时排名
- 队伍详情与阵容
- 比赛战绩与数据

---

## 技术栈

- **前端**：原生 JavaScript (ES Modules)
- **后端**：Spring Boot 2.7.18 + MyBatis-Plus
- **数据库**：MySQL 8.0
- **Web服务器**：Nginx
- **Java版本**：OpenJDK 17
