# Warp Teleportation

Administrators can set up public warp points that players can teleport to using `/warp`.

## /warp — Teleport to a Warp

```text
/warp
/warp <name>
```

Without a name, opens a warp selection dialog listing all available warps. With a name, teleports directly to the specified warp.

Each warp can have individually configured properties:

* **Permission**: Only players holding the specified permission node can teleport (leave blank for everyone)
* **Cooldown**: Seconds to wait between two teleports to the same warp
* **Cost**: Economic amount deducted on teleport (requires a Vault economy plugin)

## /setwarp — Create or Manage Warps

```text
/setwarp
/setwarp <name>
```

Without a name, opens the warp management dialog where administrators can view all warps, edit properties, or create new ones. With a name, creates or updates a warp at the current location.

The management dialog allows:

* Updating the warp location
* Setting the permission node
* Setting the cooldown in seconds
* Setting the teleport cost
* Deleting the warp

## /delwarp — Delete a Warp

```text
/delwarp <name>
```

Deletes the specified warp. Without a name, opens the management dialog.

## Cross-Server Warp Teleportation

When `proxy.enabled` is on, warp teleportation works across servers. If the target warp is on another backend, KaProxy automatically switches the server and teleports the player to the exact coordinates. Cross-server warp teleportation reuses the KaProxy Back module—no extra configuration needed.

## Vault Economy

Warp costs are optional. When Vault and a compatible economy plugin are installed, the cost is automatically deducted on teleport. Without Vault, cost settings are ignored and players teleport for free.
