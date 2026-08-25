# 插件界面（`Xuan.ui`）

可选能力（展示用）。建议在清单写出 `"ui"`。引擎 `0.1.0` 起，未声明也可以调用；仅须已成功注册 `Running`。

由宿主用现有界面绘制。不是 HTML / WebView，也不能把 JS 跑在 UI 线程。插件提交 JSON 描述的槽位与组件树；点击与改值在宿主处理，事件回到 **本插件线程**。

失败返回 `false` 或 `null`，不抛异常、不结束插件。会话结束时摘掉该插件全部槽位、页面、弹窗与 sheet。

## 槽位（入口）

只能往 **具名槽位** 登记入口，不能改 Dock、不能替换内置功能卡片。未知槽位名忽略（调用失败）。

| 槽位 | 位置 |
|---|---|
| `features.grid` | 功能页「插件」分区，卡片样式与「每日推荐」相同 |
| `settings.rows` | 设置页「插件」分组中的一行 |

### `Xuan.ui.slot.set(slot, spec)`

`spec`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `string` | 必填。本插件内唯一，最长 64 |
| `title` | `string` | 必填。最长 80 |
| `subtitle` | `string` | 可选。最长 80 |
| `icon` | `string` | 可选。宿主图标名，见文末 |
| `accent` | `string` | 可选。`#RRGGBB` / `#AARRGGBB` |
| `page` | `string` | 可选。点击后打开的页面名 |

同一 `slot`+`id` 再次 `set` 为替换。每插件最多 16 条入口。

### 其它

| 调用 | 说明 |
|---|---|
| `slot.remove(slot, id)` | 去掉这一条。没有也成功 |
| `slot.clear()` | 去掉本插件全部入口 |
| `slot.on(slot, fn)` | 该槽位的 `press` 事件。点卡片时：若写了 `page` 先打开该页，再投递 `{ type: "press", slot, id }` |

## 操作槽（宿主表面）

往 **具名表面** 增加菜单项。用于长按封面、曲目「更多」等。不是 Dock，也不能替换内置项。未知表面名忽略（调用失败）。

| 表面 | 位置 |
|---|---|
| `player.cover` | 播放页封面 / 黑胶 |
| `miniplayer.cover` | 迷你播放器封面 |
| `album.cover` | 专辑封面（详情头图、列表、搜索等） |
| `playlist.cover` | 歌单封面 |
| `artist.cover` | 歌手头像 / 封面 |
| `track.cover` | 曲目列表或推荐里的封面 |
| `track.overflow` | 曲目「更多」菜单，追加在宿主项之后 |
| `mv.cover` | MV 封面 |
| `chart.cover` | 排行榜封面 |
| `user.avatar` | 用户头像 |
| `home.banner` | 首页横幅 |

同一表面可被多处 UI 使用（例如首页歌单砖与歌单详情头图都是 `playlist.cover`）。`target.kind` 标明对象类型。

### `Xuan.ui.action.set(surface, spec)`

`spec`：

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `string` | 必填。本插件内、该表面下唯一，最长 64 |
| `title` | `string` | 必填。最长 80 |
| `icon` | `string` | 可选。宿主图标名 |
| `destructive` | `boolean` | 可选。默认 `false` |

同一 `surface`+`id` 再次 `set` 为替换。每插件最多 16 条（所有表面合计）。

### 其它

| 调用 | 说明 |
|---|---|
| `action.remove(surface, id)` | 去掉这一条。没有也成功 |
| `action.clear()` | 去掉本插件全部操作槽 |
| `action.on(surface, fn)` | 该表面的 `press`。点插件项时投递 `{ type: "press", surface, id, target }` |

宿主在长按某表面时：若有任一插件登记了该表面的操作，弹出 sheet（插件项；若该处原先就有宿主长按，则多一项宿主默认）。没有插件项时不改现有手势。`track.overflow` 把插件项追加进现有溢出菜单，不另开一层。

观察手势请用 [HOOK.md](./HOOK.md) 的 `ui.press` / `ui.longPress` / `ui.menu`，不必先 `action.set`。把图存到相册、分享、剪贴板见 [DEVICE.md](./DEVICE.md)。

```javascript
Xuan.ui.action.set("album.cover", { id: "info", title: "封面信息" });
Xuan.ui.action.on("album.cover", function(ev) {
  if (ev.target && ev.target.name) {
    Xuan.notice.show(String(ev.target.name));
  }
});
```

## 页面（组件树）

每插件可定义多份具名页。从槽位打开的页走主壳叠层（与设置、歌单详情相同的滑入与返回）。系统返回键只弹栈，插件不能拦截。宿主为内置探针提供的 Dock「调优」是主界面切页，不是叠层。

### 定义与补丁

| 调用 | 说明 |
|---|---|
| `page.define(name, spec)` | `spec.title`、`spec.root`（组件树）。`name` 非空，最长 64。最多 16 页 |
| `page.set(spec)` | 兼容写法：定义名为 `"default"` 的页。`spec.title` 必填；有 `root` 则用树，否则 `spec.body` 纯文本 |
| `page.get(name?)` | `{ name, title, root }`。省略 `name` 时取 `"default"`。没有则 `null` |
| `page.patch(name, spec)` | `spec.id` 必填，合并该节点的字段；若带 `children` 则替换子树 |
| `page.clear(name?)` | 去掉定义；正在显示则关闭。省略 `name` 时去掉本插件全部页 |

### 导航

| 调用 | 说明 |
|---|---|
| `page.open(name?, params?)` | 省略 `name` 时为 `"default"`。`params` 须 JSON 能表达。同一插件最多叠 **8** 层。实例键：`params.id`（字符串化）或参数的稳定摘要 |
| `page.back()` | 若当前顶层是本插件的页则弹出，否则失败 |
| `page.on(name, fn)` | 该页事件。同一 `name` 再次登记为替换 |

