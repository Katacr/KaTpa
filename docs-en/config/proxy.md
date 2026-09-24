# Cross-Server Teleportation

KaTpa can work with KaProxy so players can send requests to players on other backend servers. After acceptance, the traveling player completes the warm-up, switches server automatically, and arrives beside the target player.

## Requirements

* Velocity 3.4 or a compatible version, or BungeeCord 1.21
* The same KaTpa version on every backend server
* KaProxy installed on the proxy
* One MySQL or MariaDB database shared by every KaTpa backend

## Proxy Setup

1. Place `KaProxy-1.0.0.jar` in the proxy's `plugins` folder.
2. Start the proxy once so KaProxy creates its configuration.
3. Make sure the Tpa and Back modules are enabled:

```yaml
modules:
  tpa:
    enabled: true
  back:
    enabled: true
```

4. Restart the proxy or run `/kaproxy reload`.

## Backend Setup

Enable proxy support in `plugins/KaTpa/config.yml` on every backend server:

```yaml
proxy:
  enabled: true

storage:
  type: mysql
  mysql:
    host: 127.0.0.1
    port: 3306
    database: katpa
    username: katpa
    password: change-me
    use-ssl: false
```

Replace the sample database address and credentials, and use the same database settings on every backend. Restart all backend servers afterward.

In cross-server mode, the real server ID of each backend is obtained automatically from KaProxy—no need to manually configure `server-id`. The `server-id` in `config.yml` is only used for single-server mode or as a display name in the UI.

## Player Experience

Once enabled, the `/tpa` and `/tpahere` player menus contain players from the entire network. `/back` and `/dback` can also return across servers, and `/warp` and `/home` support cross-server teleportation too—the proxy switches the player to the target backend and teleports them to the exact coordinates. Whitelists, blacklists, response modes, cooldowns, and warm-up cancellation rules work the same way across servers. Cross-server teleports behave like local ones: the warm-up countdown (`modules.<module>.warmup-seconds`, 3 by default) always completes before the backend switch, and moving, taking damage, or disconnecting cancels it.

By default, if the target player changes backend during the warm-up, KaProxy follows their current server.

## Target Availability Check

Before any warm-up countdown starts, the destination is validated. If it is not reachable, the teleport is cancelled immediately with a message and no countdown runs:

* If the destination is on the current backend, the target world must be loaded; otherwise the player sees "target world ... is not loaded, teleport cancelled".
* If the destination is on another backend, that backend must currently be online (probed by KaProxy and broadcast in the presence snapshot); otherwise the player sees "target server ... is currently unavailable, teleport cancelled".
* If the proxy channel is unavailable, the player sees "Cross-server service is currently unavailable".

As a result, players no longer wait through the warm-up only to fail when the target world is unloaded or the target backend is offline.

## Warp and Player Warp Sync

When several backends share the same database, the `/warp` (public warps) and `/pwarp` (player warps) lists must stay consistent. KaTpa caches both lists in memory on each backend and keeps them in sync automatically:

* After a warp is created, edited, or deleted locally and successfully persisted, KaTpa broadcasts the change through KaProxy to the other backends (topics `warp` / `player_warp`); each backend then reloads the corresponding cache.
* When a player joins a backend, its caches are reloaded once (with a 2-second debounce), covering changes missed while that backend had no players online.

A warp created on one backend therefore appears on the other backends without a restart. `/home` is stored per player and reloaded from the database on every join, so it does not need cross-server sync.

## Troubleshooting

If players see "Cross-server service is currently unavailable," check:

1. KaProxy is enabled correctly on the proxy.
2. KaProxy's Tpa module is enabled (cross-server return also requires the Back module).
3. `proxy.enabled` is `true` on the current backend.
4. The player joined the backend through the proxy.
5. The proxy and backend consoles do not contain related errors.
