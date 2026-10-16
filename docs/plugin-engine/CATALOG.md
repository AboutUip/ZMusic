# 曲库（异步）

引擎 **`0.2.0`** 起。建议在清单写出 `"catalog"`。未声明也可以调用；仅须已成功注册 `Running`。

这些调用都走当前登录的音乐账号。脚本拿不到 cookie、token，也拿不到可直接下载的音视频地址。响应里的这两类字段会被去掉。

## 调用形状

除本页列出的对象外，异步方法都是：

```javascript
var accepted = Xuan.song.detail({ id: 123 }, function (res) {
  if (!res || !res.ok) return;
  // res.data 是普通对象
});
```

| | |
|---|---|
| 第一参 | 普通对象。字段见各方法 |
| 第二参 | 函数。回调在 **本插件线程** 上执行 |
| 返回 `true` | 已接受，稍后一定回调一次 |
| 返回 `false` | 未 `Running`、参数不是「对象 + 函数」、或本次运行已结束。不会回调 |

回调值：

| 字段 | 说明 |
|---|---|
| `ok` | `true` 表示宿主已拿到结果 |
| `data` | 成功时的数据。曲库接口大多是上游 JSON 去掉敏感字段之后的普通对象 |
| `error` | 失败代码，见下表 |

| `error` | 含义 |
|---|---|
| `bad_args` | 缺字段、类型不对、或超出长度 |
| `need_login` | 需要已登录且非游客的音乐账号 |
| `empty` | 可以解析，但没有可播放的曲目 |
| `no_song` | 歌词加载时没有 id，也没有当前曲 |
| `too_large` | 结果超过约 90 万字符 |
| `request_failed` | 网络或上游失败 |

`id`、`uid`、`songId` 等必须是正整数。`limit` 超出上限时夹到上限，不是失败。

## 歌曲 `Xuan.song`

| 方法 | 参数 | 登录 |
|---|---|---|
| `detail` | `{ id }` | 否 |
| `wiki` | `{ id }` | 否 |
| `wikiInfo` | `{ id }` | 否 |
| `creators` | `{ id }` | 否 |
| `ugc` | `{ id }` | 是 |
| `like` | `{ id, liked }` | 是 |
| `likedIds` | `{ uid }` | 是 |
| `likedCheck` | `{ ids }`，最多 50 个 | 是 |

## 搜索 `Xuan.search`

| 方法 | 参数 |
|---|---|
| `query` | `{ keywords, type?, limit?, offset? }` |
| `cloud` | 同上，走云搜索 |
| `suggest` | `{ keywords }` |
| `hot` | `{}` |

`keywords` 1～80 字。`type` 常用值：`1` 歌曲，`10` 专辑，`100` 艺人，`1000` 歌单，`1002` 用户，`1004` MV，`1006` 歌词，`1009` 电台。默认 `1`。`limit` 最大 50。

## 专辑 `Xuan.album`

| 方法 | 参数 | 登录 |
|---|---|---|
| `get` | `{ id }` | 否 |
| `dynamic` | `{ id }` | 否 |
| `subscribe` | `{ id, subscribe }` | 是 |
| `followed` | `{ limit?, offset? }` | 是 |
| `play` | `{ id, index? }` | 否 |

`play` 成功时 `data` 为 `{ count, index }`，并开始播放。

## 艺人 `Xuan.artist`

| 方法 | 参数 | 登录 |
|---|---|---|
| `detail` / `dynamic` / `desc` / `top` / `similar` / `hot` | `{ id }` | 否 |
| `songs` | `{ id, order?, limit?, offset? }`。`order` 为 `hot` 或 `time` | 否 |
| `albums` / `mvs` | `{ id, limit?, offset? }` | 否 |
| `follow` | `{ id, follow }` | 是 |
| `followed` | `{ limit?, offset? }` | 是 |
| `play` | `{ id, index? }` | 否。播放热门歌曲 |

## 歌单 `Xuan.playlist`

