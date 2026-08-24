# 清单（`plugin.json`）

包根目录下的 JSON 清单。编码 UTF-8，无 BOM。未知字段忽略。缺少必填字段则整包无效。

当前包格式版本 `zpp` 必须为 `1`。

## 字段

| 字段 | 必填 | 类型 | 说明 |
|---|---|---|---|
| `zpp` | 是 | 整数 | 包格式版本，必须为 `1` |
| `id` | 是 | 字符串 | 插件标识。反向域名，小写，发布后不可更改。至少两段，例如 `com.example.foo`。每段以字母开头，后接字母、数字或下划线 |
| `name` | 是 | 字符串 | 显示名称 |
| `version` | 是 | 整数 | 插件版本。形态为 `v 00.00` 的去点整数，见 [VERSIONING.md](./VERSIONING.md) |
| `entry` | 是 | 字符串 | 入口脚本相对路径，例如 `index.js` |
| `engine.min` | 是 | 整数 | 所需最低引擎版本（去点整数） |
| `engine.max` | 否 | 整数 | 所允许的最高引擎版本。省略表示无上限。若写出，宿主必须遵守 |
| `description` | 否 | 字符串 | 列表用短描述；长文放在 `README.md` |
| `author` | 否 | 字符串 | 显示用。未签名时仅表示自称 |
| `homepage` | 否 | 字符串 | `http://` 或 `https://` |
| `capabilities` | 否 | 字符串数组 | 省略、`[]`，或仅含 [VERSIONING.md](./VERSIONING.md) 已知能力（当前：`theme`）。未知字符串则整包无效 |
| `signatures` | 否 | 数组 | 保留。见 [SIGNING.md](./SIGNING.md) |

禁止使用 `official` 等字段自称官方。标注由社区或宿主给出，不写在包内。

## 加载条件

设当前引擎版本整数为 `E`（`0.0.2` 时 `E = 2`）。包可被加载当且仅当：

1. `zpp === 1`
2. `engine.min ≤ E`
3. 若存在 `engine.max`，则 `E ≤ engine.max`
4. `capabilities` 省略、为空数组，或每一项均为已知能力名（见 [VERSIONING.md](./VERSIONING.md)）
5. `entry` 指向的文件存在且扩展名为 `.js`
6. 存在 `plugin.png` 或 `plugin.svg`
7. 包内全部文件扩展名均在 [PACKAGE.md](./PACKAGE.md) 白名单中

宿主应用程序版本 **不** 参与能否加载。

引擎保证向后兼容：未声明 `max` 的包在更高引擎版本上仍可加载。多数包只需设置 `engine.min`。

## 示例

```json
{
  "zpp": 1,
  "id": "com.example.demo",
  "name": "示例插件",
  "version": 1,
  "entry": "index.js",
  "engine": {
    "min": 1
  },
  "description": "示例",
  "author": "example",
  "capabilities": [],
  "signatures": []
}
```

仅允许在单一引擎版本上加载时：

```json
"engine": {
  "min": 1,
  "max": 1
}
```
