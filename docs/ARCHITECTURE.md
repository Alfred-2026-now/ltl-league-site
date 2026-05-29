# LTL 联赛网站 - 系统架构

## 概述

采用前后端分离架构，前端静态托管，后端 Spring Boot RESTful API。

---

## 1. 技术栈

### 1.1 前端
- **语言**：原生 JavaScript (ES Modules)
- **样式**：自定义 CSS
- **构建**：无需构建，直接运行
- **依赖**：无第三方库

### 1.2 后端
- **框架**：Spring Boot 2.7.18
- **Java版本**：OpenJDK 17
- **ORM**：MyBatis-Plus 3.5.5
- **数据库**：MySQL 8.0
- **缓存**：无

---

## 2. 项目结构

```
ltl-league-site/
├── frontend/                    # 前端资源
│   ├── *.html                  # 页面文件
│   ├── src/
│   │   ├── styles/            # 样式文件
│   │   ├── features/          # 功能模块
│   │   ├── services/          # API服务
│   │   ├── admin/             # 管理后台脚本
│   │   └── data/              # 静态数据
│   └── assets/                # 静态资源（队徽等）
└── backend/                    # 后端服务
    ├── src/main/java/
    │   └── com/ltl/league/
    │       ├── controller/     # REST控制器
    │       ├── service/        # 业务逻辑
    │       ├── mapper/         # MyBatis Mapper
    │       ├── entity/         # 实体类
    │       ├── dto/            # 数据传输对象
    │       ├── exception/      # 异常处理
    │       └── common/         # 通用类
    └── src/main/resources/
        ├── application.yml     # 配置文件
        └── db/                 # 数据库脚本
            ├── schema.sql      # 建表脚本
            └── data.sql        # 初始数据
```

---

## 3. 数据库设计

### 3.1 核心表

#### teams（队伍表）
- `id`：主键
- `state`：队伍简称（秦、楚、蜀等）
- `name`：队伍全称
- `p_coins`：P币余额
- `points`：联赛积分
- `rank`：排名
- `logo_url`：队徽URL

#### players（选手表）
- `id`：主键
- `name`：选手名称
- `team_id`：所属队伍
- `value`：身价
- `status`：状态（1=在职）
- `is_loan`：是否租借

#### matches（比赛表）
- `id`：主键
- `match_id`：比赛标识符
- `season`：赛季
- `round`：轮次
- `format`：赛制（BO2/BO3等）
- `home_team_id`：主队ID
- `away_team_id`：客队ID
- `home_score`：主队比分
- `away_score`：客队比分
- `home_points`：主队积分
- `away_points`：客队积分
- `status`：状态
- `schedule_published`：赛程是否发布
- `result_published`：赛果是否发布

#### match_results（赛果表）
- `id`：主键
- `match_id`：关联比赛
- `version_no`：版本号
- `status`：状态（draft/published/withdrawn）
- `result_type`：结果类型（normal/forfeit）
- `home_score`、`away_score`：比分
- `winner_team_id`：胜方ID
- `home_points`、`away_points`：积分
- `tax_exempt`：是否免税
- `home_line_value`、`away_line_value`：出场身价
- `home_roster_size`、`away_roster_size`：阵容人数

#### games（小局表）
- `id`：主键
- `match_id`、`result_id`：关联
- `game_index`：局数
- `winner`：胜方
- `blue_team`、`red_team`：阵营
- `duration_seconds`：时长

#### p_ledger（P币流水表）
- `id`：主键
- `team_id`：队伍ID
- `match_id`、`result_id`：关联
- `type`：类型（match_reward/luxury_tax/loan_fee/forfeit_penalty）
- `amount`：金额
- `balance_before`、`balance_after`：余额前后
- `version`：赛果版本
- `is_voided`：是否作废

#### valuation_changes（身价变化表）
- `id`：主键
- `player_id`：选手ID
- `match_id`、`result_id`：关联
- `before_value`、`after_value`：身价前后
- `objective_delta`：客观变化
- `subjective_delta`：主观变化
- `subjective_reason`：原因
- `version`：赛果版本
- `is_voided`：是否作废

---

## 4. 后端架构

### 4.1 分层设计

```
Controller 层（接口层）
    ↓
Service 层（业务逻辑层）
    ↓
Mapper 层（数据访问层）
    ↓
Database
```

