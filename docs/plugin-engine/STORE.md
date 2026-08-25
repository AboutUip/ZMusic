# 持久键值（`Xuan.store`）

可选能力（展示用）。建议在清单写出 `"store"`。引擎 `0.1.0` 起，未声明也可以调用；仅须已成功注册 `Running`。

按 **插件 id** 隔离的键值表，进程退出后仍保留。不是任意文件系统 API，不能读其它插件或用户目录。

卸载该插件时删除其键值表。禁用、崩溃隔离、会话 `Error` **不** 删除。

## 限制

| | |
|---|---|
| 键 | 非空字符串，去掉首尾空白后长度 `1`～`256` |
| 值 | JSON 能表达的值（与 `Xuan.hook.run` 相同） |
| 单值序列化 | 最长 262 144 字节 |
| 条目数 | 最多 512 |
| 整表序列化 | 最长 2 097 152 字节 |

超出限制的 `set` 失败，不部分写入。

## API

仅 `Running` 之后可用。失败时 `get` 返回 `null`，其余返回 `false`（`keys` 失败返回空数组）。

| 调用 | 成功 | 说明 |
|---|---|---|
| `get(key)` | 值或 `null` | 没有该键为 `null` |
| `set(key, value)` | `true` / `false` | 覆盖同名键 |
| `remove(key)` | `true` / `false` | 没有该键也成功 |
| `keys()` | 字符串数组 | 插入顺序不保证 |
| `clear()` | `true` / `false` | 清空本插件表 |

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.store.set("count", 1);
var n = Xuan.store.get("count");
Xuan.store.remove("count");
Xuan.store.clear();
```
