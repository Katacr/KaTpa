# Getting Started

## Installation

1. Make sure the server is running Java 21.
2. Place `KaTpa-1.0.0.jar` in the server's `plugins` folder.
3. Start or restart the server.
4. On first launch, KaTpa creates `plugins/KaTpa/config.yml`, `plugins/KaTpa/lang/`, and its player data file.
5. Once the console reports that KaTpa is enabled, players can use it in game.

The first launch may take longer because required runtime files can be downloaded automatically.

## Send Your First Request

Run:

```text
/tpa
```

Choose a target from the player dialog. You can also enter the player name directly:

```text
/tpa Steve
```

After the other player accepts, the traveling player enters a countdown. Moving or taking damage during the countdown cancels the teleport.

## Check Commands

Run `/katap help` to view in-game help. If regular players cannot use KaTpa, ask an administrator to check the `katpa.use` permission.

## Change Settings

Functional settings are stored in `plugins/KaTpa/config.yml`, while displayed text is stored in `plugins/KaTpa/lang/`. Most changes can be applied with:

```text
/katap reload
```

Restart the server after changing the storage type or database connection settings.
