# Commands

## Player Commands

| Command | Description |
| --- | --- |
| `/tpa [player]` | Request a teleport to the target; omit the name to open the player list |
| `/tpahere [player]` | Invite the target to your location; omit the name to open the player list |
| `/tpaccept` | Accept a request; opens the pending list when multiple requests exist |
| `/tpdeny` | Deny a request; opens the pending list when multiple requests exist |
| `/tpacancel` | Cancel your outgoing pending request |
| `/back` | Return to your previous location |
| `/dback [slot]` | Return to a death location; slot starts at 1, defaults to the most recent |
| `/warp [name]` | Teleport to a warp; omit the name to open the selection list |
| `/home [name]` | Teleport to a personal home; omit the name to open the selection list |
| `/tpasetting` | Open personal settings |
| `/tpasetting mode <dialog\|chat\|sneak>` | Change the request response mode |
| `/tpasetting <whitelist\|blacklist>` | Open a list management dialog |
| `/tpasetting <whitelist\|blacklist> <add\|remove> <player>` | Add or remove a list entry |
| `/katap help` | View in-game command help |

Compatibility aliases: `/tpaaccept`, `/tpadeny`, and `/tpasettings`.

## Administrator Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/katap reload` | Reload functional settings, language files, and the proxy toggle | `katpa.admin` |
| `/setwarp [name]` | Create or manage warps | `katpa.warp.admin` |
| `/delwarp [name]` | Delete a warp | `katpa.warp.admin` |
| `/sethome [name]` | Create or manage personal homes | `katpa.home` |
| `/delhome [name]` | Delete a personal home | `katpa.home` |

Restart the server instead of only reloading after changing the storage type or database connection.
