# 播放页样式（`style`）

可选能力（展示用）。插件在运行时通过 **`Xuan.style`** 按画面、按属性覆盖播放页和桌面歌词的绘制。色 token 仍走 [THEME.md](./THEME.md)。壁纸、玻璃、浅深色、黑胶盘色的另一条入口仍走 [LOOK.md](./LOOK.md)。

建议在 `plugin.json` 写出：

```json
"capabilities": ["style"]
```

引擎 `0.2.0` 起，未声明也可以调用；仅须已成功注册 `Running`。

不写入用户设置。设置页编辑器读的是用户自己的偏好。会话结束（禁用、卸载、`Error`、离线停会话、引擎 stop）时，该插件作为 owner 的画面全部撤销。

四套画面分开，互不覆盖：

| 画面 | 画在哪 |
|---|---|
| `focus` | 横屏专注页：歌词、歌名、黑胶几何、播放条、弹幕 |
| `portrait` | 竖屏播放页：歌词、预览歌词、黑胶几何、控件偏移、歌词页磨砂 |
| `dynamic` | 横屏动态页：深色歌词、封面列偏移、封面玻璃。不改专注页歌词 |
| `overlay` | 桌面歌词悬浮窗的字色、字号、行数、对齐、窗宽。不改开关、锁定和位置 |

## 所有权

每个画面一份 overlay。后一次成功的 `set` 成为该画面 owner，并与该画面已有值 **按字段合并**。`clear` 仅 owner。

未知画面、未知字段、空对象、类型不对、预设色和自定义盘色互相打架，使 **整次失败**，不部分写入。超出范围的数字会收进播放页设置同一套上下限，`get` 返回收过的值。

颜色是 `#RRGGBB` 或 `#AARRGGBB`。歌词和标题的 `color` 只改这一项：字重、斜体、字号保持原样，除非同一次或之后的调用写了它们。

## `Xuan.style.set(surface, partial)`

返回 `true` / `false`。

```javascript
Xuan.style.set("focus", {
  lyric: {
    playing: { color: "#F8FAFC", bold: true, italic: false, fontScale: 1.1 },
    played: { italic: true },
    unplayed: { color: "#DCE6F0" },
    lineSpacing: 12,
    playedCount: 2,
    upcomingCount: 2,
    offsetX: 0,
    avoidVinyl: false
  },
  title: {
    align: "vinyl",
    offsetY: 0,
    name: { color: "#F5F7FA", fontScale: 1 },
    source: { color: "#667A8899", fontScale: 1 },
    artist: { fontScale: 1 }
  }
});
```

横屏歌手行读 `title.source`。`title.artist` 是制作人样式位，编辑器会保存它。

### `focus`

| 路径 | 类型 | 范围 |
|---|---|---|
| `lyric.playing` / `played` / `unplayed` | 对象 | `italic`、`bold` 布尔；`color` 颜色；`fontScale` `0.75`–`1.50` |
| `lyric.lineSpacing` | 数字 | `0`–`28` dp |
| `lyric.playedCount` / `upcomingCount` | 整数 | `0`–`3` |
| `lyric.offsetX` | 数字 | `-72`–`72` dp |
| `lyric.avoidVinyl` | 布尔 | 歌词宽度避开黑胶，左右对称 |
| `title.align` | 字符串 | `left` `vinyl` `center` `lyrics` |
| `title.offsetY` | 数字 | `-40`–`72` dp |
| `title.name` / `artist` / `source` | 对象 | `color`、`fontScale` `0.75`–`1.50` |
| `vinyl.offsetX` | 数字 | `-56`–`56` dp |
| `vinyl.offsetY` | 数字 | `-84`–`84` dp |
| `vinyl.absoluteCenter` | 布尔 | 垂直绝对居中 |
| `vinyl.size` | 数字 | `0.75`–`1.35` |
| `vinyl.outer` | 数字 | `0.88`–`1.35` |
| `vinyl.outerEnabled` | 布尔 | 外圈盘面 |
| `vinyl.centerRadius` | 数字 | `0.10`–`0.42` |
| `vinyl.fullCover` | 布尔 | 封面铺满中心 |
| `vinyl.spinSpeed` | 数字 | `0.50`–`2.00`，`1` 为 28 秒一圈 |
| `vinyl.color` | 字符串 | `black` `gold` `white` `custom` |
| `vinyl.base` / `vinyl.groove` | 颜色 | 写入自定义盘色。与 `black` / `gold` / `white` 同时出现则失败 |
| `uiScale` | 数字 | `0.80`–`1.25` |
| `transport.docked` | 布尔 | 播放条贴底 |
| `transport.bottomInset` | 数字 | `8`–`48` dp，悬浮时的离底距离 |
| `danmaku.enabled` | 布尔 | 横屏弹幕陪伴 |
| `danmaku.density` | 整数 | `1`–`8` |
| `danmaku.region` | 字符串 | `top` `upper` `lower` `bottom` `full` |
| `danmaku.speed` | 数字 | `0.50`–`2.00` |
| `danmaku.scale` | 数字 | `0.75`–`1.40` |

