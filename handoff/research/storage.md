# 数据模型与存储层调研

> 调研日期：2026-09-07

## 调研目的

理清 KaTpa 的数据模型设计与持久化策略。

## 范围与方法

- 阅读 `src/main/java/org/katacr/katpa/model/` 全部 11 个类。
- 阅读 `src/main/java/org/katacr/katpa/storage/` 全部 4 个 store。

## 关键发现

- 模型几乎全为不可变 record，坐标类含 `server` 字段支持跨服。
- 存储层单连接模式：SettingsStore 唯一物理连接，其余 store 共享。
- 写操作经各 store 单线程池异步落盘。
- 模块开关影响 Back/Warp/Home store 的初始化：Home 惰性加载、Warp 启动全量、Settings 始终。

## 详细结论

见交接文件 `storage.md`。关键文件引用：
- `storage/SettingsStore.java:50`（类型判定）、`:230`（共享连接）、`:166-186`（upsert 关系）
- `storage/WarpStore.java:81`（全量加载）、`storage/HomeStore.java:80`（惰性加载）
- `KaTpaPlugin.java:113-158`（模块编排）、`:281-283`（moduleEnabled）

## 结论与建议

- 新功能如需持久化，优先评估复用现有共享连接模式。
- MySQL 下跨服缓存刷新逻辑（refreshPlayer）需保证各子服数据一致。
