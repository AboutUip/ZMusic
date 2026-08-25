# ZMusic 插件规范

本文档面向 **插件作者**：说明如何构造 `.zpp` 包，以及入口脚本可使用的 **Xuan** API。

ZMusic 提供插件的包装、安装与运行。插件内容与行为由提供者负责；是否安装、是否启用由用户承担。ZMusic 不审核插件，也不对插件作安全保证。

| | |
|---|---|
| 包格式（`zpp`） | `1` |
| 引擎版本 | `0.1.0`（整数 `100`） |
| 当前宿主 API | 版本只读、运行状态、灵动岛、同步延迟、非阻塞定时器、钩子、包内 `require` / 读资源、主题、播放、出站 HTTP、持久键值、插件界面、设备（相册 / 分享 / 剪贴板）（见 [运行时](./RUNTIME.md)） |
| `capabilities` | 展示用。未知项忽略，不挡加载，也不是 API 门闩 |

跨端同一套规范。客户端如何实现引擎，见 [internal/](./internal/)。

## 文档

| 文档 | 内容 |
|---|---|
| [PACKAGE.md](./PACKAGE.md) | `.zpp` 包结构、必选文件、允许的资源类型 |
| [MANIFEST.md](./MANIFEST.md) | `plugin.json` |
| [VERSIONING.md](./VERSIONING.md) | 引擎版本与插件版本的编码 |
| [RUNTIME.md](./RUNTIME.md) | `Xuan` 运行时 API |
| [HOOK.md](./HOOK.md) | `Xuan.hook` 总线与宿主事件 |
| [PACK.md](./PACK.md) | `Xuan.require` 与 `Xuan.pack` |
| [THEME.md](./THEME.md) | 色 token 与 `Xuan.theme` |
| [PLAYER.md](./PLAYER.md) | `Xuan.player` 快照、控制与喜欢 |
| [HTTP.md](./HTTP.md) | `Xuan.http` 出站请求 |
| [STORE.md](./STORE.md) | `Xuan.store` 持久键值 |
| [UI.md](./UI.md) | `Xuan.ui` 槽位、组件树、多级页面与宿主表面操作槽 |
| [DEVICE.md](./DEVICE.md) | `Xuan.media` / `Xuan.share` / `Xuan.clipboard` |
| [SIGNING.md](./SIGNING.md) | 签名字段与未签名包 |
| [TOOLKIT.md](./TOOLKIT.md) | 官方打包命令 |

## 最短路径

1. 按 [PACKAGE.md](./PACKAGE.md) 准备文件。
2. 填写 [MANIFEST.md](./MANIFEST.md) 中的 `plugin.json`。要用本期 API 时 `engine.min` 写 `100`。
3. 在入口脚本中使用 [RUNTIME.md](./RUNTIME.md)：先 `Initializing`，再 `Running`。
4. 用 [TOOLKIT.md](./TOOLKIT.md) 打出 `.zpp`。
