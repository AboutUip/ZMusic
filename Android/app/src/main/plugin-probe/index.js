var space = {
  online: true,
  dark: false,
  loggedIn: false,
  vol: 30,
  note: "",
  loop: false,
  mode: "list"
};

function lineHost() {
  return (
    (space.online ? "在线" : "离线") +
    " · " +
    (space.dark ? "深色" : "浅色") +
    " · " +
    (space.loggedIn ? "已登录" : "未登录")
  );
}

function linePlay() {
  var snap = Xuan.player.get();
  if (!snap || !snap.track) return "当前无曲目";
  var state = snap.playing ? "播放中" : "已暂停";
  var pos = Math.floor((snap.positionMs || 0) / 1000);
  var dur = Math.floor((snap.durationMs || 0) / 1000);
  return state + " · " + snap.track.name + " · " + pos + "s / " + dur + "s";
}

function patch(id, text) {
  Xuan.ui.page.patch("tune", { id: id, text: text });
}

function refresh() {
  patch("ver", "应用 " + Xuan.zmusic.version + "  ·  引擎 " + Xuan.engine.version);
  patch("host", lineHost());
  patch("play", linePlay());
  patch("volHint", "当前 " + space.vol);
}

function onEvent(ev) {
  if (!ev) return;
  if (ev.type === "change") {
    if (ev.id === "vol") {
      space.vol = ev.value;
      patch("volHint", "当前 " + ev.value);
      return;
    }
    if (ev.id === "note") {
      space.note = ev.value || "";
      return;
    }
    if (ev.id === "loop") {
      space.loop = !!ev.value;
      return;
    }
    if (ev.id === "mode") {
      space.mode = ev.value;
    }
    return;
  }
  if (ev.type === "press") {
    if (ev.id === "themeAccent") {
      Xuan.theme.set({
        "text.accent": "#EC4141",
        "text.dock.active": "#EC4141",
        "face.accent": "#EC4141"
      });
      return;
    }
    if (ev.id === "themeClear") {
      Xuan.theme.clear();
      return;
    }
    if (ev.id === "notice") {
      Xuan.notice.show("探针：" + Xuan.engine.version);
      return;
    }
    if (ev.id === "alert") {
      Xuan.ui.alert({ title: "探针", message: "确认框可用。" });
      return;
    }
    if (ev.id === "sheet") {
      Xuan.ui.sheet({
        title: "探针",
        actions: [
          { id: "ok", label: "好" }
        ]
      });
    }
  }
}

Xuan.runtime.register(Xuan.runtime.State.Initializing);
Xuan.runtime.register(Xuan.runtime.State.Running);

Xuan.ui.page.define("tune", {
  title: "调优",
  root: {
    type: "scroll",
    gap: 14,
    padV: 4,
    children: [
      { type: "text", text: "引擎探针", style: "title", size: 22 },
      { type: "text", id: "ver", text: "读取中", style: "meta" },
      {
        type: "text",
        text: "Dock 切页 · 控件与样式试验",
        style: "hint",
        color: "accent"
      },
      {
        type: "section",
        title: "宿主",
        children: [
          { type: "text", id: "host", text: "读取中", style: "body" }
        ]
      },
      {
        type: "section",
        title: "播放",
        children: [
          { type: "text", id: "play", text: "读取中", style: "body" }
        ]
      },
      {
        type: "section",
        title: "控件",
        gap: 12,
        children: [
          {
            type: "slider",
            id: "vol",
            label: "音量",
            min: 0,
            max: 100,
            step: 1,
            value: 30
          },
          { type: "text", id: "volHint", text: "当前 30", style: "meta" },
          {
            type: "field",
            id: "note",
            label: "备注",
            placeholder: "输入一段说明",
            value: ""
          },
          {
            type: "toggle",
            id: "loop",
            label: "循环试验",
            value: false
          },
          {
            type: "segmented",
            id: "mode",
            value: "list",
            children: [
              { type: "option", id: "list", label: "列表" },
              { type: "option", id: "one", label: "单曲" },
              { type: "option", id: "rand", label: "随机" }
            ]
          }
        ]
      },
      {
        type: "section",
        title: "主题",
        children: [
          {
            type: "row",
            gap: 8,
            children: [
              { type: "button", id: "themeAccent", label: "试用强调色", role: "primary", icon: "check" },
              { type: "button", id: "themeClear", label: "清除覆盖", flex: 0, width: "hug" }
            ]
          }
        ]
      },
      {
        type: "section",
        title: "试验",
        children: [
          { type: "button", id: "notice", label: "灵动岛", icon: "info" },
          { type: "button", id: "alert", label: "确认框" },
          { type: "button", id: "sheet", label: "操作表" }
        ]
      }
    ]
  }
});

Xuan.ui.page.on("tune", onEvent);

Xuan.hook.add("app.online", "probe", function() {
  space.online = true;
  refresh();
});
Xuan.hook.add("app.offline", "probe", function() {
  space.online = false;
  refresh();
});
Xuan.hook.add("app.appearance", "probe", function(ev) {
  if (ev) space.dark = !!ev.dark;
  refresh();
});
Xuan.hook.add("user.session", "probe", function(ev) {
  if (ev) space.loggedIn = !!ev.loggedIn;
  refresh();
});
Xuan.hook.add("player.track", "probe", function() {
  refresh();
});
Xuan.hook.add("player.state", "probe", function() {
  refresh();
});

Xuan.timer.create("probe-play", 1000, 0, function() {
  patch("play", linePlay());
});

refresh();
