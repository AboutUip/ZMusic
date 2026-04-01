# DIFM电台 - 播放列表

> **接口路径（摘自原文）:** `/dj/difm/playing/tracks/list`

**文档说明:** 接口与 [NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi) 约定一致；正文来自仓库内 `docs/raw/home.md` 的离线副本并按章节拆分。API 基地址由应用配置提供，本仓库不包含任何服务端点。

---


说明: 调用此接口, 获取DIFM播放列表

**必选参数 :**  

`source`: 来源, 0: 最嗨电音 1: 古典电台 2: 爵士电台

`channelId`: 频道id

**可选参数 :**

`limit`: 返回数量, 默认为 5

**接口地址:** `/dj/difm/playing/tracks/list`

**调用例子:** `/dj/difm/playing/tracks/list?source=0&channelId=1012`

## 离线访问此文档

此文档同时也是 Progressive Web Apps(PWA), 加入了 serviceWorker, 可离线访问

## 关于此文档

此文档由 [docsify](https://github.com/QingWei-Li/docsify/) 生成 docsify 是一个动态生成文档网站的工具。不同于 GitBook、Hexo 的地方是它不会生成将 .md 转成 .html 文件，所有转换工作都是在运行时进行。

## License

[The MIT License (MIT)](https://gitlab.com/Binaryify/NeteaseCloudMusicApi/blob/main/LICENSE)

