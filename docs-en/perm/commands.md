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
| `/warp [name]` | Teleport to a warp; omit the name to open the selection list (teleport only, no admin subcommands) |
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
| `/katap warp edit <name>` | Open the warp editor GUI (description, icon, permission, cooldown, cost, rename) | `katpa.warp.admin` |
| `/katap warp create <name>` | Create a warp at the current location | `katpa.warp.admin` |
| `/katap warp delete <name>` | Delete a warp | `katpa.warp.admin` |
| `/katap warp rename <old> <new>` | Rename a warp | `katpa.warp.admin` |
| `/katap warp icon <name>` | Set the warp icon from the player's held item | `katpa.warp.admin` |
| `/katap warp set <field> <name> [value]` | Modify a warp field (`name`/`permission`/`cooldown`/`cost`/`desc`); `desc` accepts multi-word text, `permission` left blank to clear | `katpa.warp.admin` |
| `/setwarp [name]` | Create or update a warp at the current location (command shortcut) | `katpa.warp.admin` |
| `/delwarp [name]` | Delete a warp | `katpa.warp.admin` |
| `/sethome [name]` | Create or manage personal homes | `katpa.home` |
| `/delhome [name]` | Delete a personal home | `katpa.home` |

## Player Warp Commands

Players can create their own public warps; others can browse, teleport, and rate them. Creators earn teleport fees.

| Command | Description |
| --- | --- |
| `/pwarp` | Open the player warp selection list (with global list and leaderboard entries) |
| `/pwarp <name>` | Teleport to the specified player warp |
| `/katap pwarp edit <name>` | Open your warp editor (description, icon, cost, cooldown, rename) |
| `/katap pwarp create <name>` | Create a player warp at the current location |
| `/katap pwarp delete <name>` | Delete your warp (admins can delete any warp) |
| `/katap pwarp rename <old> <new>` | Rename your warp |
| `/katap pwarp icon <name>` | Set the warp icon from the player's held item |
| `/katap pwarp rate <name> <1-5>` | Rate a warp (1-5 stars) |
| `/katap pwarp set <field> <name> [value]` | Modify a field (`name`/`cost`/`cooldown`/`desc`); `desc` accepts multi-word text |

> The creation limit is controlled by the `katpa.pwarp.amount.<n>` permission (default 1). `katpa.pwarp.admin` can edit or delete any player warp (for cleaning up violating warps).

> `/warp` is for teleporting only. All warp editing is done via `/katap warp ...`. Right-clicking a warp in the GUI also opens the editor (visible to `katpa.warp.admin` only).

Restart the server instead of only reloading after changing the storage type or database connection.
