# Windows Architecture

本文描述 `ZMusic` **Windows** 客户端（`Windows/`）当前可维护的主链路与已实现能力。

**与 Android 的关系**：两端**代码独立**，不要求 UI 复刻。播放模式、鉴权 Cookie、歌单拉取与 `/song/url` 解析等与 [`ARCHITECTURE.md`](./ARCHITECTURE.md) 保持**行为级**对齐；类名与界面仅为 Windows 实现。

---

## 1. 技术栈

| 层 | 选型 |
|---|---|
| UI | WPF · WPF-UI（`FluentWindow` / Acrylic） |
| 目标框架 | .NET 9（`net9.0-windows`） |
| MVVM | CommunityToolkit.Mvvm |
| 播放 | `System.Windows.Media.MediaPlayer` |
| 网络 | `HttpClient` + `System.Text.Json` |
| 会话 | DPAPI 保护的本地文件（`%LocalAppData%/ZMusic/session.dat`） |

---

## 2. 启动与鉴权流

```
SplashWindow（描边动画）
    │ 并行：AuthBootstrapper 恢复 / 刷新会话
    ▼
已登录? ──是──► MainWindow
    │
    否
    ▼
LoginWindow（二维码 / 手机验证码）
    ▼
MainWindow
```

要点：

- Splash 关闭前先展示下一窗，避免「最后一窗关闭导致进程退出」
- 登录成功后预热「喜欢」歌单缓存
- 头像等 CDN 资源用 `HttpClient` 拉取（带 Referer），避免 WPF 直接绑远程 URI 失败

---

## 3. 模块分层

### 壳层

| 组件 | 职责 |
|------|------|
| `MainWindow` | 顶栏 + 左侧导航 + 右侧页面宿主；内容区为迷你条让出底部空间 |
| `ShellViewModel` | 导航（推荐 / 歌单 / 喜欢 / 设置）、用户昵称与头像 |
| `MiniPlayerBar` | 墨水风玻璃条：封面、曲名、制作人、模式、播放/暂停、只读进度 |
| Pages | `RecommendPage` / `PlaylistsPage` 占位；`LikedPage` 已实现；`SettingsPage` 占位 |

### 播放层（`Playback/*`）

| 组件 | 职责 |
|------|------|
| `PlaybackBridge` | 进程内门面（对齐 Android `PlaybackBridge`） |
| `PlaylistCoordinator` | 队列、模式、URL 解析、`MediaPlayer` 控制、进度 tick |
| `QueueTrack` | 队列曲目模型 |
| `PlaybackMode` | `Order`（列表循环）→ `RepeatOne` → `Shuffle` |
| `PlaybackViewModel` | 迷你条绑定 |

点播：「喜欢」列表点击 → `LikedViewModel.PlayTrack` → `PlayQueue` → `resolvePlayUrl` → `Open` / `Play`。

URL 解析顺序（与 Android 一致）：

1. `GET /song/url?id=&br=320000&cookie=`
2. 失败则 `GET /song/url/v1?id=&level=exhigh&cookie=`

### 数据层（`Data/*`）

| 组件 | 职责 |
|------|------|
| `NcmAuthClient` | 登录态、二维码 / 短信相关接口 |
| `NcmUserClient` | `/user/playlist`、`/playlist/detail`、`/playlist/track/all`、`/song/url`、`/song/url/v1` |
| `NcmLibraryJson` / `NcmPlaybackParse` / `NcmJson` | 解析 |
| `SessionStore` | Cookie / 标签 DPAPI 持久化 |
| `LikedPlaylistCache` | 心形歌单进程内缓存；Splash / 主窗预取 |

喜欢歌单链路：

1. `/user/playlist` 找本人心形单（`specialType==5` 或名「我喜欢的音乐」）
2. `/playlist/detail` 头图与描述
3. `/playlist/track/all` 曲目列表

### 配置

- `Config/NcmApiConfig.cs`
- 默认：`http://120.27.244.170:3000`
- 覆盖：环境变量 `ZMUSIC_NCM_API_BASE_URL`，或运行期 `SetRuntimeBaseUrl`

---

## 4. UI / UX 约定（Windows）

- **壳**：深色 Acrylic；侧栏简洁文案；喜欢页自带歌单头图，隐藏壳层重复标题
- **喜欢**：冷启动骨架闪烁；列表高亮当前曲（墨水指示，非彩条喧宾）
- **迷你条**：半透明玻璃（对齐用户卡片语言）；黑白灰墨水体系；播放键为白底黑标圆形；进度只读通栏
- **不要求**与 Android 全屏播放器视觉一致

---

## 5. 目录速查

```
Windows/
  App.xaml(.cs)          启动、主题、Splash → Login/Main
  AppServices.cs         进程内服务单例
  MainWindow.*           主壳
  LoginWindow.* / SplashWindow.*
  Config/                API 基址
  Data/                  鉴权、会话、库、解析、喜欢缓存
  Playback/              Bridge / Coordinator / 模式
  ViewModels/
  Views/Pages/           推荐 · 歌单 · 喜欢 · 设置
  Views/Controls/        MiniPlayerBar · UserAccountCard · UrlImage
  Assets/                图标
```

---

## 6. 本地运行

```bash
dotnet run --project Windows/ZMusic.csproj
```

```powershell
$env:ZMUSIC_NCM_API_BASE_URL = "http://127.0.0.1:3000"
dotnet run --project Windows/ZMusic.csproj
```

构建产物与用户文件见根目录 [`.gitignore`](../.gitignore)。

---

## 7. 已实现 / 未实现（对照 Android）

| 能力 | Windows | Android |
|------|:---:|:---:|
| Splash + 会话恢复 | ✅ | ✅ |
| 二维码 / 手机登录 | ✅ | ✅ |
| 主壳导航 | ✅ | ✅ |
| 喜欢歌单浏览 + 预热 | ✅ | ✅ |
| 点播 + 三模式 + 迷你条 | ✅ | ✅ |
| 队列磁盘快照恢复 | ❌ | ✅ |
| 全屏播放器 / 歌词 | ❌ | ✅ |
| 系统媒体通知 / 前台服务 | ❌（桌面进程内播放） | ✅ |
| 推荐 / 歌单页内容 | 占位 | ✅ |
| 应用内改 API 地址 UI | ❌（环境变量可改） | ✅ |

---

## 8. 后续建议

- 队列 / 进度快照（对齐 `PlaybackStateStore`）
- 上一首 / 下一首显式控件
- 推荐与歌单页接真实接口
- 可选：NAudio 频谱或更稳的流媒体栈（若 `MediaPlayer` 遇 CDN 限制）
