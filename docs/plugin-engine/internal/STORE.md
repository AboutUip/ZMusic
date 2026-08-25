# 持久键值（客户端）

作者契约见 [../STORE.md](../STORE.md)。

文件：`filesDir/plugin-engine/store/<id>.json`。整表一个 JSON 对象。写盘用临时文件再改名。

`PluginKvStore` 可在 JVM 单测中直接使用。读写发生在插件线程；表很小，不做单独 IO 线程。

卸载（`uninstallModule`）删除该文件。覆盖安装同一 `id` 时保留键值。禁用不清。

## 能力

展示名 `store`。运行时不检查清单。
