# Warp Teleportation

Administrators can set up public warp points that players can teleport to using `/warp`.

> `/warp` is for teleporting only and does not accept any admin subcommands. All warp editing is done via `/katap warp ...` or the in-GUI editor.

## /warp — Teleport to a Warp

```text
/warp
/warp <name>
/warp <name> <player>
```

Without a name, `katpa.warp.menu` is required to open the warp selection list showing all available warps (with description and icon); this permission is not granted to players by default. With a name, teleports directly to the specified warp.

Administrators with `katpa.warp.admin`, or the console, can use `/warp <name> <player>` to teleport a target who is **online on this backend**. The target runs through the normal `/warp` flow: warp permission, cost, cooldown, and destination availability are still checked, and the configured warmup runs on the target. Administrators do not skip these steps.

Each warp can have individually configured properties:

* **Description**: Explanatory text shown in the warp list
* **Icon**: Item shown in the list and editor (supports material, `custom_model_data`, and 1.21.4+ `item_model`)
* **Permission**: Only players holding the specified permission node can teleport (leave blank for everyone)
* **Cooldown**: Seconds to wait between two teleports to the same warp
* **Cost**: Economic amount deducted on teleport (requires a Vault economy plugin)

## /katap warp — Manage Warps

```text
/katap warp edit <name>
/katap warp create <name>
/katap warp delete <name>
/katap warp rename <old> <new>
/katap warp icon <name>
/katap warp set <name|permission|cooldown|cost|desc> <name> [value]
```

All `/katap warp` subcommands require `katpa.warp.admin`. The editor GUI lets you modify description, icon, permission, cooldown, cost, or rename the warp visually. Right-clicking a warp in the list also opens the editor (visible to `katpa.warp.admin` only).

* `edit` opens the graphical editor
* `create` creates a warp at the current location
* `delete` deletes a warp
* `rename` renames (old → new)
* `icon` sets the icon from the player's held item (captures material, `custom_model_data`, 1.21.4+ `item_model`)
* `set permission <name> [value]` clears the permission when value is blank; `set desc <name> <text>` accepts multi-word text; `set cooldown`/`set cost` set numeric values; `set name` is equivalent to `rename`

## /setwarp and /delwarp — Command Shortcuts

```text
/setwarp <name>
/delwarp <name>
```

`/setwarp` creates or updates a warp at the current location; `/delwarp` deletes a warp. These are command shortcuts—use `/katap warp` for full editing (description, icon, fields).

## Cross-Server Warp Teleportation

When `proxy.enabled` is on, warp teleportation works across servers. If the target warp is on another backend, KaProxy automatically switches the server and teleports the player to the exact coordinates. Cross-server warp teleportation reuses the KaProxy Back module—no extra configuration needed.

## Vault Economy

Warp costs are optional. When Vault and a compatible economy plugin are installed, the cost is automatically deducted on teleport. Without Vault, cost settings are ignored and players teleport for free.

## World Blacklist

`modules.warp.disabled-worlds` in `config.yml` lists **world names that cannot be teleported to** (case-sensitive). `/warp` is rejected when the destination world is blacklisted, and creating/updating a warp via `/setwarp` is blocked while the player stands in a blacklisted world.
