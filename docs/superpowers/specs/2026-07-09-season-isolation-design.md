# 赛季隔离设计（S1 → S2 过渡）

- 日期：2026-07-09
- 状态：已确认，待出实施计划
- 范围：S1 赛季结束 → S2 赛季过渡，实现战队数据按赛季隔离

## 一、背景与目标

S1 赛季结束，进入 S2 新赛季。需求：

1. 新赛季战队**完全重组、队名会变、国家体系也会变**（不再是秦/楚/蜀/吴/越/燕），属于全新实体
2. 做赛季隔离，**保留 S1 的历史与战队积分**（暂不开发历史展示入口，仅保留数据）
3. 选手个人的**身价（value）与存款（deposit）保持不变**
4. 所有选手统一变更为自由人（status=3），无论原状态是在职(1)还是离队(2)
5. 本次过渡**只做隔离 + 选手变自由人**；新赛季战队后续另行创建

## 二、当前赛季机制分析

项目已存在赛季机制，但覆盖不完整：

- `matches.season VARCHAR(20)`：比赛表已有赛季字段（`schema.sql:54`）
- `application.yml:30` 配置 `ltl.league.current-season: s1`
- `AdminMatchServiceImpl:31` 通过 `@Value("${ltl.league.current-season:s1}")` 注入，创建比赛时自动填入赛季
- `teams` 表**没有 season 字段**，唯一键 `uk_state(state)` 限制 6 个国家不重复（`schema.sql:21`）
- `TeamServiceImpl.getAllTeams()`（`:16`）与 `getByState()`（`:21`）查询**不带任何赛季过滤**
- `players.team_id` 在 `schema.sql:29` 声明为 NOT NULL，但 `init_season_data.sql:48-51` 已实际插入 NULL（自由人），文件与实现不一致
- 战队**无后端创建代码入口**（grep 无 `teamMapper.insert/save/new Team()`），靠 SQL 脚本维护

## 三、方案选择

对比三种 teams 隔离方案：

| 方案 | 做法 | 结论 |
|------|------|------|
| **A. teams 加 season 字段** | 与 `matches.season` 对齐，按 season 过滤 | **采用** |
| B. 用 deleted 字段归档 | 老战队 `deleted=1` | 否决：deleted 语义混乱；历史比赛 JOIN 按 deleted=0 过滤会丢队名 |
| C. 归档表 teams_archive | 复制后清空主表 | 否决：外键断链，查询需 UNION 两表，维护成本高 |

选手表**不加 season 字段**：选手是跨赛季延续的实体（身价、存款要延续），过渡时只置空 `team_id`、改 `status=3`。

## 四、详细设计

### 4.1 Schema 变更

| 表 | 变更 | 说明 |
|----|------|------|
| `teams` | 新增 `season VARCHAR(20) NOT NULL DEFAULT 's1'` | 与 `matches.season` 对齐；现有记录自动标记 s1 |
| `teams` | 唯一键 `uk_state(state)` → `(state, season, deleted)` | 国家体系会变，同赛季内 state 仍唯一 |
| `players` | `team_id` 改为可空 | 修复 schema.sql NOT NULL 与实际实现的长期不一致 |
| `schema.sql` | 同步以上三项 | 让文件与真实表结构一致 |

历史数据表 `matches/match_results/games/game_participants/p_ledger/valuation_changes/match_result_loan_inputs/match_result_valuation_inputs` **全部不动**：它们通过 `team_id`（主键）关联，按 id 查永远能查到老战队。

### 4.2 后端代码适配

核心原则：**按主键查永远全局，按列表/state 查才过滤 season**。

| 方法 | 位置 | 改动 |
|------|------|------|
| `getAllTeams()` | `TeamServiceImpl:16` | 注入 `@Value currentSeason`，加 `.eq(Team::getSeason, currentSeason)` |
| `getByState()` | `TeamServiceImpl:21` | 同上 |
| `Team` 实体 | `Team.java` | 新增 `private String season;` 字段 |
| 其他 `getById/listByIds`（约 15 处，含 `MatchServiceImpl`、各 AdminService） | — | **不动**——历史比赛展示依赖按 id 查老战队 |
| `PlayerServiceImpl`（`getAllPlayers`、`getPlayersByTeamId`） | — | **不动**——选手跨赛季延续 |

