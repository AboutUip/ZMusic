# 设备能力（客户端）

作者契约见 [../DEVICE.md](../DEVICE.md)。

## 组件

| 类型 | 位置 | 职责 |
|---|---|---|
| `PluginDeviceParams` | `plugin/` | 解析 `saveImage` / `share` / 剪贴板；JVM 可测 |
| `PluginDeviceHost` | `plugin/` | 接口；缺省 `Noop` 全部失败 |
| `PluginAndroidDevice` | `plugin/` | OkHttp 拉图、MediaStore 写入 `Pictures/ZMusic`、`ACTION_SEND`、剪贴板 |
| `Xuan.media` / `share` / `clipboard` | `PluginSession` | `hostApiAllowed`；pack 路径在会话内读字节后再交给 host |

## 线程

下载在 OkHttp 回调线程。写入相册、启动分享、剪贴板均切主线程。结果 **post 到该插件 executor** 再调 JS。禁止在 OkHttp 或主线程进 Context。

`clipboard.get` 为同步：在主线程读剪贴板，插件线程最多等 1 秒。后台读剪贴板可能得到 `null`，这是系统限制，不是引擎错误。

## 取消

未完成的下载按插件 id 登记。会话 `stop` / `Error` 时 `cancel`，完成回调带 `cancelled`。

只允许最终 URL 为 `http` / `https`。包内路径走 `PluginPackFiles.resolve`，扩展名白名单与作者文档一致。

## 能力

展示名 `media` / `share` / `clipboard`。运行时不检查清单。
