# Linux Architecture

本文是 ZMusic **Linux 客户端的终局规格**，不是过渡草案。栈、打包、玻璃引擎、播控一旦按本文落地，不因「先跑起来」再换一套。

**与 Android 的关系**：Linux **只复刻 Android 横屏模式**（壳、播放器、设置、主链路行为）。不复刻竖屏，不跟 Windows UI。Windows 仍是独立桌面端，行为可对齐、界面不对齐。

**与插件的关系**：Linux **不做插件引擎、不做创意工坊 / 插件市场**。摘除的都是附属能力；听歌主链路与横屏播放器不在此列。

---

## 1. 终局技术栈

| 层 | 选型 | 为什么这是终局而不是将就 |
|---|---|---|
| UI | Kotlin · **Compose Multiplatform Desktop** · Skia | 与 Android 同一套布局/动效语言，横屏 Composable 可按文件对照；不是 WebView |
| 玻璃 | 本端 **Skia RuntimeEffect** 液体玻璃（折射率 / 模糊与设置项对齐） | 桌面没有 Kyant Backdrop。这是 Linux 的玻璃引擎，不指望以后把 Android 库搬过来再换 |
| 播放 | **libmpv**（进程内） | 桌面没有 Media3；mpv 是 Linux 听歌/MV 的终局解码器 |
| 系统播控 | **MPRIS2**（D-Bus） | 对等 Android 的 MediaSession：媒体键、GNOME/KDE 媒体控件 |
| 网络 | OkHttp + 同一套 `Ncm*Parse` 语义；社区目录 XAIOP Java SDK | 与 Android 共用 JVM 能力，不引入第三套 JSON |
| 状态 | `StateFlow` + 薄 ViewModel + `PlaybackBridge` | 对齐 Android 分层，禁止 UI 直接 HTTP |
| 会话 | 用户目录加密文件（AES-GCM，密钥走 libsecret，否则本机密钥文件） | 对等 EncryptedSharedPreferences，不把 Cookie 明文扔在磁盘 |
| 运行时 | **jlink 裁剪 JRE**（Temurin 21）打进 deb | 用户不必先装 Java；依赖面由我们锁死 |
| 产物 | `amd64` `.deb`，文件名 `ZMusic-Linux-<version>.deb` | 硬门槛：`sudo apt install ./ZMusic-Linux-0.1.deb` |

禁止：Electron / Tauri / WebView、GTK/libadwaita 当主 UI、Qt 重译界面、用 Waydroid 跑 APK、依赖发行版全量 JDK。

---

## 2. 产品形态

- **永远是横屏应用**：默认窗口约 `1280×800`，最小宽度保证左侧 rail + 内容区不塌成竖屏布局。不实现 `Portrait*`。
- **内容区 1:1 横屏壳**：`LandscapeNavRail` + 三栏（主页 / 功能 / 个人）+ overlay 栈 + 横屏播放器 + 迷你条 + 灵动岛。
- **窗口边框交给窗口管理器**（GNOME/KDE 标题栏）。不在应用里仿 Android 状态栏。这是桌面正确做法。
- 显示协议：X11 与 XWayland 必须满功能；Wayland 原生作为同栈增强，不为它改 UI 架构。
- glibc 基线：**Ubuntu 22.04 amd64**（2.35）。在 22.04 上构建，24.04 可装可跑。禁止在更新的 glibc 上编完再宣称兼容 22.04。

---

## 3. 功能取舍

原则：只摘 **附属** 能力。横屏播放、听歌主链路、设置里与听歌/外观相关的项，必须做。

### 3.1 必须（0.1 合格线含这些）

**壳与导航**

- 左侧横屏 rail：主页 / 功能 / 个人 / 设置
- overlay 栈（水平盖入，语义同 Android `MainOverlay`，无插件页）
- 迷你条、全屏横屏播放器进出
- 应用内灵动岛（软件内提示只走岛，不用系统气泡替代岛）

**账号与启动**

- Splash → 连通性 → 恢复会话或横屏登录
- 登录：二维码（本机显示，手机扫）+ 验证码；密码登录若 Android 横屏仍提供则跟上
- 退出登录

**听歌**

