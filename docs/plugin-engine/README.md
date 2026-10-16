# ZMusic 插件规范

本文档面向 **插件作者**：说明如何构造 `.zpp` 包，以及入口脚本可使用的 **Xuan** API。

ZMusic 提供插件的包装、安装与运行。插件内容与行为由提供者负责；是否安装、是否启用由用户承担。ZMusic 不审核插件，也不对插件作安全保证。

| | |
|---|---|
| 包格式（`zpp`） | `1` |
| 引擎版本 | `0.2.0`（整数 `200`） |
| 当前宿主 API | `0.1.0` 的全部能力，加上播放控制、曲库、导航、应用信息，以及按画面逐项覆盖的播放页样式（见 [运行时](./RUNTIME.md)、[样式](./STYLE.md)） |
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
| [LOOK.md](./LOOK.md) | 外观 overlay：壁纸、玻璃、浅深色、播放页氛围 |
| [THEME.md](./THEME.md) | 色 token 与 `Xuan.theme` |
| [STYLE.md](./STYLE.md) | 播放页与桌面歌词的逐项样式：`Xuan.style` |
| [PLAYER.md](./PLAYER.md) | `Xuan.player` 快照、控制与喜欢 |
| [HTTP.md](./HTTP.md) | `Xuan.http` 出站请求 |
| [STORE.md](./STORE.md) | `Xuan.store` 持久键值 |
| [UI.md](./UI.md) | `Xuan.ui` 槽位、组件树、多级页面、宿主表面操作槽与陈列 |
| [DEVICE.md](./DEVICE.md) | `Xuan.media` / `Xuan.share` / `Xuan.clipboard` |
| [CATALOG.md](./CATALOG.md) | 曲库、搜索、评论、用户、推荐。异步，回调返回 |
| [APP.md](./APP.md) | `Xuan.app` 与 `Xuan.nav` |
| [SIGNING.md](./SIGNING.md) | 签名字段与未签名包 |
| [TOOLKIT.md](./TOOLKIT.md) | 官方打包命令 |

## 最短路径

1. 按 [PACKAGE.md](./PACKAGE.md) 准备文件。
2. 填写 [MANIFEST.md](./MANIFEST.md) 中的 `plugin.json`。只用 `0.1.0` 能力时 `engine.min` 写 `100`；要用 `0.2.0` 的曲库、歌词或导航时写 `200`。

## 公开阅读

这些地址不需要登录，也不是网页里嵌的脚本。模型或作者可以直接拉取 Markdown。

| | |
|---|---|
| 索引 | `/zmusic/plugin-engine/llms.txt` |
| 文件清单 | `/zmusic/plugin-engine/index.json` |
| 单篇 | `/zmusic/plugin-engine/README.md`（其余文件名相同） |
| 阅读页 | `/zmusic/docs/README` |

社区站点示例：`http://120.27.140.91/zmusic/plugin-engine/llms.txt`。
3. 在入口脚本中使用 [RUNTIME.md](./RUNTIME.md)：先 `Initializing`，再 `Running`。
4. 用 [TOOLKIT.md](./TOOLKIT.md) 打出 `.zpp`。
