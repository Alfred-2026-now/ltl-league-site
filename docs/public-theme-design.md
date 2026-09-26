# 官网视觉更新

前台使用 `public-site` 类和 `src/styles/public-theme.css` 统一视觉，管理后台继续使用原样式。主题为深墨色、香槟金与海克斯青；中文主视觉使用系统宋体，正文使用系统无衬线字体，无需第三方字体请求。

首页沿用规则页中的 S3「赏金猎人」信息，以赛事任务、选手榜、规则和积分兑换为入口。战队赛入口仍遵循 `main.css` 中准备期隐藏规则。公告由真实接口加载，支持空列表、失败提示和重试，不展示虚构赛况。

## 主视觉素材

- 文件：`assets/rift-season-3.webp`（1983 × 793，约 170 KiB）
- 方式：内置 image_gen 生成，再转为 WebP；不是英雄联盟官方素材。
- 原始文件保留在生成工具输出目录，项目无需依赖该目录。
- 完整生成提示词：

> Use case: stylized-concept. Asset type: ultra-wide cinematic website hero background for LTL, a League of Legends community competition, season 3 Bounty Hunters. Create premium painterly game splash art of Summoner's Rift at dusk: towering weathered stone ruins, deep pine forest, teal river mist, an immense glowing cyan hextech crystal nexus on the RIGHT third, a small cloaked bounty hunter seen from behind on a right foreground cliff holding a long weapon, tiny amber sparks. Epic atmospheric scale, intricate fantasy architecture, polished Riot-like painted game cinematic art, NOT photorealistic, NOT UI mockup. Panoramic landscape about 2.5:1. LEFT 45 percent must be quiet very dark blue-black forest and mist with low detail for HTML headline overlay. Rich petroleum teal shadows, warm antique gold rim light, restrained emerald-cyan magical glow, beautiful luminous environment on right. No text, no letters, no typography, no logos, no watermark, no border. Full bleed illustration.

## 本地预览

`npm run serve` 后打开 `http://127.0.0.1:4173`。首页首屏和静态规则不依赖后端；公告、账号、排行榜和任务仍通过原有 API 获取数据。未启动后端时，这些动态区域无法展示业务数据。

## 验证记录

- JavaScript 语法检查通过，现有 8 项测试通过（`node --test tests/*.test.*`）。
- 浏览器检查：首页桌面 1280px、手机 390px 和 320px；手机规则页、导航展开与 Escape 收起、排行榜身价/赏金切换、登录页；平板 768px 积分兑换页。
- 首页公告、选手榜和兑换列表使用当前后端真实数据验证；检查的视口没有页面横向溢出。
- 当前本地后端赛事任务接口返回 404，任务业务操作未做端到端验证。
- 未执行登录、兑换、转赠等写入操作，未发布到线上。