- 主页推荐流、横屏点播
- 功能：每日推荐、排行榜、缓存的歌曲、私人 FM、心动模式
- 歌单 / 专辑 / 歌手 / 用户页 / 喜欢的歌手 / 搜索
- 队列、三种播放模式（列表循环 / 单曲循环 / 随机）、音质档位与回退链（与 `AudioQuality` 一致）
- **横屏播放器**：唱片、投影歌词、歌词滚动/浏览态、歌词样式编辑、唱片配色、切歌拾取、选句；进度与模式
- 歌词：按行；设置开启后若有逐字数据则按字
- 下载加速（命中本机已导出曲目则直接播文件）
- 实时缓存
- 持续播放（与其它程序同时出声；MV 起播仍停歌曲）
- MV（libmpv 视频，与歌曲互斥）
- MPRIS：播放/暂停/切歌/进度/封面元数据

**设置（能做的都做，仅删除无桌面语义的项）**

| 分组 | 要做 |
|---|---|
| 连接 | 音乐服务器、社区服务器（更新日志 / 赞赏 / 赞助商仍走社区目录） |
| 播放 | 音源默认质量、持续播放、逐字歌词 |
| 缓存 | 下载加速、实时缓存 |
| 主题 | 外观（浅/深）、液态玻璃样式（折射率 / 模糊）、自定义背景（横屏槽位） |
| ZMusic | 关于、更新日志、赞赏、赞助名单、赞助商、条款与隐私 |
| 账号 | 退出登录 |

### 3.2 明确摘除（附属 / 无桌面语义）

| 摘除 | 理由 |
|---|---|
| 插件引擎、`.zpp`、Xuan、探针「调优」页 | 用户允许；整座附属子系统 |
| 创意工坊 / 社区扫码领 `app_token` | 插件市场 |
| 设置：插件、插件引擎调试 | 同上 |
| 设置：预测性返回 | Android 手势 |
| 设置：横屏模式（旋转按钮 / 会话锁） | Linux 没有传感器旋转；窗口即横屏 |
| 设置：权限（通知 / 悬浮窗 / 相机 / 附近的设备 / 安装应用） | Android 权限模型；媒体键走 MPRIS，不单独做权限页 |
| 应用外歌词悬浮窗 | Android `SYSTEM_ALERT_WINDOW` |
| 应用内 APK 更新 / 测试计划 | 桌面用 deb / GitHub；测试计划是 Android 渠道 |
| 蓝牙音频 / 充电灵动岛 | 换用 PipeWire 设备变化时可后期再加岛提示，不进 0.1 合格线 |
| 竖屏播放器、竖屏登录、Dock | 非横屏 |
| 横屏播放器里的评论 | Android 横屏播放器本身没有评论，1:1 不补 |

社区登录扫码、工坊门槛：**不做**。首页扫码按钮若只为社区工坊，则不出现。

---

## 4. 目录

```
Linux/                         # Compose Desktop 应用（源码）
  src/main/kotlin/...          # ui / playback / desktop 适配
  src/test/kotlin/...          # JVM 单测（强制）
  src/desktopTest/kotlin/...   # Compose UI 测
Distribution/Linux/            # 打 deb，与业务源码隔离（对齐 Windows 分发）
  pack.py / build-deb.sh / control / desktop / icons
shared-jvm/                    # 可选但方向锁定：无 Android API 的解析与模型
                               # Linux 0.1 可以先把文件放在 Linux/ 内，
                               # 稳定后的唯一归宿是 shared-jvm，禁止再发明第三套解析
artifacts/linux/               # gitignore；产物 ZMusic-Linux-*.deb
```

在仓库根目录打 amd64 `.deb`（Ubuntu / Debian / Kali）：

```bash
bash Distribution/Linux/build-deb.sh --install-deps
sudo apt install ./artifacts/linux/ZMusic-Linux-0.1.deb
```

脚本会定位 JDK 21（`JAVA_HOME`、`/usr/lib/jvm`、或下载 Temurin 到 `.tools/jdk-21`），用 **Linux jlink** 打进运行时，并写入桌面项。不要在 Windows 上跑 `pack.py` 指望得到能在 Kali 执行的 JRE。

社区目录 SDK **不走 Maven Central**（上面没有 `io.github.aboutuip:xaiop`）。官方 JAR 钉在 `Linux/libs/xaiop-0.15.1.jar`；`build-deb.sh` 缺文件时从 GitHub Releases 拉取。Gradle 用 `files()` 引用，不要对该 group 做 `exclusiveContent`。

分层（禁止打穿）：

