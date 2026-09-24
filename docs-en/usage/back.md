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
```

Returns to the most recent death location (only the latest one is kept, same as `/back`). On death, the player receives a clickable message "Your death location is ... [Click] to return"; clicking it returns there without typing a command.

## Cross-Server Return

When `proxy.enabled` is on, `/back` and `/dback` can return across servers. The flow is:

1. KaTpa reads the target server name from the stored location.
2. If the player is not on the target backend, KaProxy switches the server.
3. After the player arrives at the target backend, KaTpa teleports the player to the exact coordinates.

Cross-server return requires the KaProxy Back module to be enabled.

## World Blacklist

`modules.back.disabled-worlds` / `modules.dback.disabled-worlds` in `config.yml` list **world names that cannot be returned to** (case-sensitive, e.g. dungeon worlds):

- If the destination world is blacklisted, `/back` and `/dback` are rejected with a notice.
- While a player is **inside** a blacklisted world, no `/back` or death location is recorded, so no stale return point remains after the dungeon ends.
