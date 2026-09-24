# Player Warps

Players can create their own public warps. Other players can browse all warps, teleport to them, and rate them. Creators earn the teleport fee paid by others.

> Player warps are separate from admin warps (`/warp`), which are managed by `katpa.warp.admin`.

## /pw and /pwarp — Browse and Teleport

```text
/pw <name>      # quick teleport by name
/pwarp          # opens the leaderboard
/pwarp <sub>    # leaderboard / favorites / mine / history / admin
```

* `/pw <name>` teleports directly to the named player warp (free for the creator; others pay `cost`; a clickable confirmation is shown first when a fee applies).
* `/pwarp` with no arguments opens the **leaderboard**.
* `/pwarp leaderboard` (aliases `top`/`rank`) opens the leaderboard.
* `/pwarp favorites` (aliases `favorite`/`fav`) opens your favorites.
* `/pwarp mine` (aliases `my`/`manager`) opens your warp management list.
* `/pwarp history` (alias `his`) opens your teleport history.

Entries in player warp lists (main list, history, favorites, by-player, leaderboard) share the same interactions:

* **Left click** teleport
* **Right click** rate (opens the 1-5 star rating menu)
* **Q key** (drop key) favorite / unfavorite

> Entries in the "My Warps" management list remain **left/right click to edit**, keeping the in-GUI edit entry.

## Fee Confirmation

When the target warp has a teleport fee and you are not its creator, `/pw <name>` first sends a clickable message:

```text
Warp <name> costs <amount> coins to teleport. Continue? [Confirm] [Cancel]
```

Teleport and charge only after clicking **[Confirm]**; click **[Cancel]** to abort (the message expires after 30 seconds).

## Teleport Cooldown

Player warp teleports use a **global cooldown** (`modules.pwarp.cooldown-seconds`, default 30 seconds), tracked **per player**: during the cooldown that player cannot teleport via `/pwarp`/`/pw` again (the remaining seconds are shown), even to a different warp. Set it to `0` to disable.

> The cooldown is not per-warp, so the warp editor no longer offers a separate cooldown setting.

## Teleport History

Click **H** in the player warp list to see the player warps you have **successfully teleported to** before (ordered by most recent visit, deduplicated). Click an entry to teleport again, right-click to favorite/unfavorite.

## Filter by Player

Click **P** in the player warp list to see all creators who own at least one player warp (sorted by name). Click a creator to view all of their warps in a filtered list, where you can teleport and favorite.

## Favorites

Players can manually favorite player warps:

* In the main list, history list, or by-player list, press the **Q key** (drop key) to favorite/unfavorite a warp.
* Open "My Favorites" (**F**) to list all favorited warps; press Q to unfavorite, left-click to teleport.

## Creating Your Own Warp

```text
/katap pwarp create <name>
/pwarp admin edit <name>   # open any warp's editor (admin)
```

Creates a player warp at your current location. The creation limit is controlled by the `katpa.pwarp.amount.<n>` permission (default 1). If a creation fee is configured (see below), the chosen currency is charged on creation; creation fails when the balance is insufficient.

### Creation Fee (Optional)

`modules.pwarp.create-cost` in `config.yml` can require coins or points to create a player warp (choose one):

```yaml
modules:
  pwarp:
    create-cost:
      currency: money   # money = Vault coins; points = PlayerPoints
      money: 0          # coin amount when currency=money
      points: 0         # point amount when currency=points
```

* An amount of `0`, or a missing prerequisite (Vault / PlayerPoints), disables the fee.
* Points require the **PlayerPoints** plugin (an optional dependency; ignored when absent).

## Managing Your Warp

Open "My Warps" (`/pwarp mine`), then left- or right-click a warp to open the editor, where you can change:

* **Name**: `/katap pwarp rename <old> <new>`
* **Description**: the D button in the editor, or `/katap pwarp set desc <name> <text>`
* **Cost**: the F button in the editor, or `/katap pwarp set cost <name> <amount>`
* **Update location**: the U button in the editor (updates the warp to your current location)
* **Icon**: the I button in the editor (set from your held item; supports material, `custom_model_data`, 1.21.4+ `item_model`)
* **Delete**: the R button in the editor (delete your own warp); admins also see the Q button (force delete)

Delete your warp: the **R** button in the editor, or `/katap pwarp delete <name>`.

## Admin Cleanup

Admins with `katpa.pwarp.admin` can edit or delete any player warp to clean up meaningless or violating warps. In any player warp's editor (opened from the "My Warps" list), admins additionally see the **Q (admin force delete)** button, which deletes the warp regardless of its creator. The commands below also work:

```text
/pwarp admin edit <name>     # open any warp's editor
/pwarp admin delete <name>   # force delete any warp
/pwarp admin reload          # reload warp data and recompute the leaderboard cache
```

`/pwarp admin reload` only reloads player warp data and recomputes the leaderboard cache; it does not reload config, language, or menus (use `/katap reload` for those).

## Rating and Leaderboard

Any player can rate any player warp 1-5 stars (right-click the warp, or `/katap pwarp rate <name> <1-5>`). Scores accumulate by weight:

| Stars | Score |
| --- | --- |
| 5★ | +10 |
| 4★ | +5 |
| 3★ | +1 |
| 2★ | -5 |
| 1★ | -10 |

The leaderboard (`/pwarp`) shows the top `modules.pwarp.leaderboard-cache-size` (default 30) warps by total weighted score, including rank, creator, description, average stars, and teleport cost. Each player can rate a warp only once; re-rating overwrites the previous value.

Leaderboard results are served from a cache: it is recomputed when a player warp is created or deleted, or when a rating succeeds, and can be recomputed manually with `/pwarp admin reload`. In a cross-server setup, changes are broadcast to other backends so their caches refresh too.

## Teleport Income

When other players teleport to your warp, they pay the warp's `cost` to you:

* If you are online, the fee is deposited directly (requires a Vault economy plugin).
* If you are offline (possibly on another backend or entirely offline), the fee is recorded as pending income and automatically claimed with a notice the next time you join any backend.

Without Vault, cost settings are ignored, players teleport for free, and no income is settled.

## World Blacklist

`modules.pwarp.disabled-worlds` in `config.yml` lists **world names that cannot be teleported to** (case-sensitive). `/pw` and `/pwarp` are rejected when the destination world is blacklisted, and creating/updating a player warp is blocked while the player stands in a blacklisted world.