```
ui (Compose) → ViewModel → repository → Ncm*Parse
                              ↘ NcmUserClient / NcmAuthClient
                 ViewModel → PlaybackBridge → PlaylistCoordinator → libmpv
                                                          ↘ MprisExporter
```

单文件尽量小于 500 行，与 Android 约定相同。

---

## 5. 玻璃与视觉

- 液体玻璃在 Linux 上用 Skia 着色器实现：**同窗采样 + 模糊 + 折射率**。设置里的「液态玻璃样式」绑定这套参数，不是 CSS 半透明蒙层。
- 灵动岛、确认弹窗、rail/卡片与 Android 横屏同一套色板（`MainPalette` 语义）、圆角、动效时长（320ms 盖入等）。
- 允许与手机截图像素有差（采样半径、显示器色域）；不允许布局结构、字号层级、横屏播放器几何关系明显走样。
- 自定义背景只做 **横屏槽位**。

---

## 6. 播放

1. UI `playQueue` → `PlaybackBridge`
2. `PlaylistCoordinator` 解析 URL（先 `/song/url/v1` 与音质回退链，再 legacy `/song/url`，与 Android `PlayUrlResolver` 一致）
3. 把可播地址交给 libmpv；进度/结束事件回写 Bridge
4. 结束前约 30s 预取下一首（TTL ≤ 2min）；切歌仍以新鲜 resolve 为准
5. 歌词异步，不阻塞起播
6. MPRIS 随 Bridge 更新；媒体键进入同一 Coordinator
7. `musicWillPlay` 停 MV；MV 起播停歌曲

libmpv **随包提供**（`/opt/zmusic/lib/native`），不要把「用户先 `apt install libmpv2`」当成主路径。动态链接发行版 so 可作为额外优化，不是合格条件。

---

## 7. 打包（合法 deb）

文件名：`ZMusic-Linux-0.1.deb`（版本随 `versionName`）。

包内（Debian 策略）：

| 字段 | 值 |
|---|---|
| Package | `zmusic` |
| Architecture | `amd64` |
| Version | `0.1-1`（随后 `upstream-revision`） |
| Section | `sound` |
| Depends | 尽量短：`libc6`（≥ 2.35）、常见 X11/GL/ALSA；**不** Depend 发行版 `openjdk-*` |
| Installed-Size / Maintainer / Description | 必填 |

布局：

```
/opt/zmusic/                 运行时 + 应用 + native
/usr/bin/zmusic              包装脚本 → /opt/zmusic/bin/zmusic
/usr/share/applications/zmusic.desktop
/usr/share/icons/hicolor/{48,64,128,256}x{…}/apps/zmusic.png
/usr/share/pixmaps/zmusic.png
/usr/share/menu/zmusic       Debian/Kali 应用程序菜单
/usr/share/doc/zmusic/copyright   GPL-2.0 + 第三方（JRE、libmpv、Skia）
```

`postinst` 必须调用 `update-desktop-database`、`gtk-update-icon-cache`、`update-menus`（若存在），否则 Kali / XFCE「应用程序」窗口可能看不到图标。`Categories=AudioVideo;Audio;Player;GTK;`，中文名在 `Name[zh_CN]`。

`apt install ./ZMusic-Linux-0.1.deb` 必须在 **无网络** 的 Ubuntu 22.04 容器里成功（Depends 均已被镜像满足或包内自带）。这是发布门闩。 Kali 2026（XFCE / GNOME）装完后应出现在应用程序菜单的「影音 / 多媒体 / 声音」分组。

数据目录：`~/.local/share/zmusic/`（缓存、会话）、`~/.config/zmusic/`（偏好）。卸载 deb **默认不删**用户数据；与 Windows 卸载勾选清除分开。

---

## 8. 测试（发布门闩，不是事后补）

Android 端几乎没有自动化测试可借。Linux **从第一份可编译代码起**就要有测试；**没有测试的模块视为未完成**。deb 不出包，除非第 8.4 节通过。

### 8.1 JVM 单测（`Linux/src/test`，每次 CI）

至少覆盖：

- `Ncm*Parse`：歌单 / 歌曲 / 登录态 / 歌词；用仓库内固定 JSON 夹具
- `AudioQuality.fallbacks()` 与默认档
- 播放模式循环：`ORDER → REPEAT_ONE → SHUFFLE → ORDER`
- `/song/url` 失败则 `/song/url/v1`（MockWebServer）
- LRC / 逐字分词
- overlay 栈 push/pop（纯 Kotlin，不调 UI）
- 会话读写：加密往返、坏文件不崩溃
- 社区 XAIOP 目录解析（更新日志 / 赞助，若 0.1 已接）

