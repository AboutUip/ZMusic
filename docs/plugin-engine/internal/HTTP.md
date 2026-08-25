# 出站 HTTP（客户端）

作者契约见 [../HTTP.md](../HTTP.md)。

`PluginHttpClient` 使用应用已有的 OkHttp。在后台完成 I/O，结果 **post 到该插件 executor** 再调 JS。禁止在 OkHttp 回调线程进 Context。

限制在 `PluginHttpParams` 解析阶段执行；非法 `opts` 使 `request` 返回 `false` 且不回调。

已接受的请求：`Call` 登记在插件 id 下。会话 `stop` / `Error` 时 `cancel`，完成回调带 `cancelled`（Context 已销毁则不再调 JS）。

只允许 `http` / `https`。跟随重定向由 OkHttp 处理；最终 URL 若不是这两种 scheme 视为 `network` 失败。

## 能力

展示名 `http`。运行时不检查清单。
