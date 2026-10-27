# zpizzatpa

> Teleport requests for ZpizzaSMP, built on native in-game dialog menus.

**zpizzatpa** is a teleport request plugin (TPA) for Minecraft servers. Instead of chest GUIs, it uses the game's native **Dialog screens**, so players pick who to teleport to from a clean menu with player faces, pagination and a search bar.

**Authors:** Zynyhero, Claude

## Features

- **Native dialog menus** – no inventory GUIs. `/tpa` and `/tpahere` open a player picker.
- **Player faces** next to each nickname (Minecraft 1.21.9+), without rank prefixes. Buttons grow wider for long nicknames.
- **Confirmation screen** before sending a request, with green *Accept* and red *Cancel* buttons side by side.
- **Pagination and search** – when there are more players than fit on one page, a navigation row (`←  Page x/y  →`) and a search bar appear.
- **Clickable chat requests** – the receiver gets a gold message with bold `ACCEPT` / `DENY` buttons they can click.
- **Bedrock support** – Bedrock players (Geyser/Floodgate) can't click chat buttons, so they see `/tpaccept <player>` and `/tpdeny <player>` instead. Detected through Floodgate, or by UUID/prefix as a fallback.
- **Vanish aware** – vanished players are hidden from the menu, tab completion, direct requests, `/tpall` and `/tpaall` (EssentialsX, SuperVanish, PremiumVanish and anything using `hidePlayer`).
- **Real command arguments** – commands use Brigadier, so the client shows the `<jugador>` argument with live suggestions. `/tpaccept` and `/tpdeny` only suggest players who sent you a request.
- **Fully configurable messages** – every text lives in `messages.yml` (MiniMessage format).
- **LuckPerms friendly** – one permission node per command, declared in `plugin.yml` so LuckPerms picks them up automatically.
- **Folia compatible** – uses entity schedulers and `teleportAsync`.

## Requirements

| | |
|---|---|
| Server software | **Paper**, **Purpur** or **Folia** (or another Paper fork) |
| Minecraft | 1.21.8 – 26.3 |
| Java | 21 or newer (25 on 26.x servers) |
| Optional | LuckPerms, Floodgate |

> ⚠️ **Not compatible with Spigot / CraftBukkit.** The plugin relies on Paper's Dialog API and Brigadier command API.
>
> On 1.21.8 the player faces are not shown (text-embedded heads were added in 1.21.9).

## Installation

1. Download `zpizzatpa.jar` (or build it, see below).
2. Drop it into your server's `plugins/` folder.
3. Restart the server.
4. Edit `plugins/zpizzatpa/config.yml` and `messages.yml` if you want, then run `/zpizzatpa reload`.

## Building

```bash
mvn clean package
```

The jar is created at `target/zpizzatpa.jar`. Requires JDK 21 or newer. The project compiles against the 1.21.8 API so it doesn't use anything missing on the oldest supported version.

## Commands

| Command | Description | Permission |
|---|---|---|
| `/tpa` (alias `/tpask`) | Open the player picker | `zpizzatpa.tpa` |
| `/tpa <player>` | Send a request to go to a player | `zpizzatpa.tpa` |
| `/tpahere` | Open the picker; the chosen player comes to you | `zpizzatpa.tpahere` |
| `/tpahere <player>` | Ask a player to teleport to you | `zpizzatpa.tpahere` |
| `/tpaccept [player]` | Accept a request (latest one if no name is given) | `zpizzatpa.tpaccept` |
| `/tpdeny [player]` | Deny a request (latest one if no name is given) | `zpizzatpa.tpdeny` |
| `/tpcancel` | Cancel all the requests you sent | `zpizzatpa.tpcancel` |
| `/tpall` | Teleport every player to you | `zpizzatpa.tpall` |
| `/tpaall` | Send everyone a request to teleport to you | `zpizzatpa.tpaall` |
| `/zpizzatpa reload` | Reload `config.yml` and `messages.yml` | `zpizzatpa.reload` |

## Permissions

| Node | Default | Description |
|---|---|---|
| `zpizzatpa.use` | everyone | Player commands: `tpa`, `tpahere`, `tpaccept`, `tpdeny`, `tpcancel` |
| `zpizzatpa.admin` | op | Admin commands: `tpall`, `tpaall`, `reload` and `seevanished` |
| `zpizzatpa.seevanished` | op | See and target vanished players in the menus |
| `zpizzatpa.*` | op | Everything |

Each command also has its own node (`zpizzatpa.tpa`, `zpizzatpa.tpall`, …) for fine-grained control in LuckPerms.

## Configuration

`config.yml`

```yaml
request-timeout-seconds: 60   # how long a request stays valid
players-per-page: 9           # above this, pagination and search appear
strip-prefix: "."             # prefix removed from nicknames in menus (e.g. Floodgate's ".")
```

`messages.yml` holds every message, button label and dialog title, written in [MiniMessage](https://docs.advntr.dev/minimessage/format.html). Missing keys fall back to the defaults bundled in the jar.

## How it works

1. A player runs `/tpa` and picks someone from the menu (or runs `/tpa <player>` directly).
2. After confirming, the target receives a chat message with **ACCEPT** and **DENY** buttons (or the commands, if they are on Bedrock).
3. If accepted, the teleport happens. Requests expire after `request-timeout-seconds`.

Menu layout: with few players you get a simple one-column list. With more than `players-per-page`, the menu switches to a 3-column grid with the navigation row and search bar at the top.

## Authors

Made by **Zynyhero** and **Claude**.
