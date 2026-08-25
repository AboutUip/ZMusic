# 定时器（客户端）

作者契约见 [../RUNTIME.md](../RUNTIME.md) 的 `Xuan.timer`。

## 调度

宿主侧计时（不要用插件线程 `sleep` 冒充 `timer`）。到期后 **post 到该插件 executor** 再调 JS。`delay` 占用该线程时，到期回调排队，等 `delay` 返回。

`simple` 无名字，无法 `remove`。`create` 同名替换：先取消旧的再登记。`reps == 0` 为无限；每次回调成功跑完再数一次；回调里 `remove` 自己则不再排下一次。

会话 `stop` / `Error`：取消该插件全部 timer，丢弃已排队未执行的回调。

`ms < 1` 或非整数、`fn` 不是函数、`name` 空白、`reps < 0`：失败。

## 哨兵

与 hook 相同：回调前后标记 / 清除哨兵。

## 能力

不是 `capabilities` 项。只检查 `hostApiAllowed`。
