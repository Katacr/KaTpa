# Permissions

| Permission | Default | Description |
| --- | --- | --- |
| `katpa.use` | Everyone | Use request, accept, deny, and cancel commands |
| `katpa.setting` | Everyone | Open and change personal settings and lists |
| `katpa.admin` | OP | Use `/katap reload` |
| `katpa.cooldown.bypass` | OP | Ignore the outgoing request cooldown |
| `katpa.back` | Everyone | Use `/back` to return to the previous location |
| `katpa.dback` | Everyone | Use `/dback` to return to a death location |
| `katpa.dback.amount.<n>` | — | Allows saving n death locations; defaults to 1, takes the maximum value the player holds |
| `katpa.warp` | Everyone | Use `/warp` to teleport to a warp |
| `katpa.warp.admin` | OP | Use `/setwarp`, `/delwarp`, and `/katap warp ...` to manage warps (edit, create, delete, rename, icon, set fields) |
| `katpa.home` | Everyone | Use `/home`, `/sethome`, and `/delhome` to manage personal homes |
| `katpa.home.amount.<n>` | — | Allows setting n homes; defaults to 1, takes the maximum value the player holds |
| `katpa.pwarp.use` | Everyone | Use `/pwarp` to teleport to player warps |
| `katpa.pwarp.create` | Everyone | Create player warps |
| `katpa.pwarp.admin` | OP | Edit or delete any player warp (for cleaning up violating warps) |
| `katpa.pwarp.amount.<n>` | — | Allows creating n player warps; defaults to 1, takes the maximum value the player holds |

`katpa.dback.amount.*` is a dynamic permission and is not listed in `plugin.yml`. Granting `katpa.dback.amount.3` in a permission plugin allows the player to save 3 death locations. When a player holds multiple such permissions, the highest value is used.

The same applies to `katpa.home.amount.*`—granting `katpa.home.amount.5` allows the player to set 5 homes.

The same applies to `katpa.pwarp.amount.*`—granting `katpa.pwarp.amount.3` allows the player to create 3 player warps.

Permission plugins can remove default access or grant these permissions only to selected groups.
