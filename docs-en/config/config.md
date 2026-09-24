# Server Configuration

KaTpa's functional settings are stored in `plugins/KaTpa/config.yml`.

## Feature Modules

Each feature module can be toggled independently. A disabled module does not initialize its service, register commands, or respond to events. Players running a disabled module's command will see a "this feature has been disabled by the administrator" message.

```yaml
modules:
  tpa:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    request-timeout-seconds: 30
    double-sneak-interval-seconds: 2
    cooldown:
      enabled: true
      seconds: 30
    allow-cross-world: true
    disabled-worlds: []
  back:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    min-distance: 16
    disabled-worlds: []
  dback:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    disabled-worlds: []
  warp:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    disabled-worlds: []
    default-permission: ""
    default-cooldown: 0
    default-cost: 0
    name-max-length: 32
    description-max-length: 100
  home:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
    bed-home: true
    bed-home-name: 重生点
    name-max-length: 32
    description-max-length: 100
    disabled-worlds: []
  pwarp:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
    total-slots: 10
    leaderboard-cache-size: 30
    create-cost:
      currency: money
      money: 0
      points: 0
    default-cost: 0
    cooldown-seconds: 30
    name-max-length: 32
    description-max-length: 100
    disabled-worlds: []
```

| Node | Default | Purpose |
| --- | --- | --- |
| `modules.tpa.enabled` | `true` | Teleport requests (/tpa, /tpahere, /tpaccept, /tpdeny, /tpacancel, /tpasetting) |
| `modules.tpa.request-timeout-seconds` | `30` | Seconds before a pending request expires |
| `modules.tpa.double-sneak-interval-seconds` | `2` | Maximum delay between the two sneak presses |
| `modules.tpa.cooldown.enabled` | `true` | Enables the request cooldown |
| `modules.tpa.cooldown.seconds` | `30` | Required delay between valid outgoing requests |
| `modules.tpa.allow-cross-world` | `true` | Allows teleports between worlds |
| `modules.tpa.disabled-worlds` | `[]` | World names that cannot send or receive teleport requests |
| `modules.back.enabled` | `true` | Return to previous location (/back) |
| `modules.back.min-distance` | `16` | For teleports within the same world, skip updating the /back point when the new location is closer than this many blocks to the old one; `0` always records |
| `modules.back.disabled-worlds` | `[]` | World names that cannot be returned to; `/back` locations are not recorded in these worlds either |
| `modules.dback.enabled` | `true` | Return to death location (/dback) |
| `modules.dback.disabled-worlds` | `[]` | World names that cannot be returned to; death locations are not recorded in these worlds either |
| `modules.warp.enabled` | `true` | Public warp teleportation (/warp, /setwarp, /delwarp) |
| `modules.warp.disabled-worlds` | `[]` | World names that cannot be teleported to; creating/updating warps is also blocked in these worlds |
| `modules.warp.default-permission` | `""` | Default permission node for new warps; blank means unrestricted |
| `modules.warp.default-cooldown` | `0` | Default cooldown in seconds for new warps |
| `modules.warp.default-cost` | `0` | Default teleport cost for new warps |
| `modules.warp.name-max-length` | `32` | Maximum warp name length (validated on rename/create) |
| `modules.warp.description-max-length` | `100` | Maximum warp description length (validated on set description) |
| `modules.pwarp.enabled` | `true` | Player warp teleportation (/pwarp, /pw, /katap pwarp) |
| `modules.pwarp.default-amount` | `1` | Default player warp creation limit without `katpa.pwarp.amount.<n>` permission |
| `modules.pwarp.total-slots` | `10` | Total slots shown in the player warp list (owned + empty + locked) |
| `modules.pwarp.leaderboard-cache-size` | `30` | Leaderboard cache size (top N); recomputed on create/delete/rate or `/pwarp admin reload` |
| `modules.pwarp.create-cost.currency` | `money` | Currency charged to create a player warp: `money` (Vault coins) or `points` (PlayerPoints) |
| `modules.pwarp.create-cost.money` | `0` | Coins charged to create a player warp when `currency=money` (0 disables) |
| `modules.pwarp.create-cost.points` | `0` | Points charged to create a player warp when `currency=points` (0 disables) |
| `modules.pwarp.default-cost` | `0` | Default teleport cost for new player warps |
| `modules.pwarp.cooldown-seconds` | `30` | Global player warp teleport cooldown in seconds (per player; cannot teleport again while active; 0 disables) |
| `modules.pwarp.name-max-length` | `32` | Maximum player warp name length (validated on rename/create) |
| `modules.pwarp.description-max-length` | `100` | Maximum player warp description length (validated on set description) |
| `modules.pwarp.disabled-worlds` | `[]` | World names that cannot be teleported to; creating/updating player warps is also blocked in these worlds |
| `modules.home.enabled` | `true` | Personal home teleportation (/home, /sethome, /delhome) |
| `modules.home.default-amount` | `1` | Default home limit without `katpa.home.amount.<n>` permission |
| `modules.home.bed-home` | `true` | Create/overwrite the home named `bed-home-name` when a player right-clicks a bed (day or night; right-clicking the same bed repeatedly does not update it again) |
| `modules.home.bed-home-name` | `重生点` | Home name auto-created on bed right-click |
| `modules.home.name-max-length` | `32` | Maximum home name length (validated on rename/create) |
| `modules.home.description-max-length` | `100` | Maximum home description length (validated on set description) |
| `modules.home.disabled-worlds` | `[]` | World names that cannot be teleported to; creating/updating homes is also blocked in these worlds |

