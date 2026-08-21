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

Once enabled, the `/tpa` and `/tpahere` player dialogs contain players from the entire network. `/back` and `/dback` can also return across servers, and `/warp` and `/home` support cross-server teleportation too—the proxy switches the player to the target backend and teleports them to the exact coordinates. Whitelists, blacklists, response modes, cooldowns, and warm-up cancellation rules work the same way across servers.

By default, if the target player changes backend during the warm-up, KaProxy follows their current server.

## Troubleshooting

If players see "Cross-server service is currently unavailable," check:

1. KaProxy is enabled correctly on the proxy.
2. KaProxy's Tpa module is enabled (cross-server return also requires the Back module).
3. `proxy.enabled` is `true` on the current backend.
4. The player joined the backend through the proxy.
5. The proxy and backend consoles do not contain related errors.
