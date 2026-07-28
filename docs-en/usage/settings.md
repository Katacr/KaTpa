# Personal Settings

Open the settings dialog with:

```text
/tpasetting
```

The main screen lets you select a request response mode and displays the number of whitelist and blacklist entries. Settings are saved automatically and remain after reconnecting.

## Response Modes

### Dialog

Opens a native dialog when a request arrives. Multiple requests are listed separately with accept and deny actions.

### Clickable Chat

Displays clickable `[Accept]` and `[Deny]` actions in chat. This is useful when you want to keep watching the game.

### Double Sneak

Press the sneak key twice within the interval configured by the server owner to accept. If multiple requests are pending, KaTpa opens the request list to prevent accepting the wrong one.

You can also switch modes with commands:

```text
/tpasetting mode dialog
/tpasetting mode chat
/tpasetting mode sneak
```

## Whitelist

Requests from whitelisted players are accepted automatically. Use the Whitelist Management button to view entries, remove entries, or add an online player.

Command equivalents:

```text
/tpasetting whitelist
/tpasetting whitelist add <player>
/tpasetting whitelist remove <player>
```

## Blacklist

Requests from blacklisted players are denied automatically. Use the Blacklist Management button to view or change the list.

Command equivalents:

```text
/tpasetting blacklist
/tpasetting blacklist add <player>
/tpasetting blacklist remove <player>
```

You cannot add yourself. A player can appear in only one list; adding them to one list automatically removes them from the other.
