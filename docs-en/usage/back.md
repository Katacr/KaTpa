# Return Locations

KaTpa records teleport history and death locations so players can quickly return.

## /back — Return to Previous Location

```text
/back
```

Teleports the player to the location before their last teleport or disconnect. The previous location is recorded when:

* Any teleport (including those from other plugins) starts
* The player disconnects or switches backend servers

In cross-server mode, if the previous location is on another backend, KaProxy automatically switches the server and teleports the player to the exact coordinates.

## /dback — Return to Death Location

```text
/dback
/dback 1
/dback 2
```

Without a slot number, returns to the most recent death location. With a slot number, returns to the corresponding death location, starting from 1 (1 = most recent).

### Number of Death Locations

By default, 1 death location can be saved. Grant the following permissions through a permission plugin to increase the limit:

| Permission | Saved locations |
| --- | --- |
| `katpa.dback.amount.1` | 1 (default) |
| `katpa.dback.amount.3` | 3 |
| `katpa.dback.amount.5` | 5 |
| `katpa.dback.amount.10` | 10 |

When a player holds multiple such permissions, the highest value is used. New death locations are added to the front of the list; locations exceeding the limit are automatically removed from the back.

## Cross-Server Return

When `proxy.enabled` is on, `/back` and `/dback` can return across servers. The flow is:

1. KaTpa reads the target server name from the stored location.
2. If the player is not on the target backend, KaProxy switches the server.
3. After the player arrives at the target backend, KaTpa teleports the player to the exact coordinates.

Cross-server return requires the KaProxy Back module to be enabled.
