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
