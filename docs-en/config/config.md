# Server Configuration

KaTpa's functional settings are stored in `plugins/KaTpa/config.yml`.

## Common Settings

| Node | Default | Purpose |
| --- | --- | --- |
| `request-timeout-seconds` | `30` | Seconds before a pending request expires |
| `language` | `zh_CN` | Language file selected from the `lang` folder |
| `warmup-seconds` | `3` | Teleport warm-up after a request is accepted |
| `double-sneak-interval-seconds` | `2` | Maximum delay between the two sneak presses |
| `cooldown.enabled` | `true` | Enables the request cooldown |
| `cooldown.seconds` | `30` | Required delay between valid outgoing requests |
| `allow-cross-world` | `true` | Allows teleports between worlds |
| `disabled-worlds` | `[]` | World names where KaTpa cannot be used |

Disabled-world example:

```yaml
disabled-worlds:
  - resource_world
  - event_world
```

World names are case-sensitive.

## Sounds

Three sound groups can be enabled, disabled, or replaced independently:

* `sounds.request-received`: notification when a request arrives
* `sounds.countdown`: warm-up countdown sound
* `sounds.teleport`: successful teleport sound

Each group supports `enabled`, `sound`, `volume`, and `pitch`.

## Particles

`particles.warmup` controls the particles shown during teleport preparation. You can disable them or change their type, amount, and spread.

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