### 4.2 核心模块

#### MatchService
- 比赛CRUD
- 比赛详情构造（含小局、流水、身价变化）
- 版本控制

#### AdminMatchService
- 管理后台比赛操作
- 赛程发布/撤回

#### MatchResultService
- 赛果草稿管理
- 赛果发布
- 赛果撤回
- 截图上传

#### MatchSettlementService
- 结算预览
- 结算应用
- 结算回滚
- 积分更新

#### MatchSettlementCalculator
- 奢侈税计算
- 租借费计算
- 身价变化计算

#### AdminEconomyService
- P币流水查询
- 身价变化查询
- 手动身价调整

#### SettlementRewardRuleService
- 奖励规则配置
- 规则匹配查询

---

## 5. 前端架构

### 5.1 模块划分

#### features/
- `matches.js`：比赛相关逻辑
- `standings.js`：积分榜逻辑
- `teams.js`：队伍逻辑
- `navigation.js`：导航逻辑
- `content.js`：内容展示
- `leagueMetrics.js`：联赛统计
- `matchMetrics.js`：比赛统计

#### services/
- `api.js`：API调用封装
- `league.js`：数据获取

#### admin/
- `matches.js`：赛程管理
- `match-result.js`：赛果录入
- `p-ledger.js`：P币流水
- `valuation.js`：身价管理
- `reward-rules.js`：奖励规则
- `api.js`：管理后台API

### 5.2 数据流

```
用户操作
    ↓
事件处理
    ↓
API调用（services/api.js）
    ↓
后端处理
    ↓
数据渲染
```

---

## 6. 接口设计

### 6.1 前台接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/teams | 获取所有队伍 |
| GET | /api/players | 获取所有选手 |
| GET | /api/matches | 获取所有比赛 |
| GET | /api/matches/{id} | 获取比赛详情 |
| GET | /api/announcements | 获取公告 |
| GET | /api/rules | 获取规则 |
| GET | /api/schedule | 获取赛程 |

### 6.2 管理后台接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/admin/matches | 管理比赛列表 |
| POST | /api/admin/matches | 创建比赛 |
| PUT | /api/admin/matches/{id} | 更新比赛 |
| POST | /api/admin/matches/{id}/publish | 发布赛程 |
| POST | /api/admin/matches/{id}/unpublish | 撤回赛程 |
| GET | /api/admin/matches/{id}/result | 获取赛果 |
| POST | /api/admin/matches/{id}/result/draft | 创建草稿 |
| PUT | /api/admin/matches/{id}/result/{resultId} | 更新草稿 |
| POST | /api/admin/matches/{id}/result/{resultId}/publish | 发布赛果 |
| POST | /api/admin/matches/{id}/result/{resultId}/withdraw | 撤回赛果 |
| POST | /api/admin/matches/{id}/result/settlement-preview | 结算预览 |
| POST | /api/admin/matches/{id}/result/{resultId}/screenshots | 上传截图 |
| DELETE | /api/admin/attachments/{id} | 删除附件 |
| GET | /api/admin/p-ledger | P币流水查询 |
| GET | /api/admin/valuation-changes | 身价变化查询 |
| POST | /api/admin/valuation-changes/manual-adjustment | 手动调整身价 |
| GET | /api/admin/reward-rules | 奖励规则查询 |
| POST | /api/admin/reward-rules | 创建规则 |
| PUT | /api/admin/reward-rules/{id} | 更新规则 |
| DELETE | /api/admin/reward-rules/{id} | 删除规则 |

---

## 7. 安全机制

### 7.1 接口保护
- 管理后台接口无前端鉴权（依赖服务器访问控制）

### 7.2 数据验证
- 后端参数校验
- 业务规则验证
- SQL注入防护（MyBatis预编译）

### 7.3 操作审计
- P币流水记录
- 身价变化记录
- 赛果版本管理

---

## 8. 扩展点

### 8.1 可扩展功能
- **数据统计**：更多维度的数据分析
- **历史回溯**：完整的历史数据查询
- **权限系统**：分级管理员权限
- **通知系统**：比赛提醒、公告推送

### 8.2 性能优化空间
- **数据库索引**：优化查询性能
- **缓存机制**：减少数据库查询
- **静态资源CDN**：加速前端访问
- **API响应缓存**：减少计算开销
