# 包内模块与资源（客户端）

作者契约见 [../PACK.md](../PACK.md)。

## `require`

在插件线程上读出 `.js` 文本，包一层 CommonJS 再 `evaluate`：

```javascript
(function(exports, module, Xuan) {
  // 文件正文
  return module.exports;
})
```

`exports` 初始等于 `module.exports`（空对象）。缓存键：规范化后的相对路径（去掉 `./`）。循环 require 时先把当前 `module.exports` 放进缓存再执行正文。

抛错：不缓存，返回 `null`，journal 记一行。不要为此结束会话。

路径校验复用 `PluginPackageRules`（禁止 `..`、只允许包内真实文件）。

## `pack.text` / `bytes` / `exists`

只读 `installed/<id>/` 下已解压文件。`text` 用 UTF-8；非法 UTF-8 返回 `null`。`bytes` 做成 JS 数组（整数 0–255），不要把 Java `byte[]` 原样丢进 QuickJS。

## 能力

`require` / `pack` 不是 `capabilities` 项。只检查 `hostApiAllowed`。
