# API Events

KaTpa exposes a public Bukkit event `KaTpaEvent` for other plugins (such as EcoQuests) to listen to home, warp, and return teleport actions, e.g. to drive quests or statistics.

The event class lives in the `org.katacr.katpa.api.event` package.

## KaTpaEvent

Fired **after** a player successfully completes an action (creating a home, teleporting to a home/warp, returning via back/dback after arriving at the target coordinates). The event is thrown synchronously on the server main thread, so Bukkit APIs are safe inside listeners.

> **Timing**: Teleport actions fire **after the player actually arrives at the target coordinates** (local `teleport` success, or cross-server landing completed after switching servers), not when the warmup starts. This avoids miscounting when the warmup is cancelled or the teleport fails, and prevents farming progress by repeatedly initiating teleports.

### Fields

| Field | Type | Description |
| --- | --- | --- |
| `player` | `Player` | The player who performed the action |
| `action` | `Action` | Action type (see below) |
| `name` | `String` | Target name (home / warp name); `null` for `BACK` and `DBACK` |

### Action Enum

| Value | Meaning |
| --- | --- |
| `SET_HOME` | Home created/updated successfully |
| `HOME` | Teleported to a home successfully |
| `WARP` | Teleported to a public or player warp successfully |
| `BACK` | Returned to the previous location successfully |
| `DBACK` | Returned to the death location successfully |

## Listening Example

```java
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.katacr.katpa.api.event.KaTpaEvent;

public class TpaListener implements Listener {

    @EventHandler
    public void onTpa(KaTpaEvent event) {
        if (event.getAction() == KaTpaEvent.Action.WARP) {
            // Player teleported to a warp, name = event.getName()
        }
    }
}
```

## Cross-Server Support

KaTpa supports cross-server teleportation. Across servers, the event fires on the **target server** after the player lands. The request carries the action type (`home` / `warp` / `player_warp` / `back` / `dback`), which the proxy forwards back unchanged, so the target server can precisely distinguish and fire the matching event.

## Integration With Other Plugins

Quest plugins such as EcoQuests can add "create a home", "teleport to a warp", "use /back" and similar quests by listening to `KaTpaEvent`.

## Notes

1. The event fires only after a **successful** action; failures (no permission, target unavailable, teleport failed, etc.) do not fire.
2. `name` is non-null only for `SET_HOME` / `HOME` / `WARP`.
3. Cross-server events fire on the target server — make sure the listening plugin is installed there too.