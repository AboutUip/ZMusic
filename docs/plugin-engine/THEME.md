# 主题（`theme`）

可选能力（展示用）。插件在运行时通过 **`Xuan.theme`** 覆盖宿主色 token：文本、面色、铬（Dock / sheet / 玻璃）、控件拇指、播放页舞台。不读包内主题文件；壁纸位图不在本能力内。

建议在 `plugin.json` 写出，供工坊展示：

```json
"capabilities": ["theme"]
```

引擎 `0.1.0` 起，未声明也可以调用；仅须已成功注册 `Running`。

`text.accent` 与 `face.accent` 相互独立：前者是字色强调，后者是滑条、开关、选中底、占位标签等填色。要一起变，两次都写。

## 所有权

- 全局至多一份 **overlay**；`set` 成功的插件成为 **owner**。
- 后一次成功的 `set` 覆盖所有权，并与现有 overlay **按 key 合并**（新值覆盖同名 key）。
- `clear` 仅 **owner** 可清空全部 overlay；非 owner 调用失败。
- 插件会话结束（禁用、卸载、`Error`、离线停会话、引擎 stop）时：若该插件是 owner，宿主自动 `clear`。

浅色 / 深色切换时，未覆盖的 token 跟随宿主默认；已覆盖的 key 保持插件值。

## 颜色格式

字符串：`#RRGGBB` 或 `#AARRGGBB`（`#` 可省略）。大小写不敏感。带 alpha 的色用于玻璃、卡片半透明、舞台进度底槽等。

单次 `set` **要么全部生效，要么全部失败**（未知 key、非法颜色、空对象均失败，不部分写入）。

## Token 表

不在下表的 key 视为未知。`Xuan.theme.get()` 返回表中**全部** key 的当前生效色（默认 ∪ overlay）。

### 文本 `text.*`

| Key | 默认来源 | 用途 |
|---|---|---|
| `text.title` | ink | 列表/页标题、设置标题 |
| `text.body` | ink | 正文 |
| `text.subtitle` | secondary | 副标题、描述 |
| `text.meta` | secondary | 元信息行 |
| `text.hint` | hint | 占位、chevron、空态、禁用文案 |
| `text.accent` | accent | 链接、选中强调、目录栏操作字色 |
| `text.destructive` | accent | 危险操作字色（默认同 accent，可单独改） |
| `text.dock.active` | ink | Dock 选中端（标签与图标） |
| `text.dock.inactive` | secondary | Dock 未选中端 |
| `text.pageHeader` | ink | 主页顶栏标题 |
| `text.catalogTitle` | ink | 目录顶栏标题 |
| `text.catalogAction` | accent | 目录顶栏操作 |
| `text.miniPlayer.title` | ink | 迷你播放器标题 |
| `text.miniPlayer.subtitle` | secondary | 迷你播放器副标题 |
| `text.miniPlayer.icon` | ink | 迷你播放器控件 |
| `text.island` | ink | 灵动岛通知字色 |
| `text.player.transport` | `#B8C5D4` | 播放页舞台控件 |
| `text.player.transportLocked` | `#7A8796` | 舞台锁定态控件 |
| `text.player.time` | `#E8EEF5` | 舞台时间 |
| `text.onPhoto.title` | 白 | 资料卡贴图模式标题 |
| `text.onPhoto.subtitle` | 白 @ 0.88 | 资料卡贴图模式副文 |
| `text.onPhoto.meta` | 白 @ 0.62 | 资料卡贴图模式元信息 |

### 面色 `face.*`

宿主 `MainPalette` 的填色全部走这些 key。首页、设置、歌单、搜索、插件页等已有调用点会一起变。

