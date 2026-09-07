# PlaceholderAPI Placeholders

When PlaceholderAPI is installed, KaTpa automatically registers the `katpa` prefix placeholders, usable in any PAPI-compatible plugin (holograms, scoreboards, menus).

Placeholders are ordered by creation time — the earliest created comes first (`%katpa_home_1%` is the oldest home).

## Home

| Placeholder | Description |
| --- | --- |
| `%katpa_home_<n>%` | All fields of the n-th home, comma-separated: `server,world,x,y,z,yaw,pitch` |
| `%katpa_home_<n>_location%` | Brief location, comma-separated: `world,x,y,z` (no server or rotation) |
| `%katpa_home_<n>_server%` | Server ID |
| `%katpa_home_<n>_world%` | World name |
| `%katpa_home_<n>_x%` | X coordinate |
| `%katpa_home_<n>_y%` | Y coordinate |
| `%katpa_home_<n>_z%` | Z coordinate |
| `%katpa_home_<n>_yaw%` | Yaw |
| `%katpa_home_<n>_pitch%` | Pitch |

## Warp

| Placeholder | Description |
| --- | --- |
| `%katpa_warp_<n>%` | All fields of the n-th warp, comma-separated |
| `%katpa_warp_<n>_location%` | Brief location, comma-separated: `world,x,y,z` (no server or rotation) |
| `%katpa_warp_<n>_server%` | Server ID |
| `%katpa_warp_<n>_world%` | World name |
| `%katpa_warp_<n>_x%` | X coordinate |
| `%katpa_warp_<n>_y%` | Y coordinate |
| `%katpa_warp_<n>_z%` | Z coordinate |
| `%katpa_warp_<n>_yaw%` | Yaw |
| `%katpa_warp_<n>_pitch%` | Pitch |

## Last Teleport Location (Back)

Records the player's location before the last teleport (single entry).

| Placeholder | Description |
| --- | --- |
| `%katpa_back%` | All fields, comma-separated: `server,world,x,y,z,yaw,pitch` |
| `%katpa_back_location%` | Brief location, comma-separated: `world,x,y,z` (no server or rotation) |
| `%katpa_back_server%` | Server ID |
| `%katpa_back_world%` | World name |
| `%katpa_back_x%` | X coordinate |
| `%katpa_back_y%` | Y coordinate |
| `%katpa_back_z%` | Z coordinate |
| `%katpa_back_yaw%` | Yaw |
| `%katpa_back_pitch%` | Pitch |

## Death Location (Dback)

| Placeholder | Description |
| --- | --- |
| `%katpa_dback_<n>%` | Full info of the n-th death location, comma-separated |
| `%katpa_dback_<n>_location%` | Brief location, comma-separated: `world,x,y,z` (no server or rotation) |
| `%katpa_dback_<n>_server%` | Server ID |
| `%katpa_dback_<n>_world%` | World name |
| `%katpa_dback_<n>_x%` | X coordinate |
| `%katpa_dback_<n>_y%` | Y coordinate |
| `%katpa_dback_<n>_z%` | Z coordinate |
| `%katpa_dback_<n>_yaw%` | Yaw |
| `%katpa_dback_<n>_pitch%` | Pitch |

## General

| Placeholder | Description |
| --- | --- |
| `%katpa_mode%` | The current player's request response mode, returns the localized name: `Dialog` / `Chat` / `Double Sneak` |

> Index `<n>` starts at 1; returns empty string when exceeding the actual count. Integer coordinates omit the decimal part.
