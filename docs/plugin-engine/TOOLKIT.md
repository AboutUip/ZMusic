# 打包工具

将插件源目录打成符合 [PACKAGE.md](./PACKAGE.md) 的 `.zpp`。

## 命令

在仓库根目录调用（Python 3.10+）：

```
python Distribution/Plugin/zmusic_plugin.py init [目录]
python Distribution/Plugin/zmusic_plugin.py pack <源目录> [-o 出包.zpp]
python Distribution/Plugin/zmusic_plugin.py inspect <包.zpp>
```

实现位于 `Distribution/Plugin/`。`pack` 未指定 `-o` 时写出到源目录上一级的 `<id>.zpp`。

| 命令 | 作用 |
|---|---|
| `init` | 生成 `plugin.json`、`README.md`、`plugin.svg`、`index.js` |
| `pack` | 校验源目录并写出扁平根目录的 `.zpp` |
| `inspect` | 打印清单与文件列表 |

`sign` / `verify` 在签名方案确定后补充。

## `pack` 失败条件

下列任一情形必须失败、不得产出包：

- 缺少 `plugin.json`、`README.md` 或入口文件
- 既无 `plugin.png` 也无 `plugin.svg`
- `zpp` 不是 `1`，或缺少 `id`、`name`、`version`、`entry`、`engine.min`
- `id` 不符合反向域名
- `entry` 越出包根或不是 `.js`
- 任一文件扩展名不在 [PACKAGE.md](./PACKAGE.md) 白名单中
- `capabilities` 不是数组，或含有非字符串项

未知的 capability **名字**不导致失败（忽略即可）。不检查体积。应排除 `.git`、`node_modules`、`.DS_Store` 等非资源路径。输出为确定性 ZIP（条目名排序、固定时间戳）。

## `init` 默认值

- `engine.min`：`100`（`0.1.0`）
- 不写 `engine.max`
- `capabilities`、`signatures`：空数组
- `version`：`1`（`v 00.01`）
- 图标：`plugin.svg`
