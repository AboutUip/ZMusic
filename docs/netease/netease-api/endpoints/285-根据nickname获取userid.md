# 根据nickname获取userid

> **接口路径（摘自原文）:** `/get/userids`

**文档说明:** 接口与 [NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi) 约定一致；正文来自仓库内 `docs/raw/home.md` 的离线副本并按章节拆分。API 基地址由应用配置提供，本仓库不包含任何服务端点。

---

说明: 使用此接口,传入用户昵称,可获取对应的用户id,支持批量获取,多个昵称用`分号(;)`隔开  

**必选参数：**  

`nicknames`: 用户昵称,多个用分号(;)隔开

**接口地址:** `/get/userids`

**调用例子:** `/get/userids?nicknames=binaryify` `/get/userids?nicknames=binaryify;binaryify2`

