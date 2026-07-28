# Commands

## Player Commands

| Command | Description |
| --- | --- |
| `/tpa [player]` | Request a teleport to the target; omit the name to open the player list |
| `/tpahere [player]` | Invite the target to your location; omit the name to open the player list |
| `/tpaccept` | Accept a request; opens the pending list when multiple requests exist |
| `/tpdeny` | Deny a request; opens the pending list when multiple requests exist |
| `/tpacancel` | Cancel your outgoing pending request |
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

Restart the server instead of only reloading after changing the storage type or database connection.