### 8.2 Compose UI 测（`src/test` + Xvfb）

固定窗口语义 `1280×800`（0.1 将 UI 测放在 `Linux/src/test`，CI 用 Xvfb；稳定后可再拆 `desktopTest`）：

- rail 三项目切换
- 打开 / 关闭设置 overlay
- 横屏播放器：播放键、模式按钮、返回
- 设置页：音质、外观、玻璃、逐字歌词开关真的写回 store
- 灵动岛：`show` 后出现文案

允许用测试用假 `PlaybackBridge`，不在 UI 测里真连网易云。

### 8.3 播放集成测

- 本地夹具音频文件：mpv 起停、seek、结束切下一首
- MPRIS：`dbus-send` 调 `PlayPause` / `Next`，断言 Coordinator 状态
- 持续播放开/关：用假「其它程序在播」钩子测是否让出 / 是否压低（按实现可测的部分写断言，测不到的写入手动清单）

### 8.4 包装测（打出 deb 后必须跑）

在 `ubuntu:22.04` 容器：

1. `apt-get install -y ./ZMusic-Linux-0.1.deb`
2. `dpkg -I` 检查 Architecture=amd64、Package=zmusic
3. `desktop-file-validate` 桌面项
4. `lintian` 无 error（warning 允许列表写在 `Distribution/Linux`）
5. `zmusic --version` 或等价入口非零即失败
6. Xvfb 下启动进程，数秒内不崩，然后退出

Debian bookworm amd64 **抽测安装**（CI weekly 或发版时），失败只报警不挡 Ubuntu 金标。

### 8.5 横屏对照（发版前人工 + 尽量自动）

- 同一套操作路径：登录 → 主页点播 → 进横屏播放器 → 改歌词样式 → 改设置音质再播
- 对照 Android 横屏截图：rail 宽度关系、播放器唱片与歌词列、设置分组顺序
- 自动：关键页截图哈希或尺寸断言（允许阈值）；不把「和手机 RGB 完全一致」当门闩

### 8.6 手动清单（`docs` 发版勾选，不替代 8.1–8.4）

- 媒体键、GNOME 媒体控件
- 浅色 / 深色 / 玻璃 / 背景
- 掉线、错误密码、过期会话
- `sudo apt remove zmusic` 后桌面项消失，用户数据仍在

---

## 9. 里程碑（同一架构上的切片）

不换栈。后面每一刀都是往同一份 Linux 里加横屏能力。

| 刀 | 内容 | 测试 |
|---|---|---|
| L0 | 工程、jlink、空窗口、deb 安装门闩 | 8.4 最小集 |
| L1 | 色板、rail、三栏空页、岛、设置壳 | 8.2 rail/设置 |
| L2 | 会话、横屏登录、服务器设置 | 8.1 会话 + Mock 登录 |
| L3 | Bridge + mpv + MPRIS + 迷你条 | 8.3 |
| L4 | 主页 / 喜欢或歌单点播 | 8.1 parse + Mock URL |
| L5 | **横屏播放器 + 歌词**（合格线的脸） | 8.2 播放器 + 8.5 |
| L6 | 功能页听歌入口、搜索、歌手、专辑 overlay | 单测夹具 + UI 冒烟 |
| L7 | 设置其余项、缓存、背景、玻璃参数、关于与目录 | 8.2 设置写回 |
| L8 | MV、持续播放细化、下载加速 / 实时缓存 | 8.3 + 手动 |

**0.1 发布**：L0–L5 必须完成；L6–L7 应完成「设置尽可能支持」——L7 未完成不得标 0.1。L8 可进 0.1.x，但 MV 互斥停歌在 L3 就要留接口。

---

## 10. CI

GitHub Actions，`ubuntu-22.04`：

1. `Linux` 单元测试 + desktopTest（Xvfb）
2. 打包 `ZMusic-Linux-<ver>.deb`
3. 容器内 `apt install ./…`
4. 产物上传 `artifacts/linux/`

无测试绿、无安装绿，不发版。

---

## 11. 许可

GPL-2.0。包内 `copyright` 列出：Temurin、libmpv（LGPL）、Skiko/Skia、OkHttp、XAIOP。不要捆绑 Oracle JRE。
