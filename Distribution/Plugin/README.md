# 插件打包工具

将插件源目录打成符合仓库 `docs/plugin-engine/PACKAGE.md` 的 `.zpp`。契约见 `docs/plugin-engine/TOOLKIT.md`。

需要 Python 3.10+。不安装第三方包。

在仓库根目录：

```
python Distribution/Plugin/zmusic_plugin.py init [目录]
python Distribution/Plugin/zmusic_plugin.py pack <源目录> [-o 出包.zpp]
python Distribution/Plugin/zmusic_plugin.py inspect <包.zpp>
```

`pack` 未指定 `-o` 时，写出到源目录上一级的 `<id>.zpp`。`sign` / `verify` 尚未提供。
