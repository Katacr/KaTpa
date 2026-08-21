# Personal Homes

Each player can set their own home locations and teleport to them using `/home`.

## /home — Teleport to a Home

```text
/home
/home <name>
```

Without a name, opens a home selection dialog listing all available homes. With a name, teleports directly to the specified home.

## /sethome — Create or Manage Homes

```text
/sethome
/sethome <name>
```

Without a name, opens the home management dialog where players can view all homes, delete homes, or create new ones. With a name, creates or updates a home at the current location.

## /delhome — Delete a Home

```text
/delhome <name>
```

Deletes the specified home. Without a name, opens the management dialog.

## Home Limit

By default, 1 home can be set. Grant the following permissions through a permission plugin to increase the limit:

| Permission | Home limit |
| --- | --- |
| `katpa.home.amount.1` | 1 (default) |
| `katpa.home.amount.3` | 3 |
| `katpa.home.amount.5` | 5 |
| `katpa.home.amount.10` | 10 |

When a player holds multiple such permissions, the highest value is used.

## Cross-Server Home Teleportation

When `proxy.enabled` is on, `/home` can teleport across servers. If the target home is on another backend, KaProxy automatically switches the server and teleports the player to the exact coordinates. Cross-server home teleportation reuses the KaProxy Back module—no extra configuration needed.
