# 出站 HTTP（`Xuan.http`）

可选能力（展示用）。建议在清单写出 `"http"`。引擎 `0.1.0` 起，未声明也可以调用；仅须已成功注册 `Running`。

向 `http:` / `https:` 发出请求。回调在 **本插件线程** 上执行，不阻塞 `delay` 以外的宿主线程。不要把这次调用当成同步 `delay`。

## `Xuan.http.request(opts, fn)`

`opts` 为对象，`fn` 为函数。返回 `true` 表示已接受，随后 **必定** 回调一次。返回 `false` 表示拒绝，不回调。

| `opts` | 类型 | 说明 |
|---|---|---|
| `url` | `string` | 必填。须为 `http://` 或 `https://` |
| `method` | `string` | 可选。`GET` / `POST` / `PUT` / `DELETE` / `PATCH` / `HEAD`，大小写不敏感。默认 `GET` |
| `headers` | 对象 | 可选。键值均为字符串。最多 32 项；键最长 128，值最长 4096 |
| `body` | `string` 或省略 | 可选。仅非 `GET` / `HEAD` 可带正文。按 UTF-8 发送 |
| `timeout` | `number` | 可选。整毫秒，`1`～`60000`，默认 `15000` |

响应正文最长 **1 048 576** 字节（按原始字节计）。超出则不提供正文，`error` 为 `"too_large"`。

### 回调参数

一个对象：

| 字段 | 类型 | 说明 |
|---|---|---|
| `ok` | `boolean` | HTTP 状态码 2xx 为 `true` |
| `status` | `number` 或 `null` | 未拿到响应时为 `null` |
| `error` | `string` 或 `null` | 失败原因；成功为 `null` |
| `text` | `string` 或 `null` | 响应正文（按 UTF-8 宽松解码）；无正文或超限为 `null` |
| `headers` | 对象 | 响应头；同名合并为逗号分隔字符串 |

`error` 取值：`bad_url` / `timeout` / `too_large` / `network` / `cancelled`。

会话结束会取消未完成请求，并以 `cancelled` 回调（若会话已无法进 JS 则不再调用函数）。

下列调用 **失败**（返回 `false`，不回调）：

- 尚未 `Running` 或已结束
- `opts` 不是对象，或 `fn` 不是函数
- `url` / `method` / `timeout` / `headers` / `body` 不满足上表
- `GET` / `HEAD` 带了 `body`

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.http.request({
  url: "https://example.com/api",
  method: "GET",
  timeout: 8000
}, function(res) {
  if (res.ok) {
    Xuan.notice.show(String(res.status));
  }
});
```
