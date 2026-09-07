# Player Warps

Players can create their own public warps. Other players can browse all warps, teleport to them, and rate them. Creators earn the teleport fee paid by others.

> Player warps are separate from admin warps (`/warp`), which are managed by `katpa.warp.admin`.

## /pwarp — Browse and Teleport

```text
/pwarp
/pwarp <name>
```

Without a name, opens the player warp selection list showing all player warps on the server (with creator, description, icon, average stars, and score). With a name, teleports directly to the specified warp.

Interactions in the list:

* **Left click** teleports (free for the creator; others pay the warp's `cost`)
* **Right click** opens the rating menu (1-5 stars)
* Button **B** (My Warps) opens the list of warps you created
* Button **L** (Leaderboard) opens the top-10 player warps by score

## Creating Your Own Warp

```text
/katap pwarp create <name>
```

Creates a player warp at your current location. The creation limit is controlled by the `katpa.pwarp.amount.<n>` permission (default 1).

## Managing Your Warp

Open "My Warps" (or click **B** after `/pwarp`), then click a warp to open the editor, where you can change:

* **Name**: `/katap pwarp rename <old> <new>`
* **Description**: the D button in the editor, or `/katap pwarp set desc <name> <text>`
* **Cost**: the F button in the editor, or `/katap pwarp set cost <name> <amount>`
* **Cooldown**: the C button in the editor, or `/katap pwarp set cooldown <name> <seconds>`
* **Icon**: the I button in the editor (set from your held item; supports material, `custom_model_data`, 1.21.4+ `item_model`)

Delete your warp: `/katap pwarp delete <name>`.

## Rating and Leaderboard

Any player can rate any player warp 1-5 stars (right-click the warp, or `/katap pwarp rate <name> <1-5>`). Scores accumulate by weight:

| Stars | Score |
| --- | --- |
| 5★ | +10 |
| 4★ | +5 |
| 3★ | +1 |
| 2★ | -5 |
| 1★ | -10 |

The leaderboard (click **L** after `/pwarp`) shows the top 10 warps by total weighted score, including rank, creator, description, average stars, and teleport cost. Each player can rate a warp only once; re-rating overwrites the previous value.

## Teleport Income

When other players teleport to your warp, they pay the warp's `cost` to you:

* If you are online, the fee is deposited directly (requires a Vault economy plugin).
* If you are offline (possibly on another backend or entirely offline), the fee is recorded as pending income and automatically claimed with a notice the next time you join any backend.

Without Vault, cost settings are ignored, players teleport for free, and no income is settled.

## Admin Cleanup

Admins with `katpa.pwarp.admin` can edit or delete any player warp to clean up meaningless or violating warps:

```text
/katap pwarp delete <name>
/katap pwarp edit <name>
```
