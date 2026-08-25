# 包内模块与资源（`Xuan.require` / `Xuan.pack`）

引擎 **`0.1.0`** 起的核心 API，不是可选 `capabilities`。只读 **本插件已解压的包根**，不能读其它插件、不能读用户磁盘、不能出包根。

仅在已成功注册 `Running` 之后可用。失败返回 `null` / `false`（见各调用），不抛异常、不结束插件。

路径规则与 [PACKAGE.md](./PACKAGE.md) 的 `entry` 相同：相对包根、正斜杠、禁止 `..` 与绝对路径。允许可选的 `./` 前缀（会被去掉）。大小写按宿主文件系统。

## `Xuan.require(path)`

加载包内另一个 `.js`，按 **CommonJS** 求值。

| | |
|---|---|
| `path` | 必须指向 `.js`，且该文件在包内 |
| 返回 | `module.exports`；失败为 `null` |
| 缓存 | 同一会话同一规范化路径只执行一次；再次 `require` 返回同一导出对象 |
| 循环 | 允许。循环边拿到的是 **正在构建中** 的 `module.exports`（可能还不完整） |

被加载脚本可以使用：

- `exports.foo = ...`
- `module.exports = ...`（整体替换）
- `Xuan`（与入口同一套）
- 再调用 `Xuan.require` / `Xuan.pack`

不提供 `import` / ES module。不要依赖 Node 的目录 `index.js` 或 `node_modules` 解析：路径必须写到具体文件。

失败（返回 `null`）：未 `Running`、路径非法、不是 `.js`、文件不存在、脚本抛错。脚本抛错时该路径不写入缓存，可再次尝试。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

var util = Xuan.require("lib/util.js");
if (util) {
  util.hello();
}
```

`lib/util.js`：

```javascript
exports.hello = function() {
  Xuan.notice.show("ok");
};
```

## `Xuan.pack`

读资源，不执行。扩展名仍须在 [PACKAGE.md](./PACKAGE.md) 白名单内（解压时已校验）。

| 调用 | 成功 | 失败 |
|---|---|---|
| `Xuan.pack.exists(path)` | `true` / `false`（非法路径为 `false`） | 未 `Running` 或已结束时 `false` |
| `Xuan.pack.text(path)` | UTF-8 字符串（BOM 会去掉） | `null` |
| `Xuan.pack.bytes(path)` | 字节数组：每项 `0`～`255` 的整数 | `null` |

`text` 在文件不是合法 UTF-8 时失败。不限制体积；读不下时返回 `null`。

```javascript
var cfg = Xuan.pack.text("data/config.json");
var hasPng = Xuan.pack.exists("plugin.png");
var icon = Xuan.pack.bytes("plugin.png");
```
