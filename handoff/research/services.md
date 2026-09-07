# 服务层与命令层调研

> 调研日期：2026-09-07

## 调研目的

理清 KaTpa 的业务服务职责、请求生命周期、命令与权限、事件监听。

## 范围与方法

- 阅读 `src/main/java/org/katacr/katpa/service/` 全部 9 个 service。
- 阅读 `src/main/java/org/katacr/katpa/command/` 全部 11 个 command。
- 阅读 `src/main/java/org/katacr/katpa/listener/PlayerListener.java`。

## 关键发现

- RequestService 用三索引（outgoing/incoming/byId）+ UUID 管理请求，撤销校验 requestId 防误撤。
- TeleportService 用 WarmupSession 处理吟唱、移动/伤害中断、异步传送。
- 命令按模块开关注册，关闭模块统一 `module-disabled` 提示。
- PlayerListener 监听 7 类事件，协调 back 记录、吟唱中断、跨服上线。

## 详细结论

见交接文件 `services.md`。关键文件引用：
- `service/RequestService.java:40,172,247,472,516`
- `service/TeleportService.java:63,162,205,220,242`
- `KaTpaPlugin.java:307-364`（命令注册）、`listener/PlayerListener.java:27-104`

## 结论与建议

- 进行中重构：back/dback/home/warp 跨服传送先 `beginDirect` 吟唱再切服，需验证中断回滚。
- 新增 placeholder 模块需补充占位符清单。
