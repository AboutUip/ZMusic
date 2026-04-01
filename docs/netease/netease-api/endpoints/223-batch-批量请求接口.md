# batch 批量请求接口

> **接口路径（摘自原文）:** `/batch`

**文档说明:** 接口与 [NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi) 约定一致；正文来自仓库内 `docs/raw/home.md` 的离线副本并按章节拆分。API 基地址由应用配置提供，本仓库不包含任何服务端点。

---


说明 : 登录后调用此接口 ,传入接口和对应原始参数(原始参数非文档里写的参数,需参考源码),可批量请求接口

**接口地址 :** `/batch`

**调用例子 :** 使用 GET 方式:`/batch?/api/v2/banner/get={"clientType":"pc"}` 使用 POST 方式传入参数:`{ "/api/v2/banner/get": {"clientType":"pc"} }`

