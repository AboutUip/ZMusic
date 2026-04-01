# 网易云音乐 Node API — 接口文档索引

本目录由 `docs/raw/home.md` 按 `###` 章节拆分生成，便于离线查阅；与 [NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi) 接口约定一致。实际请求发往的基地址由应用配置决定，不在此仓库中写死。

## 统计

- 接口文档块（`###` 章节）数量：**338**
- 上游项目参考：[Binaryify/NeteaseCloudMusicApi](https://gitlab.com/Binaryify/NeteaseCloudMusicApi)

## 按章节列表

| # | 标题 | 提取的接口路径 | 文档 |
|---|------|----------------|------|
| 1 | 调用前须知 | — | [打开](./endpoints/001-调用前须知.md) |
| 2 | 登录 | `/login/cellphone` `/login` `/login/qr/key` `/login/qr/create` `/login/qr/check` `/register/anonimous` | [打开](./endpoints/002-登录.md) |
| 3 | 刷新登录 | `/login/refresh` | [打开](./endpoints/003-刷新登录.md) |
| 4 | 发送验证码 | `/captcha/sent` | [打开](./endpoints/004-发送验证码.md) |
| 5 | 验证验证码 | `/captcha/verify` | [打开](./endpoints/005-验证验证码.md) |
| 6 | 注册(修改密码) | `/register/cellphone` | [打开](./endpoints/006-注册(修改密码).md) |
| 7 | 检测手机号码是否已注册 | `/cellphone/existence/check` | [打开](./endpoints/007-检测手机号码是否已注册.md) |
| 8 | 初始化昵称 | `/activate/init/profile` | [打开](./endpoints/008-初始化昵称.md) |
| 9 | 重复昵称检测 | `/nickname/check` | [打开](./endpoints/009-重复昵称检测.md) |
| 10 | 更换绑定手机 | `/rebind` | [打开](./endpoints/010-更换绑定手机.md) |
| 11 | 退出登录 | `/logout` | [打开](./endpoints/011-退出登录.md) |
| 12 | 登录状态 | `/login/status` | [打开](./endpoints/012-登录状态.md) |
| 13 | 获取用户详情 | `/user/detail` | [打开](./endpoints/013-获取用户详情.md) |
| 14 | 获取账号信息 | `/user/account` | [打开](./endpoints/014-获取账号信息.md) |
| 15 | 获取用户信息 , 歌单，收藏，mv, dj 数量 | `/user/subcount` | [打开](./endpoints/015-获取用户信息-,-歌单，收藏，mv,-dj-数量.md) |
| 16 | 获取用户等级信息 | `/user/level` | [打开](./endpoints/016-获取用户等级信息.md) |
| 17 | 获取用户绑定信息 | `/user/binding` | [打开](./endpoints/017-获取用户绑定信息.md) |
| 18 | 用户绑定手机 | `/user/replacephone` | [打开](./endpoints/018-用户绑定手机.md) |
| 19 | 更新用户信息 | `/user/update` | [打开](./endpoints/019-更新用户信息.md) |
| 20 | 更新头像 | `/avatar/upload` | [打开](./endpoints/020-更新头像.md) |
| 21 | 私信和通知接口 | `/pl/count` | [打开](./endpoints/021-私信和通知接口.md) |
| 22 | 国家编码列表 | `/countries/code/list` | [打开](./endpoints/022-国家编码列表.md) |
| 23 | 获取用户歌单 | `/user/playlist` | [打开](./endpoints/023-获取用户歌单.md) |
| 24 | 更新歌单 | `/playlist/update` | [打开](./endpoints/024-更新歌单.md) |
| 25 | 更新歌单描述 | `/playlist/desc/update` | [打开](./endpoints/025-更新歌单描述.md) |
| 26 | 更新歌单名 | `/playlist/name/update` | [打开](./endpoints/026-更新歌单名.md) |
| 27 | 更新歌单标签 | `/playlist/tags/update` | [打开](./endpoints/027-更新歌单标签.md) |
| 28 | 歌单封面上传 | `/playlist/cover/update` | [打开](./endpoints/028-歌单封面上传.md) |
| 29 | 调整歌单顺序 | `/playlist/order/update` | [打开](./endpoints/029-调整歌单顺序.md) |
| 30 | 调整歌曲顺序 | `/song/order/update` | [打开](./endpoints/030-调整歌曲顺序.md) |
| 31 | 获取用户历史评论 | `/user/comment/history` | [打开](./endpoints/031-获取用户历史评论.md) |
| 32 | 获取用户电台 | `/user/dj` | [打开](./endpoints/032-获取用户电台.md) |
| 33 | 获取用户关注列表 | `/user/follows` | [打开](./endpoints/033-获取用户关注列表.md) |
| 34 | 获取用户粉丝列表 | `/user/followeds` | [打开](./endpoints/034-获取用户粉丝列表.md) |
| 35 | 获取用户动态 | `/user/event` | [打开](./endpoints/035-获取用户动态.md) |
| 36 | 转发用户动态 | `/event/forward` | [打开](./endpoints/036-转发用户动态.md) |
| 37 | 删除用户动态 | `/event/del` | [打开](./endpoints/037-删除用户动态.md) |
| 38 | 分享文本、歌曲、歌单、mv、电台、电台节目到动态 | `/share/resource` | [打开](./endpoints/038-分享文本、歌曲、歌单、mv、电台、电台节目到动态.md) |
| 39 | 获取动态评论 | `/comment/event` | [打开](./endpoints/039-获取动态评论.md) |
| 40 | 关注/取消关注用户 | `/follow` | [打开](./endpoints/040-关注取消关注用户.md) |
| 41 | 获取用户播放记录 | `/user/record` | [打开](./endpoints/041-获取用户播放记录.md) |
| 42 | 获取热门话题 | `/hot/topic` | [打开](./endpoints/042-获取热门话题.md) |
| 43 | 获取话题详情 | `/topic/detail` | [打开](./endpoints/043-获取话题详情.md) |
| 44 | 获取话题详情热门动态 | `/topic/detail/event/hot` | [打开](./endpoints/044-获取话题详情热门动态.md) |
| 45 | 云村热评(官方下架,暂不能用) | `/comment/hotwall/list` | [打开](./endpoints/045-云村热评(官方下架,暂不能用).md) |
| 46 | 心动模式/智能播放 | `/playmode/intelligence/list` | [打开](./endpoints/046-心动模式智能播放.md) |
| 47 | 获取动态列表 | `/event` | [打开](./endpoints/047-获取动态列表.md) |
| 48 | 歌手分类列表 | `/artist/list` | [打开](./endpoints/048-歌手分类列表.md) |
| 49 | 收藏/取消收藏歌手 | `/artist/sub` | [打开](./endpoints/049-收藏取消收藏歌手.md) |
| 50 | 歌手热门 50 首歌曲 | `/artist/top/song` | [打开](./endpoints/050-歌手热门-50-首歌曲.md) |
| 51 | 歌手全部歌曲 | `/artist/songs` | [打开](./endpoints/051-歌手全部歌曲.md) |
| 52 | 收藏的歌手列表 | `/artist/sublist` | [打开](./endpoints/052-收藏的歌手列表.md) |
| 53 | 收藏的专栏 | `/topic/sublist` | [打开](./endpoints/053-收藏的专栏.md) |
| 54 | 收藏视频 | `/video/sub` | [打开](./endpoints/054-收藏视频.md) |
| 55 | 收藏/取消收藏 MV | `/mv/sub` | [打开](./endpoints/055-收藏取消收藏-MV.md) |
| 56 | 收藏的 MV 列表 | `/mv/sublist` | [打开](./endpoints/056-收藏的-MV-列表.md) |
| 57 | 歌单分类 | `/playlist/catlist` | [打开](./endpoints/057-歌单分类.md) |
| 58 | 热门歌单分类 | `/playlist/hot` | [打开](./endpoints/058-热门歌单分类.md) |
| 59 | 歌单 ( 网友精选碟 ) | `/top/playlist` | [打开](./endpoints/059-歌单-(-网友精选碟-).md) |
| 60 | 精品歌单标签列表 | `/playlist/highquality/tags` | [打开](./endpoints/060-精品歌单标签列表.md) |
| 61 | 获取精品歌单 | `/top/playlist/highquality` | [打开](./endpoints/061-获取精品歌单.md) |
| 62 | 相关歌单 | `/related/playlist` | [打开](./endpoints/062-相关歌单.md) |
| 63 | 获取歌单详情 | `/playlist/detail` | [打开](./endpoints/063-获取歌单详情.md) |
| 64 | 获取歌单所有歌曲 | `/playlist/track/all` | [打开](./endpoints/064-获取歌单所有歌曲.md) |
| 65 | 歌单详情动态 | `/playlist/detail/dynamic` | [打开](./endpoints/065-歌单详情动态.md) |
| 66 | 歌单更新播放量 | `/playlist/update/playcount` | [打开](./endpoints/066-歌单更新播放量.md) |
| 67 | 获取音乐 url | `/song/url` | [打开](./endpoints/067-获取音乐-url.md) |
| 68 | 获取音乐 url - 新版 | `/song/url/v1` | [打开](./endpoints/068-获取音乐-url---新版.md) |
| 69 | 音乐是否可用 | `/check/music` | [打开](./endpoints/069-音乐是否可用.md) |
| 70 | 搜索 | `/search` | [打开](./endpoints/070-搜索.md) |
| 71 | 默认搜索关键词 | `/search/default` | [打开](./endpoints/071-默认搜索关键词.md) |
| 72 | 热搜列表(简略) | `/search/hot` | [打开](./endpoints/072-热搜列表(简略).md) |
| 73 | 热搜列表(详细) | `/search/hot/detail` | [打开](./endpoints/073-热搜列表(详细).md) |
| 74 | 搜索建议 | `/search/suggest` | [打开](./endpoints/074-搜索建议.md) |
| 75 | 搜索多重匹配 | `/search/multimatch` | [打开](./endpoints/075-搜索多重匹配.md) |
| 76 | 新建歌单 | `/playlist/create` | [打开](./endpoints/076-新建歌单.md) |
| 77 | 删除歌单 | `/playlist/delete` | [打开](./endpoints/077-删除歌单.md) |
| 78 | 收藏/取消收藏歌单 | `/playlist/subscribe` | [打开](./endpoints/078-收藏取消收藏歌单.md) |
| 79 | 歌单收藏者 | `/playlist/subscribers` | [打开](./endpoints/079-歌单收藏者.md) |
| 80 | 对歌单添加或删除歌曲 | `/playlist/tracks` | [打开](./endpoints/080-对歌单添加或删除歌曲.md) |
| 81 | 收藏视频到视频歌单 | `/playlist/track/add` | [打开](./endpoints/081-收藏视频到视频歌单.md) |
| 82 | 删除视频歌单里的视频 | `/playlist/track/delete` | [打开](./endpoints/082-删除视频歌单里的视频.md) |
| 83 | 最近播放的视频 | `/playlist/video/recent` | [打开](./endpoints/083-最近播放的视频.md) |
| 84 | 获取歌词 | `/lyric` | [打开](./endpoints/084-获取歌词.md) |
| 85 | 获取逐字歌词 | `/lyric/new` | [打开](./endpoints/085-获取逐字歌词.md) |
| 86 | 新歌速递 | `/top/song` | [打开](./endpoints/086-新歌速递.md) |
| 87 | 首页-发现 | `/homepage/block/page` | [打开](./endpoints/087-首页-发现.md) |
| 88 | 首页-发现-圆形图标入口列表 | `/homepage/dragon/ball` | [打开](./endpoints/088-首页-发现-圆形图标入口列表.md) |
| 89 | 歌曲评论 | `/comment/music` | [打开](./endpoints/089-歌曲评论.md) |
| 90 | 楼层评论 | `/comment/floor` | [打开](./endpoints/090-楼层评论.md) |
| 91 | 专辑评论 | `/comment/album` | [打开](./endpoints/091-专辑评论.md) |
| 92 | 歌单评论 | `/comment/playlist` | [打开](./endpoints/092-歌单评论.md) |
| 93 | mv 评论 | `/comment/mv` | [打开](./endpoints/093-mv-评论.md) |
| 94 | 电台节目评论 | `/comment/dj` | [打开](./endpoints/094-电台节目评论.md) |
| 95 | 视频评论 | `/comment/video` | [打开](./endpoints/095-视频评论.md) |
| 96 | 热门评论 | `/comment/hot` | [打开](./endpoints/096-热门评论.md) |
| 97 | 新版评论接口 | `/comment/new` | [打开](./endpoints/097-新版评论接口.md) |
| 98 | 给评论点赞 | `/comment/like` | [打开](./endpoints/098-给评论点赞.md) |
| 99 | 抱一抱评论 | `/hug/comment` | [打开](./endpoints/099-抱一抱评论.md) |
| 100 | 评论抱一抱列表 | `/comment/hug/list` | [打开](./endpoints/100-评论抱一抱列表.md) |
| 101 | 发送/删除评论 | `/comment` | [打开](./endpoints/101-发送删除评论.md) |
| 102 | banner | `/banner` | [打开](./endpoints/102-banner.md) |
| 103 | 资源点赞( MV,电台,视频) | `/resource/like` | [打开](./endpoints/103-资源点赞(-MV,电台,视频).md) |
| 104 | 获取点赞过的视频 | `/playlist/mylike` | [打开](./endpoints/104-获取点赞过的视频.md) |
| 105 | 获取歌曲详情 | `/song/detail` | [打开](./endpoints/105-获取歌曲详情.md) |
| 106 | 获取专辑内容 | `/album` | [打开](./endpoints/106-获取专辑内容.md) |
| 107 | 专辑动态信息 | `/album/detail/dynamic` | [打开](./endpoints/107-专辑动态信息.md) |
| 108 | 收藏/取消收藏专辑 | `/album/sub` | [打开](./endpoints/108-收藏取消收藏专辑.md) |
| 109 | 获取已收藏专辑列表 | `/album/sublist` | [打开](./endpoints/109-获取已收藏专辑列表.md) |
| 110 | 获取歌手单曲 | `/artists` | [打开](./endpoints/110-获取歌手单曲.md) |
| 111 | 获取歌手 mv | `/artist/mv` | [打开](./endpoints/111-获取歌手-mv.md) |
| 112 | 获取歌手专辑 | `/artist/album` | [打开](./endpoints/112-获取歌手专辑.md) |
| 113 | 获取歌手描述 | `/artist/desc` | [打开](./endpoints/113-获取歌手描述.md) |
| 114 | 获取歌手详情 | `/artist/detail` | [打开](./endpoints/114-获取歌手详情.md) |
| 115 | 获取相似歌手 | `/simi/artist` | [打开](./endpoints/115-获取相似歌手.md) |
| 116 | 获取相似歌单 | `/simi/playlist` | [打开](./endpoints/116-获取相似歌单.md) |
| 117 | 相似 mv | `/simi/mv` | [打开](./endpoints/117-相似-mv.md) |
| 118 | 获取相似音乐 | `/simi/song` | [打开](./endpoints/118-获取相似音乐.md) |
| 119 | 获取最近 5 个听了这首歌的用户 | `/simi/user` | [打开](./endpoints/119-获取最近-5-个听了这首歌的用户.md) |
| 120 | 获取每日推荐歌单 | `/recommend/resource` | [打开](./endpoints/120-获取每日推荐歌单.md) |
| 121 | 获取每日推荐歌曲 | `/recommend/songs` | [打开](./endpoints/121-获取每日推荐歌曲.md) |
| 122 | 每日推荐歌曲-不感兴趣 | `/recommend/songs/dislike` | [打开](./endpoints/122-每日推荐歌曲-不感兴趣.md) |
| 123 | 获取历史日推可用日期列表 | `/history/recommend/songs` | [打开](./endpoints/123-获取历史日推可用日期列表.md) |
| 124 | 获取历史日推详情数据 | `/history/recommend/songs/detail` | [打开](./endpoints/124-获取历史日推详情数据.md) |
| 125 | 私人 FM | `/personal_fm` | [打开](./endpoints/125-私人-FM.md) |
| 126 | 签到 | `/daily_signin` | [打开](./endpoints/126-签到.md) |
| 127 | 乐签信息 | `/sign/happy/info` | [打开](./endpoints/127-乐签信息.md) |
| 128 | 喜欢音乐 | `/like` | [打开](./endpoints/128-喜欢音乐.md) |
| 129 | 喜欢音乐列表 | `/likelist` | [打开](./endpoints/129-喜欢音乐列表.md) |
| 130 | 垃圾桶 | `/fm_trash` | [打开](./endpoints/130-垃圾桶.md) |
| 131 | 新碟上架 | `/top/album` | [打开](./endpoints/131-新碟上架.md) |
| 132 | 全部新碟 | `/album/new` | [打开](./endpoints/132-全部新碟.md) |
| 133 | 最新专辑 | `/album/newest` | [打开](./endpoints/133-最新专辑.md) |
| 134 | 听歌打卡 | `/scrobble` | [打开](./endpoints/134-听歌打卡.md) |
| 135 | 热门歌手 | `/top/artists` | [打开](./endpoints/135-热门歌手.md) |
| 136 | 全部 mv | `/mv/all` | [打开](./endpoints/136-全部-mv.md) |
| 137 | 最新 mv | `/mv/first` | [打开](./endpoints/137-最新-mv.md) |
| 138 | 网易出品 mv | `/mv/exclusive/rcmd` | [打开](./endpoints/138-网易出品-mv.md) |
| 139 | 推荐 mv | `/personalized/mv` | [打开](./endpoints/139-推荐-mv.md) |
| 140 | 推荐歌单 | `/personalized` | [打开](./endpoints/140-推荐歌单.md) |
| 141 | 推荐新音乐 | `/personalized/newsong` | [打开](./endpoints/141-推荐新音乐.md) |
| 142 | 推荐电台 | `/personalized/djprogram` | [打开](./endpoints/142-推荐电台.md) |
| 143 | 推荐节目 | `/program/recommend` | [打开](./endpoints/143-推荐节目.md) |
| 144 | 独家放送(入口列表) | `/personalized/privatecontent` | [打开](./endpoints/144-独家放送(入口列表).md) |
| 145 | 独家放送列表 | `/personalized/privatecontent/list` | [打开](./endpoints/145-独家放送列表.md) |
| 146 | mv 排行 | `/top/mv` | [打开](./endpoints/146-mv-排行.md) |
| 147 | 获取 mv 数据 | `/mv/detail` | [打开](./endpoints/147-获取-mv-数据.md) |
| 148 | 获取 mv 点赞转发评论数数据 | `/mv/detail/info` | [打开](./endpoints/148-获取-mv-点赞转发评论数数据.md) |
| 149 | mv 地址 | `/mv/url` | [打开](./endpoints/149-mv-地址.md) |
| 150 | 获取视频标签列表 | `/video/group/list` | [打开](./endpoints/150-获取视频标签列表.md) |
| 151 | 获取视频分类列表 | `/video/category/list` | [打开](./endpoints/151-获取视频分类列表.md) |
| 152 | 获取视频标签/分类下的视频 | `/video/group` | [打开](./endpoints/152-获取视频标签分类下的视频.md) |
| 153 | 获取全部视频列表 | `/video/timeline/all` | [打开](./endpoints/153-获取全部视频列表.md) |
| 154 | 获取推荐视频 | `/video/timeline/recommend` | [打开](./endpoints/154-获取推荐视频.md) |
| 155 | 相关视频 | `/related/allvideo` | [打开](./endpoints/155-相关视频.md) |
| 156 | 视频详情 | `/video/detail` | [打开](./endpoints/156-视频详情.md) |
| 157 | 获取视频点赞转发评论数数据 | `/video/detail/info` | [打开](./endpoints/157-获取视频点赞转发评论数数据.md) |
| 158 | 获取视频播放地址 | `/video/url` | [打开](./endpoints/158-获取视频播放地址.md) |
| 159 | 所有榜单 | `/toplist` | [打开](./endpoints/159-所有榜单.md) |
| 160 | 排行榜详情 | `/top/list` | [打开](./endpoints/160-排行榜详情.md) |
| 161 | 所有榜单内容摘要 | `/toplist/detail` | [打开](./endpoints/161-所有榜单内容摘要.md) |
| 162 | 歌手榜 | `/toplist/artist` | [打开](./endpoints/162-歌手榜.md) |
| 163 | 云盘 | `/user/cloud` | [打开](./endpoints/163-云盘.md) |
| 164 | 云盘数据详情 | `/user/cloud/detail` | [打开](./endpoints/164-云盘数据详情.md) |
| 165 | 云盘歌曲删除 | `/user/cloud/del` | [打开](./endpoints/165-云盘歌曲删除.md) |
| 166 | 云盘上传 | `/cloud` | [打开](./endpoints/166-云盘上传.md) |
| 167 | 云盘歌曲信息匹配纠正 | `/cloud/match` | [打开](./endpoints/167-云盘歌曲信息匹配纠正.md) |
| 168 | 电台 banner | `/dj/banner` | [打开](./endpoints/168-电台-banner.md) |
| 169 | 电台个性推荐 | `/dj/personalize/recommend` | [打开](./endpoints/169-电台个性推荐.md) |
| 170 | 电台订阅者列表 | `/dj/subscriber` | [打开](./endpoints/170-电台订阅者列表.md) |
| 171 | 用户电台 | `/user/audio` | [打开](./endpoints/171-用户电台.md) |
| 172 | 热门电台 | `/dj/hot` | [打开](./endpoints/172-热门电台.md) |
| 173 | 电台 - 节目榜 | `/dj/program/toplist` | [打开](./endpoints/173-电台---节目榜.md) |
| 174 | 电台 - 付费精品 | `/dj/toplist/pay` | [打开](./endpoints/174-电台---付费精品.md) |
| 175 | 电台 - 24 小时节目榜 | `/dj/program/toplist/hours` | [打开](./endpoints/175-电台---24-小时节目榜.md) |
| 176 | 电台 - 24 小时主播榜 | `/dj/toplist/hours` | [打开](./endpoints/176-电台---24-小时主播榜.md) |
| 177 | 电台 - 主播新人榜 | `/dj/toplist/newcomer` | [打开](./endpoints/177-电台---主播新人榜.md) |
| 178 | 电台 - 最热主播榜 | `/dj/toplist/popular` | [打开](./endpoints/178-电台---最热主播榜.md) |
| 179 | 电台 - 新晋电台榜/热门电台榜 | `/dj/toplist` | [打开](./endpoints/179-电台---新晋电台榜热门电台榜.md) |
| 180 | 电台 - 类别热门电台 | `/dj/radio/hot` | [打开](./endpoints/180-电台---类别热门电台.md) |
| 181 | 电台 - 推荐 | `/dj/recommend` | [打开](./endpoints/181-电台---推荐.md) |
| 182 | 电台 - 分类 | `/dj/catelist` | [打开](./endpoints/182-电台---分类.md) |
| 183 | 电台 - 分类推荐 | `/dj/recommend/type` | [打开](./endpoints/183-电台---分类推荐.md) |
| 184 | 电台 - 订阅 | `/dj/sub` | [打开](./endpoints/184-电台---订阅.md) |
| 185 | 电台的订阅列表 | `/dj/sublist` | [打开](./endpoints/185-电台的订阅列表.md) |
| 186 | 电台 - 付费精选 | `/dj/paygift` | [打开](./endpoints/186-电台---付费精选.md) |
| 187 | 电台 - 非热门类型 | `/dj/category/excludehot` | [打开](./endpoints/187-电台---非热门类型.md) |
| 188 | 电台 - 推荐类型 | `/dj/category/recommend` | [打开](./endpoints/188-电台---推荐类型.md) |
| 189 | 电台 - 今日优选 | `/dj/today/perfered` | [打开](./endpoints/189-电台---今日优选.md) |
| 190 | 电台 - 详情 | `/dj/detail` | [打开](./endpoints/190-电台---详情.md) |
| 191 | 电台 - 节目 | `/dj/program` | [打开](./endpoints/191-电台---节目.md) |
| 192 | 电台 - 节目详情 | `/dj/program/detail` | [打开](./endpoints/192-电台---节目详情.md) |
| 193 | 通知 - 私信 | `/msg/private` | [打开](./endpoints/193-通知---私信.md) |
| 194 | 发送私信 | `/send/text` | [打开](./endpoints/194-发送私信.md) |
| 195 | 发送私信(带歌曲) | `/send/song` | [打开](./endpoints/195-发送私信(带歌曲).md) |
| 196 | 发送私信(带专辑) | `/send/album` | [打开](./endpoints/196-发送私信(带专辑).md) |
| 197 | 发送私信(带歌单) | `/send/playlist` | [打开](./endpoints/197-发送私信(带歌单).md) |
| 198 | 最近联系人 | `/msg/recentcontact` | [打开](./endpoints/198-最近联系人.md) |
| 199 | 私信内容 | `/msg/private/history` | [打开](./endpoints/199-私信内容.md) |
| 200 | 通知 - 评论 | `/msg/comments` | [打开](./endpoints/200-通知---评论.md) |
| 201 | 通知 - @我 | `/msg/forwards` | [打开](./endpoints/201-通知---@我.md) |
| 202 | 通知 - 通知 | `/msg/notices` | [打开](./endpoints/202-通知---通知.md) |
| 203 | 设置 | `/setting` | [打开](./endpoints/203-设置.md) |
| 204 | 数字专辑-新碟上架 | `/album/list` | [打开](./endpoints/204-数字专辑-新碟上架.md) |
| 205 | 数字专辑&数字单曲-榜单 | `/album_songsaleboard` | [打开](./endpoints/205-数字专辑&数字单曲-榜单.md) |
| 206 | 数字专辑-语种风格馆 | `/album/list/style` | [打开](./endpoints/206-数字专辑-语种风格馆.md) |
| 207 | 数字专辑详情 | `/album/detail` | [打开](./endpoints/207-数字专辑详情.md) |
| 208 | 我的数字专辑 | `/digitalAlbum/purchased` | [打开](./endpoints/208-我的数字专辑.md) |
| 209 | 购买数字专辑 | `/digitalAlbum/ordering` | [打开](./endpoints/209-购买数字专辑.md) |
| 210 | 音乐日历 | `/calendar` | [打开](./endpoints/210-音乐日历.md) |
| 211 | 云贝 | `/yunbei` | [打开](./endpoints/211-云贝.md) |
| 212 | 云贝今日签到信息 | `/yunbei/today` | [打开](./endpoints/212-云贝今日签到信息.md) |
| 213 | 云贝签到 | `/yunbei/sign` | [打开](./endpoints/213-云贝签到.md) |
| 214 | 云贝账户信息 | `/yunbei/info` | [打开](./endpoints/214-云贝账户信息.md) |
| 215 | 云贝所有任务 | `/yunbei/tasks` | [打开](./endpoints/215-云贝所有任务.md) |
| 216 | 云贝 todo 任务 | `/yunbei/tasks/todo` | [打开](./endpoints/216-云贝-todo-任务.md) |
| 217 | 云贝完成任务 | `/yunbei/task/finish` | [打开](./endpoints/217-云贝完成任务.md) |
| 218 | 云贝收入 | `/yunbei/tasks/receipt` | [打开](./endpoints/218-云贝收入.md) |
| 219 | 云贝支出 | `/yunbei/tasks/expense` | [打开](./endpoints/219-云贝支出.md) |
| 220 | 关注歌手新歌 | `/artist/new/song` | [打开](./endpoints/220-关注歌手新歌.md) |
| 221 | 关注歌手新 MV | `/artist/new/mv` | [打开](./endpoints/221-关注歌手新-MV.md) |
| 222 | 一起听相关 | — | [打开](./endpoints/222-一起听相关.md) |
| 223 | batch 批量请求接口 | `/batch` | [打开](./endpoints/223-batch-批量请求接口.md) |
| 224 | 云贝推歌 | `/yunbei/rcmd/song` | [打开](./endpoints/224-云贝推歌.md) |
| 225 | 云贝推歌历史记录 | `/yunbei/rcmd/song/history` | [打开](./endpoints/225-云贝推歌历史记录.md) |
| 226 | 已购单曲 | `/song/purchased` | [打开](./endpoints/226-已购单曲.md) |
| 227 | 获取 mlog 播放地址 | `/mlog/url` | [打开](./endpoints/227-获取-mlog-播放地址.md) |
| 228 | 将 mlog id 转为视频 id | `/mlog/to/video` | [打开](./endpoints/228-将-mlog-id-转为视频-id.md) |
| 229 | vip 成长值 | `/vip/growthpoint` | [打开](./endpoints/229-vip-成长值.md) |
| 230 | vip 成长值获取记录 | `/vip/growthpoint/details` | [打开](./endpoints/230-vip-成长值获取记录.md) |
| 231 | vip 任务 | `/vip/tasks` | [打开](./endpoints/231-vip-任务.md) |
| 232 | 领取 vip 成长值 | `/vip/growthpoint/get` | [打开](./endpoints/232-领取-vip-成长值.md) |
| 233 | 歌手粉丝 | `/artist/fans` | [打开](./endpoints/233-歌手粉丝.md) |
| 234 | 歌手粉丝数量 | `/artist/follow/count` | [打开](./endpoints/234-歌手粉丝数量.md) |
| 235 | 数字专辑详情 | `/digitalAlbum/detail` | [打开](./endpoints/235-数字专辑详情-2.md) |
| 236 | 数字专辑销量 | `/digitalAlbum/sales` | [打开](./endpoints/236-数字专辑销量.md) |
| 237 | 音乐人数据概况 | `/musician/data/overview` | [打开](./endpoints/237-音乐人数据概况.md) |
| 238 | 音乐人播放趋势 | `/musician/play/trend` | [打开](./endpoints/238-音乐人播放趋势.md) |
| 239 | 音乐人任务 | `/musician/tasks` | [打开](./endpoints/239-音乐人任务.md) |
| 240 | 音乐人任务(新) | `/musician/tasks/new` | [打开](./endpoints/240-音乐人任务(新).md) |
| 241 | 账号云豆数 | `/musician/cloudbean` | [打开](./endpoints/241-账号云豆数.md) |
| 242 | 领取云豆 | `/musician/cloudbean/obtain` | [打开](./endpoints/242-领取云豆.md) |
| 243 | 获取 VIP 信息 | `/vip/info` | [打开](./endpoints/243-获取-VIP-信息.md) |
| 244 | 获取 VIP 信息(app端) | `/vip/info/v2` | [打开](./endpoints/244-获取-VIP-信息(app端).md) |
| 245 | 音乐人签到 | `/musician/sign` | [打开](./endpoints/245-音乐人签到.md) |
| 246 | 歌曲相关视频 | `/mlog/music/rcmd` | [打开](./endpoints/246-歌曲相关视频.md) |
| 247 | 公开隐私歌单 | `/playlist/privacy` | [打开](./endpoints/247-公开隐私歌单.md) |
| 248 | 获取客户端歌曲下载 url | `/song/download/url` | [打开](./endpoints/248-获取客户端歌曲下载-url.md) |
| 249 | 获取歌手视频 | `/artist/video` | [打开](./endpoints/249-获取歌手视频.md) |
| 250 | 最近播放-歌曲 | `/record/recent/song` | [打开](./endpoints/250-最近播放-歌曲.md) |
| 251 | 最近播放-视频 | `/record/recent/video` | [打开](./endpoints/251-最近播放-视频.md) |
| 252 | 最近播放-声音 | `/record/recent/voice` | [打开](./endpoints/252-最近播放-声音.md) |
| 253 | 最近播放-歌单 | `/record/recent/playlist` | [打开](./endpoints/253-最近播放-歌单.md) |
| 254 | 最近播放-专辑 | `/record/recent/album` | [打开](./endpoints/254-最近播放-专辑.md) |
| 255 | 最近播放-播客 | `/record/recent/dj` | [打开](./endpoints/255-最近播放-播客.md) |
| 256 | 签到进度 | `/signin/progress` | [打开](./endpoints/256-签到进度.md) |
| 257 | 内部版本接口 | `/inner/version` | [打开](./endpoints/257-内部版本接口.md) |
| 258 | 黑胶时光机 | `/vip/timemachine` | [打开](./endpoints/258-黑胶时光机.md) |
| 259 | 音乐百科 - 简要信息 | `/song/wiki/summary` | [打开](./endpoints/259-音乐百科---简要信息.md) |
| 260 | 乐谱列表 | `/sheet/list` | [打开](./endpoints/260-乐谱列表.md) |
| 261 | 乐谱内容 | `/sheet/preview` | [打开](./endpoints/261-乐谱内容.md) |
| 262 | 曲风列表 | `/style/list` | [打开](./endpoints/262-曲风列表.md) |
| 263 | 曲风偏好 | `/style/preference` | [打开](./endpoints/263-曲风偏好.md) |
| 264 | 曲风详情 | `/style/detail` | [打开](./endpoints/264-曲风详情.md) |
| 265 | 曲风-歌曲 | `/style/song` | [打开](./endpoints/265-曲风-歌曲.md) |
| 266 | 曲风-专辑 | `/style/album` | [打开](./endpoints/266-曲风-专辑.md) |
| 267 | 曲风-歌单 | `/style/playlist` | [打开](./endpoints/267-曲风-歌单.md) |
| 268 | 曲风-歌手 | `/style/artist` | [打开](./endpoints/268-曲风-歌手.md) |
| 269 | 云村星评馆 - 简要评论 | `/starpick/comments/summary` | [打开](./endpoints/269-云村星评馆---简要评论.md) |
| 270 | 私人 DJ | `/aidj/content/rcmd` | [打开](./endpoints/270-私人-DJ.md) |
| 271 | 回忆坐标 | `/music/first/listen/info` | [打开](./endpoints/271-回忆坐标.md) |
| 272 | 播客列表 | `/voicelist/search` | [打开](./endpoints/272-播客列表.md) |
| 273 | 播客声音列表 | `/voicelist/list` | [打开](./endpoints/273-播客声音列表.md) |
| 274 | 播客声音搜索 | `/voicelist/list/search` | [打开](./endpoints/274-播客声音搜索.md) |
| 275 | 播客声音详情 | `/voice/detail` | [打开](./endpoints/275-播客声音详情.md) |
| 276 | 播客声音排序 | `/voicelist/trans` | [打开](./endpoints/276-播客声音排序.md) |
| 277 | 播客列表详情 | `/voicelist/detail` | [打开](./endpoints/277-播客列表详情.md) |
| 278 | 播客删除 | `/voice/delete` | [打开](./endpoints/278-播客删除.md) |
| 279 | 播客上传声音 | `/voice/upload` | [打开](./endpoints/279-播客上传声音.md) |
| 280 | 电台排行榜获取 | `/djRadio/top` | [打开](./endpoints/280-电台排行榜获取.md) |
| 281 | 获取声音歌词 | `/voice/lyric` | [打开](./endpoints/281-获取声音歌词.md) |
| 282 | 验证接口-二维码生成 | `/verify/getQr` | [打开](./endpoints/282-验证接口-二维码生成.md) |
| 283 | 验证接口-二维码检测 | `/verify/qrcodestatus` | [打开](./endpoints/283-验证接口-二维码检测.md) |
| 284 | 听歌识曲 | `/audio/match` | [打开](./endpoints/284-听歌识曲.md) |
| 285 | 根据nickname获取userid | `/get/userids` | [打开](./endpoints/285-根据nickname获取userid.md) |
| 286 | 专辑简要百科信息 | `/ugc/album/get` | [打开](./endpoints/286-专辑简要百科信息.md) |
| 287 | 歌曲简要百科信息 | `/ugc/song/get` | [打开](./endpoints/287-歌曲简要百科信息.md) |
| 288 | 歌手简要百科信息 | `/ugc/artist/get` | [打开](./endpoints/288-歌手简要百科信息.md) |
| 289 | mv简要百科信息 | `/ugc/mv/get` | [打开](./endpoints/289-mv简要百科信息.md) |
| 290 | 搜索歌手 | `/ugc/artist/search` | [打开](./endpoints/290-搜索歌手.md) |
| 291 | 用户贡献内容 | `/ugc/detail` | [打开](./endpoints/291-用户贡献内容.md) |
| 292 | 用户贡献条目、积分、云贝数量 | `/ugc/user/devote` | [打开](./endpoints/292-用户贡献条目、积分、云贝数量.md) |
| 293 | 年度听歌报告 | `/summary/annual` | [打开](./endpoints/293-年度听歌报告.md) |
| 294 | 本地歌曲文件匹配网易云歌曲信息 | `/search/match` | [打开](./endpoints/294-本地歌曲文件匹配网易云歌曲信息.md) |
| 295 | 歌曲音质详情 | `/song/music/detail` | [打开](./endpoints/295-歌曲音质详情.md) |
| 296 | 歌曲红心数量 | `/song/red/count` | [打开](./endpoints/296-歌曲红心数量.md) |
| 297 | 私人 FM 模式选择 | `/personal/fm/mode` | [打开](./endpoints/297-私人-FM-模式选择.md) |
| 298 | 获取专辑歌曲的音质 | `/album/privilege` | [打开](./endpoints/298-获取专辑歌曲的音质.md) |
| 299 | 歌手详情动态 | `/artist/detail/dynamic` | [打开](./endpoints/299-歌手详情动态.md) |
| 300 | 最近听歌列表 | `/recent/listen/list` | [打开](./endpoints/300-最近听歌列表.md) |
| 301 | 云盘导入歌曲 | `/cloud/import` | [打开](./endpoints/301-云盘导入歌曲.md) |
| 302 | 获取客户端歌曲下载链接 - 新版 | `/song/download/url/v1` | [打开](./endpoints/302-获取客户端歌曲下载链接---新版.md) |
| 303 | 当前账号关注的用户/歌手 | `/user/follow/mixed` | [打开](./endpoints/303-当前账号关注的用户歌手.md) |
| 304 | 会员下载歌曲记录 | `/song/downlist` | [打开](./endpoints/304-会员下载歌曲记录.md) |
| 305 | 会员本月下载歌曲记录 | `/song/monthdownlist` | [打开](./endpoints/305-会员本月下载歌曲记录.md) |
| 306 | 已购买单曲 | `/song/singledownlist` | [打开](./endpoints/306-已购买单曲.md) |
| 307 | 歌曲是否喜爱 | `/song/like/check` | [打开](./endpoints/307-歌曲是否喜爱.md) |
| 308 | 用户是否互相关注 | `/user/mutualfollow/get` | [打开](./endpoints/308-用户是否互相关注.md) |
| 309 | 歌曲动态封面 | `/song/dynamic/cover` | [打开](./endpoints/309-歌曲动态封面.md) |
| 310 | 用户徽章 | `/user/medal` | [打开](./endpoints/310-用户徽章.md) |
| 311 | 用户状态 | `/user/social/status` | [打开](./endpoints/311-用户状态.md) |
| 312 | 用户状态 - 支持设置的状态 | `/user/social/status/support` | [打开](./endpoints/312-用户状态---支持设置的状态.md) |
| 313 | 用户状态 - 相同状态的用户 | `/user/social/status/rcmd` | [打开](./endpoints/313-用户状态---相同状态的用户.md) |
| 314 | 用户状态 - 编辑 | `/user/social/status/edit` | [打开](./endpoints/314-用户状态---编辑.md) |
| 315 | 听歌足迹 - 年度听歌足迹 | `/listen/data/year/report` | [打开](./endpoints/315-听歌足迹---年度听歌足迹.md) |
| 316 | 听歌足迹 - 今日收听 | `/listen/data/today/song` | [打开](./endpoints/316-听歌足迹---今日收听.md) |
| 317 | 听歌足迹 - 总收听时长 | `/listen/data/total` | [打开](./endpoints/317-听歌足迹---总收听时长.md) |
| 318 | 听歌足迹 - 本周/本月收听时长 | `/listen/data/realtime/report` | [打开](./endpoints/318-听歌足迹---本周本月收听时长.md) |
| 319 | 听歌足迹 - 周/月/年收听报告 | `/listen/data/report` | [打开](./endpoints/319-听歌足迹---周月年收听报告.md) |
| 320 | 歌单导入 - 元数据/文字/链接导入 | `/playlist/import/name/task/create` | [打开](./endpoints/320-歌单导入---元数据文字链接导入.md) |
| 321 | 歌单导入 - 任务状态 | `/playlist/import/task/status` | [打开](./endpoints/321-歌单导入---任务状态.md) |
| 322 | 副歌时间 | `/song/chorus` | [打开](./endpoints/322-副歌时间.md) |
| 323 | 相关歌单推荐 | `/playlist/detail/rcmd/get` | [打开](./endpoints/323-相关歌单推荐.md) |
| 324 | 歌词摘录 - 歌词摘录信息 | `/song/lyrics/mark` | [打开](./endpoints/324-歌词摘录---歌词摘录信息.md) |
| 325 | 歌词摘录 - 我的歌词本 | `/song/lyrics/mark/user/page` | [打开](./endpoints/325-歌词摘录---我的歌词本.md) |
| 326 | 歌词摘录 - 添加/修改摘录歌词 | `/song/lyrics/mark/add` | [打开](./endpoints/326-歌词摘录---添加修改摘录歌词.md) |
| 327 | 歌词摘录 - 删除摘录歌词 | `/song/lyrics/mark/del` | [打开](./endpoints/327-歌词摘录---删除摘录歌词.md) |
| 328 | 广播电台 - 分类/地区信息 | `/broadcast/category/region/get` | [打开](./endpoints/328-广播电台---分类地区信息.md) |
| 329 | 广播电台 - 我的收藏 | `/broadcast/channel/collect/list` | [打开](./endpoints/329-广播电台---我的收藏.md) |
| 330 | 广播电台 - 电台信息 | `/broadcast/channel/currentinfo` | [打开](./endpoints/330-广播电台---电台信息.md) |
| 331 | 广播电台 - 全部电台 | `/broadcast/channel/list` | [打开](./endpoints/331-广播电台---全部电台.md) |
| 332 | 用户的创建歌单列表 | `/user/playlist/create` | [打开](./endpoints/332-用户的创建歌单列表.md) |
| 333 | 用户的收藏歌单列表 | `/user/playlist/collect` | [打开](./endpoints/333-用户的收藏歌单列表.md) |
| 334 | DIFM电台 - 分类 | `/dj/difm/all/style/channel` | [打开](./endpoints/334-DIFM电台---分类.md) |
| 335 | DIFM电台 - 收藏列表 | `/dj/difm/subscribe/channels/get` | [打开](./endpoints/335-DIFM电台---收藏列表.md) |
| 336 | DIFM电台 - 收藏频道 | `/dj/difm/channel/subscribe` | [打开](./endpoints/336-DIFM电台---收藏频道.md) |
| 337 | DIFM电台 - 取消收藏频道 | `/dj/difm/channel/unsubscribe` | [打开](./endpoints/337-DIFM电台---取消收藏频道.md) |
| 338 | DIFM电台 - 播放列表 | `/dj/difm/playing/tracks/list` | [打开](./endpoints/338-DIFM电台---播放列表.md) |
