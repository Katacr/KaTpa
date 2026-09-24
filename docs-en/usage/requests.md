# Teleport Requests

## Two Teleport Directions

| Command | Result | Example |
| --- | --- | --- |
| `/tpa [player]` | You teleport to the other player | `/tpa Steve` |
| `/tpahere [player]` | Invite the other player to your location | `/tpahere Alex` |

Omitting the player name opens the online player menu. When cross-server support is enabled, players on other backend servers also appear in the list.

## Accept or Deny

The receiver can use the interface buttons or run:

```text
/tpaccept
/tpdeny
```

`/tpaaccept` is an alias of `/tpaccept`, and `/tpadeny` is an alias of `/tpdeny`.

If you have multiple pending requests, accepting or denying without selecting one opens the request list. Every entry has its own buttons, so another player's request will not be handled by mistake.

## Cancel a Request

The request-sent message includes a clickable `[Cancel]` action. You can also run:

```text
/tpacancel
```

Each player can have only one outgoing pending request. Canceling does not refund an already-started request cooldown.

## Request Expiration

Requests must be handled within the time configured by the server owner. The pending list displays the remaining time and closes automatically after its final request is handled or expires.

## Teleport Warm-Up

After a request is accepted, the traveling player enters a warm-up period:

* The remaining seconds appear in the ActionBar
* A countdown sound plays once per second
* Enderman-style particles surround the player
* Changing position cancels the teleport; looking around in place does not
* Valid damage or either related player disconnecting cancels the teleport

An Enderman teleport sound plays when the teleport completes.

## Common Reasons a Request Cannot Be Sent

* The request cooldown is still active
* You already have a pending outgoing request
* The traveling player is already involved in another teleport
* KaTpa is disabled in the current world
* Cross-world teleportation is disabled
* The receiver has blacklisted you

## Cross-server and dungeon worlds

When the target player's world is blocked by the target server's `modules.tpa.disabled-worlds` (e.g. they are inside a dungeon), the **accept step is rejected directly on their server**, so the traveler is not switched first only to fail at the arrival stage and end up stranded on the target server.

- `/tpa`: the destination is the receiver → the receiver cannot accept while standing in a blacklisted world, and both sides are notified.
- `/tpahere`: the traveler is the receiver → the receiver (the traveler) cannot accept while standing in a blacklisted world.

> This check applies on the **target server** (whoever is being teleported into, their server's config is used). A final check is still kept at the arrival stage as a fallback.
