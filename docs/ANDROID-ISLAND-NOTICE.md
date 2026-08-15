# Android 应用内灵动岛通知

软件内提示（收藏、无法打开、评论操作、海报导出、显示配置导入等）**统一**走顶部灵动岛胶囊，不再使用 Snackbar / Toast。

系统媒体通知（Media3 前台服务、锁屏、通知栏播放控件）**不在此列**，仍由 Media3 独占。

---

## 怎么发

```kotlin
// Compose / Activity / 任意 Context
context.showIslandNotice("已收藏歌单", coverUrl = playlistCover)

// ViewModel / Application 单例
app.islandNoticeCenter.show("已收藏歌单", coverUrl)
```

| 参数 | 说明 |
|---|---|
| `message` | 岛内文案，空串会被丢弃 |
| `coverUrl` | 可选。有则：Logo 圆点 → 功能封面 → 展开；无则展开后左侧仍是 ZMusic Logo |

不要再调用 `Toast.makeText`、`SnackbarHost`、页面内自定义 toast。新功能需要反馈时只调上面两个入口。

---

## 结构

| 文件 | 职责 |
|---|---|
| `ui/notice/IslandNotice.kt` | `IslandNotice` 数据、`IslandNoticeCenter` 队列、`Context.showIslandNotice` |
| `ui/notice/IslandNoticeHost.kt` | `IslandNoticeRoot` 宿主：采样整页液体玻璃 + 岛动画 |
| `ui/main/MainChrome.kt` | `islandLiquidGlass`（Kyant Backdrop，lens 高度 ≤ 胶囊半高） |
| `ZMusicApplication` | `islandNoticeCenter` 单例 |
| `ui/orientation/ZMusicOrientationHost.kt` | 包一层 `IslandNoticeRoot`（方向蒙版之下） |

宿主在 **NavHost 之外、方向切换蒙版之下**：登录 / 主壳 / 全屏播放器都能出岛；横竖屏过渡蒙版仍盖在最上面。

液体玻璃与 Dock 相同约束：玻璃节点必须在 `layerBackdrop` **记录层之外**。二次确认弹窗（`GlassAlertDialog`）也画在这一层，**不要**用系统 `Dialog`（另开窗口会让状态栏变黑，也无法采主界面玻璃）。

---

## 动画（单条）

1. 状态栏下方（竖屏）或刘海/安全区下方（横屏隐藏状态栏）**水平居中**出现一个圆，圆内是 `ic_logo_vinyl_z`
2. 若有 `coverUrl`：圆还在时交叉淡入功能封面（带轻微缩放）
3. 横向展开成胶囊，文案在封面右侧淡入；宽度按文字测量，竖屏上限约屏宽 78% / 300dp，横屏约 42% / 320dp，避免变成一条顶栏
4. 驻留（短句约 2.4s，长句更久）后收成圆并上移淡出

曲线用过冲贝塞尔 + 弹簧，展开时伴随轻微纵向 squash，避免匀速线性。

---

## 队列（多条）

`IslandNoticeCenter` 内部队列（最多 8 条，超出丢最旧）。宿主是唯一消费者：

- 没有在展示：走「出现」动画
- 已有展示：当前岛**收缩到约 38% 宽度**（收一半）→ **立刻换成下一条文案/封面** → 再展开
- 驻留期内来了下一条：打断驻留，直接走换条，不会叠两条岛
- 换条动画中途不插队，下一条等本轮收缩/展开结束

---

## 文字

单行。未超出：完整显示。超出：先 `…` 截断，约 0.7s 后自动横向 marquee，两端做淡出，避免硬切。

---

## 横竖屏

| | 竖屏 | 横屏 |
|---|---|---|
| 高度 | 40dp | 36dp（圆直径 = 高度，保证垂直居中） |
| 顶距 | 状态栏 + 6dp | 安全区/刘海 + 8dp（状态栏已隐藏） |
| 最大宽度 | min(78% 屏宽, 300dp) | min(42% 屏宽, 320dp)，保持顶栏中央胶囊而不是通栏 |

旋转过程中队列仍在 Application 单例里；方向蒙版 zIndex 高于岛。

---

## 二次确认弹窗（`GlassAlertDialog`）

取消收藏、退出登录、登录同意条款等**确认框**用同一个组件，画在 `IslandNoticeRoot` 里（与岛同窗、记录层外），因此：

- 状态栏保持沉浸，不会出现一块突兀黑条
- 卡片是 Kyant 液体玻璃，能采到底下的主界面

```kotlin
GlassAlertDialog(
    title = "取消收藏此歌单？",
    message = "「$title」将从你的收藏中移除",
    confirmLabel = "取消收藏",
    confirmDestructive = true,
    onConfirm = { /* ... */ },
    onDismiss = { /* ... */ },
)
```

不要用 Compose `Dialog` / `Toast` 做这类确认。玻璃节点不能画进系统 Dialog 窗口。

---

## 已接入入口

- 主壳：点播权限拒绝、首页无法打开等 `onHint`
- 歌单收藏 / 取消收藏（带歌单封面）
- 评论点赞 / 抱抱 / 回复
- 海报制作导出
- 播放器显示配置导入 / 扫码

新增软件内提示时：发 `showIslandNotice`，并尽可能带上当前功能封面。
