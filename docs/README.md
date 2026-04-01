# 技术文档（离线 API 说明）

`docs` 仅存放**可随仓库分发的技术说明**，不包含生成脚本、HTML 镜像或任何具体部署地址。

## 网易云兼容 API（NeteaseCloudMusicApi）

| 路径 | 说明 |
|------|------|
| `raw/home.md` | 接口说明全文（离线副本，开发时无需再拉取在线文档） |
| `netease/INDEX.md` | 按 `###` 章节编号的总表，链到各分篇 |
| `netease/endpoints/*.md` | 各接口章节独立 Markdown |
| `netease/SUMMARY.md` | 原文「功能特性」编号列表 |

客户端应将 API **基地址**放在应用配置或构建变量中（例如 `local.properties` / `BuildConfig`），勿写入本目录。

上游参考：[NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi)。
