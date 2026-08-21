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
  home:
    enabled: true
    warmup: true
    warmup-seconds: 3
    sounds: true
    particles: true
    default-amount: 1
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
| `modules.dback.enabled` | `true` | Return to death location (/dback) |
| `modules.dback.default-amount` | `1` | Default death location save count without `katpa.dback.amount.<n>` permission |
| `modules.warp.enabled` | `true` | Public warp teleportation (/warp, /setwarp, /delwarp) |
| `modules.warp.default-permission` | `""` | Default permission node for new warps; blank means unrestricted |
| `modules.warp.default-cooldown` | `0` | Default cooldown in seconds for new warps |
| `modules.warp.default-cost` | `0` | Default teleport cost for new warps |
| `modules.home.enabled` | `true` | Personal home teleportation (/home, /sethome, /delhome) |
| `modules.home.default-amount` | `1` | Default home limit without `katpa.home.amount.<n>` permission |

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

## Reloading

After changing regular functional settings or language files, run:

```text
/katap reload
```

This command requires `katpa.admin`. Database type and connection changes require a server restart.
