# 文本主题（`theme`）

可选能力。插件在运行时通过 **`Xuan.theme`** 覆盖宿主**文本色** token。不读包内主题文件；面色 / 玻璃 / 壁纸不在本能力范围内。

须在 `plugin.json` 声明：

```json
"capabilities": ["theme"]
```

未声明时宿主仍注入 `Xuan.theme`，但调用一律失败（返回 `false` / 空对象规则见下）。仅在已成功注册 `Running` 之后可用。

## 所有权

- 全局至多一份 **overlay**；`set` 成功的插件成为 **owner**。
- 后一次成功的 `set` 覆盖所有权，并与现有 overlay **按 key 合并**（新值覆盖同名 key）。
- `clear` 仅 **owner** 可清空全部 overlay；非 owner 调用失败。
- 插件会话结束（禁用、卸载、`Error`、离线停会话、引擎 stop）时：若该插件是 owner，宿主自动 `clear`。

浅色 / 深色切换时，未覆盖的 token 跟随宿主默认；已覆盖的 key 保持插件值。

## 颜色格式

字符串：`#RRGGBB` 或 `#AARRGGBB`（`#` 可省略）。大小写不敏感。

单次 `set` **要么全部生效，要么全部失败**（未知 key、非法颜色、空对象均失败，不部分写入）。

## Token 表

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

不在上表的 key 视为未知。歌词**用户样式**编辑器颜色、非文本色板不在本能力内。

## API

详见 [RUNTIME.md](./RUNTIME.md) 中 `Xuan.theme`。摘要：

| 调用 | 成功 | 失败 |
|---|---|---|
| `Xuan.theme.set( partial )` | `true`，合并 overlay 并成为 owner | `false`（未 Running、无能力、参数非法） |
| `Xuan.theme.clear()` | `true`，清空 overlay | `false`（非 owner / 未 Running / 无能力） |
| `Xuan.theme.get()` | 当前**生效**色（默认 ∪ overlay），字符串 map | 无能力或未 Running 时返回空对象 `{}` |

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.theme.set({
  "text.dock.active": "#EC4141",
  "text.dock.inactive": "#8E8E93",
  "text.title": "#1C1C1E"
});

var cur = Xuan.theme.get();
Xuan.theme.clear();
```
