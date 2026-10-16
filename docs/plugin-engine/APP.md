# 应用与导航

引擎 **`0.2.0`** 起。建议在清单写出 `"app"` 与 `"nav"`。未声明也可以调用；仅须已成功注册 `Running`。

失败返回 `false` 或 `null`，不抛异常、不结束插件。

## `Xuan.app`

| 调用 | 返回 | 说明 |
|---|---|---|
| `locale()` | `string` | 当前界面语言标签，例如 `zh`、`en`、`ja` |
| `platform()` | `string` | 当前恒为 `"android"` |
| `dark()` | `boolean` | 当前是否深色 |
| `online()` | `boolean` | 宿主是否认为在线 |
| `foreground()` | `boolean` | 应用是否在前台 |
| `orientation()` | `string` | `portrait` 或 `landscape` |
| `screen()` | 对象 | `widthPx`、`heightPx`、`density` |
| `network()` | `string` | `wifi` / `cellular` / `ethernet` / `other` / `none` |
| `battery()` | 对象 | `level` 为 0～100，读不到为 `-1`。`charging` 为是否在充电 |
| `openUrl(url)` | `true` / `false` | 只用系统浏览器打开 `http` 或 `https` |
| `toast(message)` | `true` / `false` | 顶部灵动岛。空白失败。过长会截到 120 字 |
| `haptic(ms)` | `true` / `false` | 振动 `1`～`400` 毫秒 |
| `keepAwake(on)` | `true` / `false` | 是否保持当前画面常亮。没有前台 Activity 时失败 |

不提供通讯录、短信、定位或安装其它应用。

## `Xuan.user.get()`

同步。只描述登录状态，没有 cookie，没有社区 token。

| 字段 | 类型 | 说明 |
|---|---|---|
| `loggedIn` | `boolean` | 是否持有音乐账号会话（含游客） |
| `guest` | `boolean` | 是否游客 |
| `label` | `string` 或 `null` | 本地保存的显示名 |
| `community.loggedIn` | `boolean` | 是否登录了 ZMusic 社区 |
| `community.uid` | `string` 或 `null` | 社区用户 id |

音乐账号的数字 id 用 [CATALOG.md](./CATALOG.md) 的 `Xuan.user.account`。

## `Xuan.nav`

打开宿主已有页面。返回 `true` 表示主界面已接住这次请求。

无参数：

| 调用 | 打开 |
|---|---|
| `player()` | 全屏播放页 |
| `search()` | 搜索 |
| `settings()` | 设置 |
| `charts()` | 排行榜 |
| `fm()` | 私人漫游 |
| `daily()` | 每日推荐 |
| `cloud()` | 云盘 |
| `workshop()` | 创意工坊 |
| `back()` | 关掉全屏播放页，否则退出一层页面。已经在首页则为 `false` |
| `home()` | 关掉全屏播放页并清空页面栈 |

带 id。第二参是标题，可省略：

| 调用 | 打开 |
|---|---|
| `playlist(id, title?)` | 歌单 |
| `album(id, title?)` | 专辑 |
| `artist(id, title?)` | 艺人 |
| `mv(id, title?)` | MV |
| `user(id, title?)` | 用户 |

`id` 必须是正整数。

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

if (Xuan.app.online()) {
  Xuan.nav.search();
}
Xuan.nav.playlist(123456, "示例歌单");
```