| Key | 默认来源 | 用途 |
|---|---|---|
| `face.page` | page | 页面画布 |
| `face.surface` | surface | 抬升面、实心底 |
| `face.accent` | accent | 滑条已播段、开关开启槽、选中底、占位黑胶标签、登录/启动主色 |
| `face.hairline` | hairline | 细线、迷你条进度底槽 |
| `face.card` | card | 卡片、列表行玻璃底 |
| `face.placeholder` | placeholder | 封面/头像未加载底 |
| `face.trackOff` | trackOff | 滑条未激活段、开关关闭槽 |

### 铬 `chrome.*`

| Key | 默认来源 | 用途 |
|---|---|---|
| `chrome.dockGlass` | dockGlass | Dock / 侧栏玻璃 |
| `chrome.dockStroke` | dockStroke | Dock 描边 |
| `chrome.sheetTint` | sheetTint | 底栏 sheet 色罩 |
| `chrome.sheetWash` | sheetWash | sheet 洗色 |
| `chrome.glassFill` | 公式 @ 0.62 | 见下 |

未覆盖 `chrome.glassFill` 时，宿主按透明度参数计算白膜（各处 alpha 不同）。覆盖后，**所有** `glassFill` 调用返回该色（把透明度写进 `#AARRGGBB`）。`get()` 在未覆盖时给出公式在参考透明度 0.62 下的色值，便于对照。

### 控件 `control.*`

| Key | 默认 | 用途 |
|---|---|---|
| `control.thumb` | 白 | 开关拇指（滑条拇指走 `face.accent`） |

### 播放页舞台 `player.*`

舞台默认不跟浅深 ink 走。覆盖后播放页进度、播放钮与舞台底一起变。

| Key | 默认 | 用途 |
|---|---|---|
| `player.stage` | `#05070C` | 播放页不透明底 |
| `player.progress.thumb` | `#E8EEF5` | 舞台进度拇指 |
| `player.progress.active` | `#D5DEE8` @ 0.9 | 舞台进度已播段 |
| `player.progress.off` | 白 @ 0.14 | 舞台进度未播段 |
| `player.playFill` | 白 @ 0.12 | 播放钮圆形底 |
| `player.playIcon` | `#F5F7FA` | 播放/暂停图标 |

## 不在本能力内

- 壁纸位图（用户在设置里选的 chrome 壁纸）
- 歌词**用户样式**编辑器、桌面歌词浮层调色
- 黑胶盘用户配色（黑 / 金 / 白 / 自定义）
- 评论点赞等插画色
- 功能页听歌模式卡片上的独立身份色（例如漫游蓝）

## 插件树里的 `color`

`text` / `button` / `section` 标题的 `color` 可以是：

- 上表**全名**（任意族）：`text.accent`、`face.page`、`chrome.dockGlass`、`control.thumb`、`player.stage`
- 省略 `text.` 的文本族写法：`accent`、`dock.active`、`miniPlayer.title`、`player.transport` 等同对应 `text.*`
- `#RRGGBB` / `#AARRGGBB`

`player.stage` 等非文本族没有简称，必须写全名。绘制时未识别的 token 回退为该处默认色；`color` 不是字符串或过长仍使该次 `define` / `patch` 失败，见 [UI.md](./UI.md)。

## API

详见 [RUNTIME.md](./RUNTIME.md) 中 `Xuan.theme`。摘要：

| 调用 | 成功 | 失败 |
|---|---|---|
| `Xuan.theme.set( partial )` | `true`，合并 overlay 并成为 owner | `false`（未 Running、参数非法） |
| `Xuan.theme.clear()` | `true`，清空 overlay | `false`（非 owner / 未 Running） |
| `Xuan.theme.get()` | 当前**生效**色（默认 ∪ overlay），字符串 map，含全部 token | 未 Running 时返回空对象 `{}` |

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.theme.set({
  "text.dock.active": "#EC4141",
  "text.accent": "#EC4141",
  "face.accent": "#EC4141",
  "face.page": "#F6F7F9"
});

var cur = Xuan.theme.get();
Xuan.theme.clear();
```