| 方法 | 参数 | 登录 |
|---|---|---|
| `detail` | `{ id, limit? }` | 否 |
| `tracks` | `{ id, limit?, offset? }` | 否 |
| `dynamic` | `{ id }` | 否 |
| `create` | `{ name }`，最多 40 字 | 是 |
| `delete` | `{ id }` | 是 |
| `rename` | `{ id, name }` | 是 |
| `add` / `remove` | `{ id, ids }`，最多 50 首 | 是 |
| `subscribe` | `{ id, subscribe }` | 是 |
| `list` | `{ uid, limit?, offset? }` | 否 |
| `play` | `{ id, index? }` | 否。最多载入 300 首 |

## 评论 `Xuan.comment`

歌曲评论。`songId` 也可写成 `id`（`list` / `legacy`）。

| 方法 | 参数 | 登录 |
|---|---|---|
| `list` | `{ songId, page?, limit?, sort?, cursor? }`。`sort`：`1` 推荐，`2` 热度，`3` 时间 | 否 |
| `legacy` | `{ songId, limit?, offset?, before? }` | 否 |
| `floor` | `{ songId, parentCommentId, limit?, time? }` | 否 |
| `post` | `{ songId, content, replyCommentId? }`。正文最多 300 字 | 是 |
| `like` | `{ songId, commentId, liked }` | 是 |
| `hug` | `{ uid, commentId, songId }`。`uid` 是评论作者 | 是 |
| `hugs` | `{ uid, commentId, songId, page?, limit?, cursor?, idCursor? }` | 否 |

## MV `Xuan.mv`

只给资料，不给播放地址。要打开 MV 页用 [APP.md](./APP.md) 的 `Xuan.nav.mv`。

| 方法 | 参数 |
|---|---|
| `detail` / `related` | `{ id }` |
| `byArtist` | `{ id, limit?, offset? }` |
| `first` | `{ limit? }` |
| `exclusive` / `all` | `{ limit?, offset? }` |

## 用户 `Xuan.user`

`get()` 是同步的，见 [APP.md](./APP.md)。下面这些是异步的。

| 方法 | 参数 | 登录 |
|---|---|---|
| `account` | `{}`。返回 `{ uid, nickname }`，没有 cookie | 是 |
| `detail` / `medal` | `{ id }` 或 `{ uid }` 中的 `id` | 否 |
| `level` / `subcount` / `vip` | `{}`。`vip` 可带 `uid` | 是 |
| `follows` / `followers` | `{ uid, limit?, offset? }` | 否 |
| `follow` | `{ id, follow }` | 是 |
| `record` | `{ uid, type? }`。`type` `1` 近一周，`0` 全部 | 是 |

## 推荐、首页、云盘

| 方法 | 参数 | 登录 |
|---|---|---|
| `Xuan.recommend.songs` | `{}` | 是 |
| `Xuan.recommend.resource` | `{}` | 是 |
| `Xuan.recommend.playlists` | `{ limit? }` | 否 |
| `Xuan.recommend.newSongs` | `{ limit? }` | 否 |
| `Xuan.recommend.mv` | `{}` | 否 |
| `Xuan.home.banner` | `{}` | 否 |
| `Xuan.home.charts` | `{}` | 否 |
| `Xuan.home.playlists` | `{ limit?, offset? }` | 否 |
| `Xuan.cloud.list` | `{ limit?, offset? }` | 是 |
| `Xuan.cloud.detail` | `{ ids }` | 是 |

云盘只读。不能代用户上传或删除。

## 播放与歌词、漫游

| 方法 | 参数 | 说明 |
|---|---|---|
| `Xuan.play.songs` | `{ ids, index? }` | 最多 100 首。成功后开始播放，`data` 为 `{ count, index }` |
| `Xuan.lyric.load` | `{ id? }` | 省略 `id` 时加载当前曲。`data` 形状同 `Xuan.lyric.get()` |
| `Xuan.fm.start` | `{}` | 开始私人漫游。需要登录 |

```javascript
Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.search.query({ keywords: "晴天", type: 1, limit: 10 }, function (res) {
  if (!res.ok) return;
  var songs = res.data && res.data.result && res.data.result.songs;
  if (!songs || !songs.length) return;
  Xuan.play.songs({ ids: [songs[0].id] }, function (play) {
    if (play.ok) Xuan.player.open();
  });
});
```
