# FAQ

## Why is the `/tpa` player list empty?

Single-server mode lists only other players on the current server. Cross-server mode also requires KaProxy to be running with its Tpa module enabled.

## Why can't I send requests repeatedly?

The server owner may have enabled a cooldown, or you may already have an outgoing pending request. Wait for the displayed time or use `/tpacancel` first.

## Why did `/tpaccept` open a list?

You have multiple pending requests. Select a specific player so the wrong request is not accepted.

## Why was my teleport canceled?

Changing position, taking valid damage, or either related player disconnecting during the warm-up cancels it. A disabled world or cross-world restriction can also prevent completion.

## What is the difference between the whitelist and blacklist?

The whitelist automatically accepts requests from that player, while the blacklist automatically denies them. Adding a player to one list removes them from the other.

## Why can't I find an offline player in list management?

The player must have joined the current server at least once before KaTpa can remember them. The add-player dialog lists currently online players by default.

## Why didn't my database configuration change take effect?

The storage type and connection settings are read when the plugin starts. Restart the server after changing them.

## Why do I see `players.db-wal` and `players.db-shm`?

KaTpa uses a single-file mode by default, so normally only `players.db` remains. If temporary files were created by another tool, shut the server down normally before checking them. Never delete database files while the server is running.

## `/back` says there is no previous location

The player has never been teleported, has never disconnected, or the world at the previous location has been unloaded. In cross-server mode, make sure the KaProxy Back module is enabled.

## `/dback` only saves one death location

The default permission `katpa.dback.amount.1` only allows 1 death location. Grant `katpa.dback.amount.3` or a higher value in a permission plugin to increase the limit.

## Cross-server `/back` or `/dback` fails

Check that the KaProxy Back module is enabled, `proxy.enabled` is `true`, the target backend is online, and the world is loaded.

## `/warp` says insufficient funds

The warp has a teleport cost set, which requires a Vault economy plugin and sufficient account balance. Administrators can adjust or remove the cost through the `/setwarp` management dialog.

## `/sethome` says the home limit is reached

The default permission `katpa.home.amount.1` only allows 1 home. Grant `katpa.home.amount.3` or a higher value in a permission plugin to increase the limit.

## Cross-server `/warp` or `/home` fails

Cross-server warp and home teleportation reuses the KaProxy Back module. Make sure the Back module is enabled, `proxy.enabled` is `true`, the target backend is online, and the world is loaded.

## How to disable unused features

Set the corresponding module's `enabled` to `false` under the `modules` node in `plugins/KaTpa/config.yml`. For example, to disable warps and homes:

```yaml
modules:
  warp:
    enabled: false
  home:
    enabled: false
```

Restart the server after changing. Disabled modules do not register commands; players running the corresponding command will see a "this feature has been disabled by the administrator" message.
