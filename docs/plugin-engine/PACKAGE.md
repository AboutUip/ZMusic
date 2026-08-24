# 包格式

`.zpp`（ZMusic Plugin Package）是 **ZIP** 归档，扩展名为 `.zpp`。不含专用文件头；用常规解压工具应能打开。

## 约束

- 禁止 ZIP 密码加密。
- 条目路径必须相对包根，禁止 `..`、绝对路径与反斜杠。
- 官方打包工具输出的条目位于归档 **根目录**。若归档内仅有一个顶层目录，宿主将该目录视为包根。
- 不限制归档体积。
- 扩展名必须小写。未列入下文白名单的扩展名使 **整包无效**。

## 必选文件

路径均相对包根。

| 文件 | 说明 |
|---|---|
| `plugin.json` | 清单，见 [MANIFEST.md](./MANIFEST.md) |
| `README.md` | 说明与介绍。UTF-8，无 BOM。宿主 **不执行** 此文件 |
| `plugin.png` 或 `plugin.svg` | 图标，至少其一 |
| `plugin.json` 中 `entry` 指向的 `.js` | 入口脚本，必须存在于包内 |

`entry` 使用正斜杠相对路径，不得包含 `..`。

同时存在 `plugin.png` 与 `plugin.svg` 时，列表使用 png；仅有 svg 时使用 svg。宿主将 svg 作为图形绘制，不作为 HTML 执行。建议提供 `viewBox`；缺失不导致包无效。png 须为 PNG 位图；建议正方形 512×512，边长不作强制。

## 允许的扩展名

含子目录。必选文件已包含在下列集合中。

| 类 | 扩展名 |
|---|---|
| 脚本 / 配置 | `.js` `.json` |
| 图片 | `.png` `.jpg` `.jpeg` `.webp` `.gif` `.bmp` `.svg` |
| 音频 | `.mp3` `.flac` `.m4a` `.aac` `.ogg` `.opus` `.wav` |
| 视频 | `.mp4` `.webm` `.mkv` `.mov` |
| 文本 | `.txt` `.md` `.csv` `.tsv` `.lrc` `.srt` `.vtt` `.yml` `.yaml` `.toml` `.ini` `.cue` `.m3u` `.m3u8` |

允许空目录。向本表 **追加** 扩展名不影响已有包；未列入的扩展名始终使包无效。

## 禁止

出现任一下列情况则整包无效：

- `.html` `.htm` `.xhtml`
- `.wasm` `.pdf`、字体（`.ttf` `.otf` `.woff` 等）
- 原生库与可执行文件（`.so` `.dll` `.exe` `.apk` `.jar` `.class` `.dex` 等）
- 嵌套归档（`.zip` `.zpp` `.7z` `.rar` `.tar` `.gz` 等）
- 无扩展名文件（许可证使用 `LICENSE.txt`）
- 未出现在白名单中的其它扩展名

## README.md

- 供人阅读，不是脚本。
- 使用常见 Markdown（标题、段落、列表、链接、代码块）。
- 禁止 HTML 与脚本。
- 插图仅可引用包内相对路径，且目标扩展名必须在白名单内。
- 名称、版本、引擎范围以 `plugin.json` 为准，勿在 README 中维护第二份可能冲突的版本。

## 附加脚本与资源

白名单内的文件可以放入包中。本引擎版本 **不** 提供从入口脚本读取资源的 API，也 **不** 定义 `require` / `import`。入口以外的 `.js` 可以存放，但加载语义未定义，不得依赖。

## 升级

同一 `id` 下，更大的 `version` 整数覆盖安装。`version` 编码见 [VERSIONING.md](./VERSIONING.md)。更换 `id` 视为另一个插件。
