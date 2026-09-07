# UI 交互层与网络层调研

> 调研日期：2026-09-07

## 调研目的

理清 KaTpa 的 Dialog 平台抽象、三种接受方式、跨服 KaProxy 通讯。

## 范围与方法

- 阅读 `src/main/java/org/katacr/katpa/ui/` 全部文件（含 paper/spigot 适配器）。
- 阅读 `src/main/java/org/katacr/katpa/network/` 全部文件。
- 阅读 `src/main/java/org/katacr/katpa/util/` 的 MessageService、ConfigUpdater。
- 阅读 `src/main/resources/` 的 plugin.yml、config.yml、lang。

## 关键发现

- InteractionPlatform 接口平台中立，InteractionService 反射探测选 Paper/Spigot 实现。
- Paper 用 Adventure ClickCallback，Spigot 用 PlayerCustomClickEvent 校验一次性点击。
- 三种接受方式 dialog/chat/sneak 由接收者偏好分派。
- 跨服经 `kaproxy:main` 通道与 KaProxy 代理端通讯，版本化二进制协议，30s 心跳拉在线玩家。

## 详细结论

见交接文件 `ui.md` 与 `network.md`。关键文件引用：
- `ui/InteractionService.java:113-142`（平台探测）
- `network/KaProxyProtocol.java:12-58`、`network/CrossServerService.java:27,214-301`
- `util/MessageService.java:32-131`、`util/ConfigUpdater.java:22-108`

## 结论与建议

- 单 JAR 跨平台依赖反射探测，新增 UI 功能须保持平台中立签名。
- 跨服网络配置（proxy/storage）变更需重启插件。
