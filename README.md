# LTL League Site

LTL 联赛规则中心与赛事官网。当前版本保留原有电竞风视觉基调，并把页面内容、联赛数据、计算规则和交互逻辑拆分为可维护的模块，为后续联赛管理系统功能做结构准备。

## 当前能力

- 展示公告、积分榜、队伍名单、规则手册、计算器和赛程。
- 基于结构化队伍数据自动计算在职总身价、工资帽参考线和积分榜排序。
- 提供奢侈税、租借费、P 币兑换三个前端计算器。
- 无第三方依赖，通过本地静态服务器即可查看。

## 目录结构

```text
.
├── assets/                 # 队伍视觉资产
├── docs/                   # 架构、工作流和路线图文档
├── src/
│   ├── data/               # 联赛静态数据
│   ├── features/           # 页面功能模块和 DOM 交互
│   ├── services/           # 可复用业务计算逻辑
│   └── styles/             # 样式入口
└── index.html              # 静态页面入口
```

## 本地开发

当前不需要安装依赖。因为浏览器 ES Module 更适合通过 HTTP 加载，建议启动本地静态服务器：

双击根目录的 `preview.bat`，或在终端运行：

```powershell
.\preview.bat
```

也可以直接使用 npm 脚本：

```powershell
npm run serve
```

然后访问 `http://localhost:4173`。

日常修改入口：

1. 修改页面结构优先编辑 `index.html`。
2. 修改联赛数据优先编辑 `src/data/league.js`。
3. 修改计算规则优先编辑 `src/services/leagueMetrics.js`。
4. 修改页面交互优先编辑 `src/features/` 下对应模块。

## 校验

```powershell
npm run check
```

更完整的工程化校验会在引入构建工具和测试框架后补齐。

## 协作约定

Git 分支、提交和合并规范见 [docs/GIT_WORKFLOW.md](docs/GIT_WORKFLOW.md)。
系统边界和模块职责见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。
后续功能演进见 [docs/ROADMAP.md](docs/ROADMAP.md)。
国服战绩录入与比赛详情设计见 [docs/MATCH_RESULT_IMPORT.md](docs/MATCH_RESULT_IMPORT.md)。
战绩详情待细化项见 [docs/MATCH_DETAIL_OPEN_ITEMS.md](docs/MATCH_DETAIL_OPEN_ITEMS.md)。
