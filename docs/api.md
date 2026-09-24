# API 事件

KaTpa 提供一个公开 Bukkit 事件 `KaTpaEvent`，供其他插件（如 EcoQuests）监听家、地标、返回等传送操作，以驱动任务、统计等逻辑。

事件类位于 `org.katacr.katpa.api.event` 包。

## KaTpaEvent

在玩家**成功完成**操作后触发（创建家、传送到家/地标、back、dback 抵达目标坐标后）。事件在服务端主线程同步抛出，监听器内可直接调用 Bukkit API。

> **触发时机**：传送类操作在玩家**真正抵达目标坐标后**触发（本地传送 `teleport` 成功、跨服切服后落点完成），而非发起吟唱时。这样可避免玩家在吟唱中取消或传送失败时误计，也防止反复发起刷进度。

### 字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `player` | `Player` | 执行操作的玩家 |
| `action` | `Action` | 操作类型（见下） |
| `name` | `String` | 目标名称（家名 / 地标名）；`BACK`、`DBACK` 为 `null` |

### Action 枚举

| 值 | 含义 |
| --- | --- |
| `SET_HOME` | 成功创建/更新家 |
| `HOME` | 成功传送到家 |
| `WARP` | 成功传送到公共地标或玩家地标 |
| `BACK` | 成功返回上一位置 |
| `DBACK` | 成功返回死亡地点 |

## 监听示例

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.katacr.katpa.api.event.KaTpaEvent;

public class TpaListener implements Listener {

    @EventHandler
    public void onTpa(KaTpaEvent event) {
        if (event.getAction() == KaTpaEvent.Action.WARP) {
            // 玩家成功传送到地标，名称 = event.getName()
        }
    }
}
```

## 跨服支持

KaTpa 支持跨服传送。跨服时，事件在**目标服务器**上、玩家抵达落点后触发。请求中会携带动作类型（`home` / `warp` / `player_warp` / `back` / `dback`），代理转发后原样回传，因此目标服能精确区分触发对应事件。

## 与其他插件联动

EcoQuests 等任务插件可通过监听 `KaTpaEvent` 添加“创建家”、“传送到地标”、“使用 /back”等任务。

## 注意事项

1. 事件只在**操作成功后**触发；失败（无权限、目标不可用、传送失败等）不会触发。
2. `name` 仅在 `SET_HOME` / `HOME` / `WARP` 时非空。
3. 跨服传送的事件在目标服触发，请确保目标服也安装了监听该事件的插件。