Module toggle changes require a server restart to take effect.

## Warmup, Sounds, and Particles

The following three items are global configs shared by all modules. Each module can independently enable or disable them via `modules.<module>.warmup`, `modules.<module>.sounds`, and `modules.<module>.particles`.

| Node | Default | Purpose |
| --- | --- | --- |
| `modules.<module>.warmup` | `true` | Whether this module uses teleport warmup |
| `modules.<module>.warmup-seconds` | `3` | Warmup countdown seconds for this module |
| `modules.<module>.sounds` | `true` | Whether this module plays interaction sounds |
| `modules.<module>.particles` | `true` | Whether this module shows warmup particles |

### Sounds

Three sound groups can be enabled, disabled, or replaced independently:

* `sounds.request-received`: notification when a request arrives
* `sounds.countdown`: warm-up countdown sound
* `sounds.teleport`: successful teleport sound

Each group supports `enabled`, `sound`, `volume`, and `pitch`.

### Particles

`particles.warmup` controls the particles shown during teleport preparation. You can disable them or change their type, amount, and spread.

## Global Settings

| Node | Default | Purpose |
| --- | --- | --- |
| `language` | `zh_CN` | Language file selected from the `lang` folder |
| `server-id` | `local` | Backend display name (server alias). When creating homes/warps it is stored in the database alongside the real KaProxy server name; menus and messages show this alias, while cross-server teleports/transactions keep using the real name. If blank, falls back to the real name |

## World and Server Display Names

World and server names shown in menus and messages are localized for players:

* **World names**: if **Multiverse-Core** is installed (optional dependency), the world's alias is shown; otherwise (or when no alias is set) the Bukkit world name is used.
* **Server names**: when a home/warp is created, both the real KaProxy server name (used for teleport/transactions) and `server-id` (display alias) are stored; menus and messages show `server-id`, falling back to the real name when unset.

Multiverse-Core is accessed via reflection as an optional dependency; nothing breaks when it is absent.

## Language Files

All player messages and interface text are stored in `plugins/KaTpa/lang/`. To use a custom language:

1. Copy an existing language file and name it, for example, `my_lang.yml`.
2. Translate the text you want to change without renaming any nodes.
3. Set `language: my_lang` in `config.yml`.
4. Run `/katap reload`.

Within it, `gui.list.*` holds the **list menu entry** names/descriptions (warps/homes/player warps/requests/online players/relation lists), `gui.prompt.*` the chat-input prompts shown when editing fields from a menu, `gui.request-menu.*` the teleport-request hopper window, and `chat-input.*` the chat-input cancel/timeout notices. The text of the menu buttons themselves lives in `plugins/KaTpa/gui/*.yml` (see the "Menus" section).

## Data Storage

Single-server installations use SQLite by default and need no extra setup:

```yaml
storage:
  type: sqlite
```

Networks should use a MySQL or MariaDB database shared by every backend server. Restart the server after changing `storage.type` or `storage.mysql`.

KaTpa periodically validates its long-lived database connection and reconnects automatically after MySQL/MariaDB closes it because of an idle timeout or a temporary interruption. JDBC operations from all storage modules are serialized to prevent concurrent transactions from interfering with each other on the shared connection. A write that fails while already in progress is not replayed automatically, which avoids duplicating non-idempotent operations such as income increments. The failed connection is invalidated, the next database operation uses a new connection, and the original failure remains visible in the server log.

## Reloading

After changing regular functional settings or language files, run:

```text
/katap reload
```

This command requires `katpa.admin`. Database type and connection changes require a server restart.

## Text & icon format (GUI items, titles, messages)

- **Colors**: legacy codes (`&a`, `&l`, `&#RRGGBB`) and **MiniMessage** (e.g. `<white>`, `<gradient:#ff0000:#00ff00>`). Text containing MiniMessage tags is parsed as MiniMessage, otherwise as legacy codes.
- **CraftEngine glyphs**: `<image:namespace:id>` and `<shift:pixels>` are kept literally and substituted by CraftEngine when it intercepts packets; keep CE `network.intercept-packets.container` (GUI) and `item` set to `true`.
- **Inline item icon**: `&item:[item]` (requires MC 1.21.9+). `item` may be a vanilla material (`diamond`) or an external item id (`ce:general:pass`, `ia:namespace:id`, `oraxen:id`); its 2D texture is resolved automatically and the tag is removed on failure. Override complex icons via `item_sprites.yml`:
  ```yaml
  sprites:
    'ce:general:pass': 'items:general:item/pass'
    'oraxen:complex_staff': { atlas: 'minecraft:blocks', sprite: 'oraxen:item/complex_staff_icon' }
  ```
- **Item material**: the `material` field supports external item prefixes `ce:` / `craftengine:`, `ia:` / `itemsadder:`, `oraxen:` (e.g. `material: "ce:general:pass"`); on failure it falls back to the vanilla/default material.

> Older servers (< 1.21.9 or without a new enough Adventure) degrade gracefully: MiniMessage falls back to legacy codes, `&item:[...]` tags are removed.

