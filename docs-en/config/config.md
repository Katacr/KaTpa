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
  dback:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
  warp:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
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
  pwarp:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
    default-cost: 0
    default-cooldown: 0
    name-max-length: 32
    description-max-length: 100
```

| Node | Default | Purpose |
| --- | --- | --- |
| `modules.tpa.enabled` | `true` | Teleport requests (/tpa, /tpahere, /tpaccept, /tpdeny, /tpacancel, /tpasetting) |
| `modules.tpa.request-timeout-seconds` | `30` | Seconds before a pending request expires |
| `modules.tpa.double-sneak-interval-seconds` | `2` | Maximum delay between the two sneak presses |
| `modules.tpa.cooldown.enabled` | `true` | Enables the request cooldown |
| `modules.tpa.cooldown.seconds` | `30` | Required delay between valid outgoing requests |
| `modules.tpa.allow-cross-world` | `true` | Allows teleports between worlds |
| `modules.tpa.disabled-worlds` | `[]` | World names where KaTpa cannot be used |
| `modules.back.enabled` | `true` | Return to previous location (/back) |
| `modules.back.min-distance` | `16` | For teleports within the same world, skip updating the /back point when the new location is closer than this many blocks to the old one; `0` always records |
| `modules.dback.enabled` | `true` | Return to death location (/dback) |
| `modules.dback.default-amount` | `1` | Default death location save count without `katpa.dback.amount.<n>` permission |
| `modules.warp.enabled` | `true` | Public warp teleportation (/warp, /setwarp, /delwarp) |
| `modules.warp.default-permission` | `""` | Default permission node for new warps; blank means unrestricted |
| `modules.warp.default-cooldown` | `0` | Default cooldown in seconds for new warps |
| `modules.warp.default-cost` | `0` | Default teleport cost for new warps |
| `modules.warp.name-max-length` | `32` | Maximum warp name length (validated on rename/create) |
| `modules.warp.description-max-length` | `100` | Maximum warp description length (validated on set description) |
| `modules.pwarp.enabled` | `true` | Player warp teleportation (/pwarp, /katap pwarp) |
| `modules.pwarp.default-amount` | `1` | Default player warp creation limit without `katpa.pwarp.amount.<n>` permission |
| `modules.pwarp.default-cost` | `0` | Default teleport cost for new player warps |
| `modules.pwarp.default-cooldown` | `0` | Default cooldown in seconds for new player warps |
| `modules.pwarp.name-max-length` | `32` | Maximum player warp name length (validated on rename/create) |
| `modules.pwarp.description-max-length` | `100` | Maximum player warp description length (validated on set description) |
| `modules.home.enabled` | `true` | Personal home teleportation (/home, /sethome, /delhome) |
| `modules.home.default-amount` | `1` | Default home limit without `katpa.home.amount.<n>` permission |
| `modules.home.bed-home` | `true` | Create/overwrite the home named `bed-home-name` when a player sleeps and sets their personal respawn point (first time using that bed) |
| `modules.home.bed-home-name` | `重生点` | Home name auto-created when the player sets their respawn point in a bed |
| `modules.home.name-max-length` | `32` | Maximum home name length (validated on rename/create) |
| `modules.home.description-max-length` | `100` | Maximum home description length (validated on set description) |

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
| `server-id` | `local` | Display name for UI purposes only. In cross-server mode, the real server ID is obtained automatically from KaProxy |

## Language Files

All player messages and interface text are stored in `plugins/KaTpa/lang/`. To use a custom language:

1. Copy an existing language file and name it, for example, `my_lang.yml`.
2. Translate the values without changing node names.
3. Set `language: my_lang` in `config.yml`.
4. Run `/katap reload`.

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
