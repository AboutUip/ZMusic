# ZMusic Docs

文档目录用于承载可随仓库分发的技术资料，目标是：

- 新同学可快速上手
- 迭代信息可追踪
- API 说明可离线查阅

---

## 阅读顺序（推荐）

1. 根入门文档：`../README.md`
2. 架构总览：`ARCHITECTURE.md`
3. 接口离线说明：`raw/home.md`

---

## 文档索引

| 路径 | 说明 |
|---|---|
| `ARCHITECTURE.md` | Android 端架构、核心模块与数据流 |
| `raw/home.md` | 网易云兼容 API 文档离线全文 |
| `netease/netease-api/INDEX.md` | API 章节索引（分篇入口） |
| `netease/netease-api/endpoints/*.md` | API 细分端点文档 |
| `netease/netease-api/SUMMARY.md` | 功能特性编号列表 |

---

## 维护约定

- 文档以“可执行信息”为主，避免空泛描述
- 代码目录变更时，同步更新路径引用
- 涉及环境配置（如服务地址）优先放在根 `README.md`
- 不在文档中写入私有服务地址、Cookie 或账号信息

---

上游参考：  
[NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi)
