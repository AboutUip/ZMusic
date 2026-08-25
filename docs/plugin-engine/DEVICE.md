# 设备能力（`Xuan.media` / `Xuan.share` / `Xuan.clipboard`）

可选能力（展示用）。建议在清单分别写出 `"media"` / `"share"` / `"clipboard"`。引擎 `0.1.0` 起，未声明也可以调用；仅须已成功注册 `Running`。

把图片写入系统相册、拉起系统分享、读写剪贴板。网络下载与 Intent 不在插件线程上执行。失败返回 `false` 或回调 `{ ok: false }`，不抛异常、不结束插件。

会话结束会取消未完成的图片下载，并以 `cancelled` 回调（若会话已无法进 JS 则不再调用函数）。

## `Xuan.media.saveImage(opts, fn)`

把一张图保存到系统相册（Android 为 `Pictures/ZMusic`）。`opts` 为对象，`fn` 为函数。返回 `true` 表示已接受，随后 **必定** 回调一次。返回 `false` 表示拒绝，不回调。

`url` 与 `pack` **必须恰好一个**。

| `opts` | 类型 | 说明 |
|---|---|---|
| `url` | `string` | `http://` / `https://` 图片地址 |
| `pack` | `string` | 本包内相对路径。扩展名须为 `png` / `jpg` / `jpeg` / `webp` / `gif` / `bmp` |
| `filename` | `string` | 可选。相册显示名，最长 80，不得含路径分隔 |

原始字节上限 **8 388 608**。超出则回调 `error` 为 `"too_large"`。

### 回调参数

| 字段 | 类型 | 说明 |
|---|---|---|
| `ok` | `boolean` | 是否已写入相册 |
| `error` | `string` 或 `null` | `bad_url` 不会出现在回调里（非法 `opts` 直接返回 `false`）。已接受后：`timeout` / `too_large` / `network` / `cancelled` / `io` |

```javascript
Xuan.media.saveImage({ url: track.coverUrl }, function(res) {
  if (res.ok) Xuan.notice.show("已保存");
});
```

## `Xuan.share.send(opts, fn?)`

拉起系统分享。`opts` 为对象。`fn` 可省略；写出则必须是函数，且在分享 Intent 已发出（或失败）后回调一次。返回 `true` / `false` 含义同 `saveImage`。

至少提供 `text`、`url`、`imageUrl`、`pack` 之一。`imageUrl` 与 `pack` 不得同时出现。

| `opts` | 类型 | 说明 |
|---|---|---|
| `title` | `string` | 可选。最长 80 |
| `text` | `string` | 可选。最长 8000 |
| `url` | `string` | 可选。`http` / `https`，并入正文 |
| `imageUrl` | `string` | 可选。下载后作为图片分享 |
| `pack` | `string` | 可选。包内图片，规则同 `saveImage` |

图片字节上限与 `saveImage` 相同。用户是否真正完成分享，宿主无法保证。

```javascript
Xuan.share.send({
  title: track.name,
  text: track.artists,
  imageUrl: track.coverUrl
});
```

## `Xuan.clipboard`

| 调用 | 说明 |
|---|---|
| `clipboard.set(text)` | 写入纯文本。最长 32768。成功 `true` |
| `clipboard.get()` | 当前纯文本，没有则为 `null`。过长截到 32768 |

未 `Running`、参数非法、已结束时 `set` 返回 `false`，`get` 返回 `null`。

下列调用 **失败**（`saveImage` / `share.send` 返回 `false` 且不回调）：

- 尚未 `Running` 或已结束
- `opts` 不是对象
- `saveImage` 的 `fn` 不是函数；`share.send` 若传了第二参却不是函数
- `url` / `pack` / `filename` / `text` 不满足上表
- 包内文件不存在、不是文件、或超过字节上限（视为拒绝，不回调）
