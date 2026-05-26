# Git 工作流

## 分支模型

推荐使用轻量 Feature Branch 工作流：

- `main`：稳定分支，只合并已经验证过的变更。
- `feature/<name>`：新功能，例如 `feature/match-admin`。
- `fix/<name>`：缺陷修复，例如 `fix/luxury-tax-rounding`。
- `chore/<name>`：工程化、文档、重构，例如 `chore/project-foundation`。

当前环境因为本地 Git 引用权限限制，本次分支使用 `project-foundation`。

## 日常开发流程

```powershell
git switch main
git pull
git switch -c feature/your-change
```

完成修改后：

```powershell
git status
git diff
git add .
git commit -m "feat: add match schedule module"
git push -u origin feature/your-change
```

然后在 GitHub 发起 Pull Request，经过检查后合并到 `main`。

## 提交信息

采用 Conventional Commits 的简化版本：

- `feat:` 新功能
- `fix:` 修复问题
- `refactor:` 不改变行为的重构
- `docs:` 文档
- `style:` 纯样式调整
- `test:` 测试
- `chore:` 工程配置、目录调整、依赖维护

示例：

```text
refactor: split league data and page features
docs: add git workflow guide
fix: correct BO3 loan fee calculation
```

## Pull Request 检查项

合并前至少确认：

- 页面能正常打开，没有控制台语法错误。
- 修改联赛规则时，同步检查相关计算器。
- 修改队伍或选手数据时，确认积分榜、工资帽线、队伍卡片显示一致。
- 修改样式时，检查桌面和移动端主要视图。
- 文档或数据结构变化时，更新 `README.md` 或 `docs/`。

## 发布建议

在项目还是静态站时，可以直接通过 GitHub Pages、Vercel 或 Netlify 发布。引入后台和登录后，再把部署流程升级为：

1. PR 检查通过。
2. 合并到 `main`。
3. 自动构建预览。
4. 管理员确认后发布生产版本。