效果：前端战队列表页与下拉选择器（经 `GET /teams`）自动只返回当前赛季战队；历史比赛详情内嵌的战队信息走 `getById` 不受影响。

### 4.3 数据迁移脚本

新建 `scripts/season-transition-s1-to-s2.sql`，事务内执行：

```sql
START TRANSACTION;

-- 1. 现有战队标记为 S1（字段默认值已为 s1，显式确认，防止空串）
UPDATE teams SET season = 's1'
WHERE (season IS NULL OR season = '') AND deleted = 0;

-- 2. 所有选手变为自由人（保留 value / deposit / position / is_substitute / game_account / puuid）
UPDATE players SET
  team_id = NULL,
  status = 3,
  is_loan = 0,
  loan_team_id = NULL,
  updated_at = NOW()
WHERE deleted = 0;

COMMIT;
```

保留字段：`value`、`deposit`（按需求保留）、`position`、`is_substitute`、`game_account`、`puuid`（选手属性，与战队无关）。

注意：迁移脚本**只处理数据**，schema 的 ALTER 由独立的建表/迁移脚本负责（见实施计划）。

### 4.4 配置切换

`application.yml`：

```yaml
ltl:
  league:
    current-season: s2   # 由 s1 改为 s2
```

切换后 `AdminMatchServiceImpl` 创建比赛自动用 s2，`TeamServiceImpl` 列表查询自动只返回 s2 战队。

### 4.5 新赛季战队创建

本次过渡不创建新战队。新赛季战队后续通过 SQL 脚本创建（参考现有 `scripts/init_season_data.sql` 形式），每条 `INSERT` 显式带 `season='s2'`。新国家体系与队名、初始 P 币由运营另行确定。

## 五、影响面与边界

**不受影响**：历史比赛、赛果、小局、参赛记录、P 币流水、身价变化、租借费输入、评价数据——全部按 id 关联，保留完整。

**可见变化**：
- 前端战队列表页（`GET /teams`）切换后只返回 s2 战队
- 战队下拉选择器（选手管理、比赛编排等）只列 s2 战队
- 过渡后到新战队创建前，战队列表为空、所有选手为自由人——这是预期的过渡态

**不做项**：
- 历史展示入口（S1 战绩/积分榜页面）——仅保留数据，不开发 UI
- 选手身价/存款的赛季重置——明确保留
- 新赛季战队创建——本次过渡范围外

**风险与缓解**：
- 风险：`uk_state` 唯一键改造期间与现有数据冲突 → 脚本顺序：先 ALTER 加 season 列与默认值回填，再 DROP 旧唯一键、ADD 新唯一键
- 风险：迁移误操作 → 全程在事务内；执行前对生产库做备份
- 风险：本地与线上 schema 漂移 → schema.sql 文件同步更新，保持单一事实来源

## 六、验证方式

**本地验证**：
1. 执行 schema ALTER（加 season 列、改唯一键、team_id 可空）
2. 执行 `season-transition-s1-to-s2.sql`
3. 检查：
   - `teams` 有 6 条 `season='s1'` 记录，`points/p_coins/rank` 与 S1 结算值一致
   - 所有 `players.deleted=0` 记录 `team_id IS NULL AND status=3 AND is_loan=0 AND loan_team_id IS NULL`
   - 选手 `value/deposit` 与迁移前一致（抽查若干）
4. 切配置 `current-season: s2`，启动后端：
   - `GET /teams` 返回空数组（s2 暂无战队）
   - `GET /matches` 历史比赛仍带完整战队名/队徽（走 getById）
5. 后端编译通过（`mvn compile`）

**部署环境验证**（按既有工作约定，在部署服务器上同样验证一遍）：
- 在测试/预演环境完整跑一遍迁移，确认历史比赛展示无异常后再动生产数据
- 生产迁移前备份 `teams` 与 `players` 表

## 七、后续工作（非本次范围）

- 新赛季战队 SQL 脚本（待新国家体系/队名确定）
- 历史展示入口（S1 战绩、积分榜、战队档案页）
- 战队管理后台 CRUD 接口（当前无后端入口，靠 SQL 维护）