### 事件（`fn` 的一个对象参数）

| `type` | 其它字段 |
|---|---|
| `press` | `page`、`id` |
| `change` | `page`、`id`、`value` |
| `open` | `page`、`params` |
| `leave` | `page`、`params` |

开关、滑条、输入、分段在手指操作时，宿主先改树上的 `value`，再发 `change`。进度类更新请用 `patch`，不要整页 `define`。

## 组件树

根必须是对象，含 `type`。可选 `id`（本页内唯一，供事件与 `patch`）。未知 `type` 使整次 `define` / `set` 失败。

限制：最多 200 个节点、深度 12、单个 `text`/`body` 最长 8192（兼容 `page.set` 的 `body` 最长 32768）、每个节点最多 48 个子节点。

### 样式（可选）

未写则用宿主默认。非法值使该次 `define` / `patch` 失败。

| 字段 | 用于 | 说明 |
|---|---|---|
| `gap` | `column` / `row` / `scroll` / `section` | 子项间距，0～48，单位 dp。默认 10（`section` 默认 8） |
| `pad` / `padH` / `padV` | 布局与多数控件 | 内边距 0～48。`padH` / `padV` 覆盖对应轴 |
| `align` | `column` / `scroll` / `section` | 横轴：`start`（默认）/ `center` / `end` |
| `align` | `row` | 纵轴：`start` / `center`（默认）/ `end` |
| `align` | `text` | `start`（默认）/ `center` / `end` |
| `flex` | `row` 的子节点 | 省略则均分剩余空间；`0` 按内容；`1`～`8` 为权重 |
| `color` | `text` / `button` / `section` 标题 | [THEME.md](./THEME.md) 全名（`text.accent`、`face.page`、`control.thumb`、`player.stage` 等）；或省略 `text.` 的文本族写法（`accent`、`dock.active`）；或 `#RRGGBB` / `#AARRGGBB` |
| `size` | `text` / `button` | 字号 10～32 |
| `weight` | `text` / `button` | `regular` `medium` `semibold` `bold` |
| `width` | `button` | `fill`（默认）/ `hug` |
| `radius` | `button` / `image` | 圆角 0～48 |
| `height` | `image` | 40～400，默认 160。`spacer` 仍为 1～200 |
| `fit` | `image` | `cover`（默认）/ `contain` |
| `icon` | `button` | 宿主图标名，见文末 |
| `step` | `slider` | 步进，大于 0 |
| `multiline` | `field` | 多行输入 |

### 布局

| `type` | 字段 |
|---|---|
| `column` / `row` | `children` |
| `scroll` | `children`（纵向） |
| `spacer` | `height`：1～200，单位 dp |
| `section` | `title`（可选）、`children` |
| `tabs` | `value`（当前 tab 的 `id`）、`children` 为 `tab` |
| `tab` | `id`、`label`、`children` |

### 展示

| `type` | 字段 |
|---|---|
| `text` | `text`；`style`：`title` / `body` / `subtitle` / `meta` / `hint`（默认 `body`） |
| `image` | `src`：`http(s)` URL 或包内相对路径 |
| `empty` | `text` |
| `loading` | 无字段；显示转圈 |

### 控件

| `type` | 字段 | 事件 |
|---|---|---|
| `button` | `label`；`role`：`primary` / `destructive` / 省略；`enabled` | `press` |
| `toggle` | `value` 布尔 | `change` |
| `slider` | `value`、`min`、`max`（数字）；可选 `label`、`step` | `change` |
| `field` | `value` 字符串；可选 `label`、`placeholder`、`multiline` | `change` |
| `segmented` | `value`（选中项 `id`）；`children` 为 `option` | `change` |
| `option` | `id`、`label` | — |

### 列表

| `type` | 字段 | 事件 |
|---|---|---|
| `list` | `children` 为 `item` 或 `track` | — |
| `item` | `title`、`subtitle`、`trailing`（文案） | `press` |
| `track` | `title`、`subtitle`、`coverUrl`、`durationMs` | `press` |

## 弹窗与 sheet

`Xuan.ui.alert(spec, fn?)` 规则与此前相同（玻璃确认框，全局一条）。

`Xuan.ui.sheet(spec, fn?)`：底部操作表。`spec.title` 必填；`spec.message` 可选；`spec.actions` 为 `{ id, label, destructive? }[]`，1～8 项。回调参数为所点 `id`，关闭为 `"dismiss"`。已有 sheet 或故障弹窗时失败。

## 图标名

`home` `features` `profile` `search` `settings` `favorite` `timer` `radio` `daily` `charts` `folder` `workshop` `extension` `music` `playlist` `info` `add` `check` `history` `headset` `moon`（同 `timer`）。未知名用 `extension`。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.ui.page.define("home", {
  title: "助眠",
  root: {
    type: "column",
    children: [
      { type: "text", text: "到点暂停", style: "subtitle" },
      { type: "slider", id: "mins", min: 5, max: 120, step: 5, value: 30, label: "分钟" },
      { type: "field", id: "note", label: "备注", placeholder: "可选说明" },
      { type: "button", id: "start", label: "开始", role: "primary" }
    ]
  }
});

Xuan.ui.slot.set("features.grid", {
  id: "sleep",
  title: "助眠",
  subtitle: "定时停止",
  icon: "timer",
  page: "home"
});

Xuan.ui.page.on("home", function(ev) {
  if (ev.type === "press" && ev.id === "start") {
    Xuan.player.pause();
  }
});
```
