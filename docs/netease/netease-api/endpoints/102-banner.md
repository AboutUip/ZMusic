# banner

> **接口路径（摘自原文）:** `/banner`

**文档说明:** 接口与 [NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi) 约定一致；正文来自仓库内 `docs/raw/home.md` 的离线副本并按章节拆分。API 基地址由应用配置提供，本仓库不包含任何服务端点。

---


说明 : 调用此接口 , 可获取 banner( 轮播图 ) 数据

**可选参数 :**

`type`:资源类型,对应以下类型,默认为 0 即 PC  

```
0: pc

1: android

2: iphone

3: ipad
```  

**接口地址 :** `/banner`

**调用例子 :** `/banner`, `/banner?type=2`

