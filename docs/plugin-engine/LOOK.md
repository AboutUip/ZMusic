# 外观（`look`）

可选能力（展示用）。插件在运行时通过 **`Xuan.look`** 覆盖宿主 **非危险** 的个性化：浅深色、液态玻璃、主界面壁纸、播放页黑胶 / 氛围 / 自定义背景、个人页背景图。色 token 仍走 [THEME.md](./THEME.md)；歌单砖布置仍走 [UI.md](./UI.md) 的陈列。

建议在 `plugin.json` 写出，供工坊把包装成主题包：

```json
"capabilities": ["look", "theme"]
```

引擎 `0.1.0` 起，未声明也可以调用；仅须已成功注册 `Running`。

不写入用户设置。会话结束（禁用、卸载、`Error`、离线停会话、引擎 stop）时，该插件作为 owner 的区全部撤销，用户原设置回来。

位图只读 **本插件包内** 文件（规则与 [PACK.md](./PACK.md) 相同）。不能用 http、不能读用户相册、不能交 SVG（宿主按位图解码）。允许 `.png` `.jpg` `.jpeg` `.webp` `.gif` `.bmp`。

## 所有权

按区一份 overlay。后一次成功的 `set` 成为该区 owner，并与该区已有值 **按字段合并**。`clear(region?)` 仅 owner。省略 `region` 时清本插件全部区。

未知区名、未知字段、空对象、缺图、非法数字使 **整次失败**，不部分写入。

## 区名

| 区 | 覆盖什么 |
|---|---|
| `appearance` | 浅色 / 深色 / 跟随系统。不改设置里的选项，只改实际绘制 |
| `chrome.glass` | 液态 / 磨砂 / 纯色，折射率，模糊。Dock、迷你条、弹窗、岛 |
| `chrome.wallpaper` | 主壳页背景图、覆盖面、条目铬（纯色/磨砂/液态） |
| `player.vinyl` | 播放页黑胶盘色（黑 / 金 / 白 / 自定义） |
| `player.atmosphere` | 横屏雨夜、活跃光晕 |
| `player.background` | 竖屏播放页自定义背景图 |
| `library.profile` | 个人页大图背景（铺了 chrome 壁纸「个人」时仍以壁纸为准） |

不做：账号 / 网络 / 下载 / 音质、歌词点选自动播放、屏幕常亮、桌面歌词浮层、黑胶手势与选歌、预测性返回、改 Dock 标签。

## `Xuan.look.set(region, spec)`

返回 `true` / `false`。

### `appearance`

`{ mode: "light" | "dark" | "system" }`

### `chrome.glass`

| 字段 | 说明 |
|---|---|
| `mode` | `liquid` / `frost` / `solid` |
| `refraction` | 0～2 |
| `blur` | 0～1 |

### `chrome.wallpaper`

| 字段 | 说明 |
|---|---|
| `enabled` | 布尔。写了任一张图且未写 `enabled` 时视为开启 |
| `itemChrome` | `liquid` / `frost` / `solid`。铺图时列表条目的铬 |
| `coverage` | 非空字符串数组。未写且带了图时铺 **全部** 下列面 |
| `generic` | `{ portrait?, landscape? }` 通用帧 |
| `home` `features` `profile` `search` `settings` `playlist` `album` `artist` | 分面帧，同样 `{ portrait?, landscape? }` |

`coverage` 取值即上表分面名。

帧可以是包内相对路径字符串，或：

```javascript
{ pack: "wall/home.jpg", offsetX: 0.5, offsetY: 0.5, scale: 1 }
```

`offsetX` / `offsetY`：0～1。`scale`：0.2～8。省略则 0.5 / 0.5 / 1。路径必须存在且为位图。

`chrome.wallpaper` 的 `scale` **1（含省略）与用户在设置里选图相同：先铺满当前视口再略放大**，不是整张图完整塞进屏幕。小于 1 会露出更多画面（可能留边），大于 1 再放大。

`player.background` 的 `scale` 1（含省略）先铺满播放页（含底栏），再按 0.60～2.50 缩放。`offsetX` / `offsetY` 与壁纸相同：铺满后对齐裁切，不会把图移出屏幕露出底色。用户自己在播放页选的自定义背景仍是先完整放下再平移缩放。

### `player.vinyl`

与陈列黑胶相同：`style` 为 `black` / `gold` / `white` / `custom`。`custom` 必须同时给 `base`、`groove`（`#RRGGBB` / `#AARRGGBB`）。非 `custom` 不得带这两项。

### `player.atmosphere`

`{ rainNight?: boolean, halo?: boolean }`

竖屏自定义背景开启（用户或本 overlay）时，光晕仍按宿主规则关掉。

### `player.background` / `library.profile`

`{ pack: "bg.jpg", offsetX?, offsetY?, scale? }`。播放页 `scale` 为 0.60～2.50；个人页与壁纸相同（0.2～8）。

## 其它

| 调用 | 说明 |
|---|---|
| `look.clear(region?)` | 清 overlay。非 owner / 未知区 / 该区无 overlay 则失败 |
| `look.get()` | 全部已知区的当前描述（默认 ∪ overlay）。未 `Running` 时 `{}` |

未覆盖的区 `get()` 仍给出默认描述；原样 `set` 回去会变成 overlay。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.theme.set({ "face.page": "#0E1014", "text.title": "#F5F7FA" });
Xuan.look.set("appearance", { mode: "dark" });
Xuan.look.set("chrome.glass", { mode: "frost", blur: 0.7 });
Xuan.look.set("chrome.wallpaper", {
  itemChrome: "frost",
  coverage: ["home", "features", "profile", "search", "settings", "playlist", "album", "artist"],
  generic: { portrait: "wall/p.jpg", landscape: "wall/l.jpg" }
});
Xuan.look.set("player.vinyl", { style: "gold" });
```