`look` 的 `player.vinyl` 若也设置了盘色，盘色以 `look` 为准。黑胶大小、偏移、转速只在这里。

### `portrait`

歌词角色、`lineSpacing`、`fontScale` 与专注页相同。`playedCount` / `upcomingCount` 为 `0`–`10`。

| 路径 | 类型 | 范围 |
|---|---|---|
| `lyric.offsetY` | 数字 | `-72`–`72` dp |
| `lyric.backgroundTransparency` | 数字 | `0`–`1`，歌词页磨砂让出背景 |
| `preview.enabled` | 布尔 | 封面态进度条上方的预览歌词 |
| `preview.count` | 整数 | `1`–`3` |
| `preview.playingColor` / `upcomingColor` | 颜色 | 只改这一行的颜色 |
| `preview.playingFont` / `upcomingFont` | 数字 | `12`–`28` sp |
| `preview.fancy` | 布尔 | 切句动画 |
| `preview.align` | 字符串 | `left` `center` `right` |
| `preview.offsetY` | 数字 | `-120`–`0` dp |
| `preview.lineSpacing` | 数字 | `0`–`20` dp |
| `transport.offsetY` | 数字 | `-48`–`48` dp |
| `transport.includeControls` | 布尔 | 底部容器是否包住播放控件 |
| `vinyl.*` / `uiScale` | 同专注页 | 写在竖屏自己的偏好上 |

竖屏没有 `title`、`danmaku`、`lyric.offsetX`、`lyric.avoidVinyl`。写了会整次失败。

### `dynamic`

| 路径 | 类型 | 范围 |
|---|---|---|
| `lyric.playing` / `played` / `unplayed` | 对象 | 同专注页角色。默认是深色字 |
| `lyric.offsetX` | 数字 | `-160`–`160` dp |
| `cover.offsetX` | 数字 | `-120`–`200` dp |
| `cover.glass` | 数字 | `0`–`1`，`1` 为白框，`0` 透出虚化封面 |

动态页没有行距、行数、黑胶几何。写了会整次失败。

### `overlay`

| 路径 | 类型 | 范围 |
|---|---|---|
| `playedLines` / `upcomingLines` | 整数 | `0`–`6` |
| `playedColor` / `currentColor` / `upcomingColor` / `translationColor` | 颜色 | 各一行各一种颜色 |
| `fontSize` | 数字 | `12`–`28` sp |
| `align` | 字符串 | `left` `center` `right` |
| `windowBackground` | 布尔 | 窗底 |
| `blur` | 整数 | `0`–`40` |
| `lyricBackground` | 布尔 | 歌词底 |
| `dynamicWidth` | 布尔 | 随文字变宽 |
| `widthPercent` | 整数 | `45`–`100` |
| `ignoreCutout` | 布尔 | 侵入刘海，便于横屏居中 |

不改悬浮窗开关、锁定、坐标。

## `Xuan.style.get()`

返回当前插件 overlay。没写过的字段不出现。未 `Running` 时返回 `{}`。

## `Xuan.style.clear(surface?, path?)`

| 调用 | 结果 |
|---|---|
| `clear()` | 清掉本插件拥有的全部画面 |
| `clear("focus")` | 清掉这一面 |
| `clear("focus", "lyric.playing.color")` | 只清这一项，同级的 `bold` 还在 |
| `clear("focus", "lyric.playing")` | 清掉播放行的全部样式项 |
| `clear("focus", "lyric")` | 清掉这一面的歌词块 |

路径不存在、字段本来就没设、或不是 owner，返回 `false`。清掉最后一项后，这一面从 `get()` 里消失。
