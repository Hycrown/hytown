# LandClaims

A chunk-based land claiming plugin for Hytale servers with playtime-based claim limits.

## Features

- **Chunk-based claims** - Claim 16x16 chunk areas to protect your builds
- **Playtime rewards** - More playtime = more claim chunks available
- **Trust system** - Share your claims with trusted players
- **Full protection** - Blocks interactions from non-trusted players in claimed areas

## Installation

1. Download `LandClaims.jar` from [Releases](../../releases)
2. Place in your Hytale server's `mods/` folder
3. Restart the server

## Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/claim` | `landclaims.claim` | Claim the chunk you're standing in |
| `/unclaim` | `landclaims.unclaim` | Unclaim the chunk you're standing in |
| `/claims` | `landclaims.list` | List all your claims |
| `/trust <uuid>` | `landclaims.trust` | Trust a player in all your claims |
| `/untrust <uuid>` | `landclaims.untrust` | Remove trust from a player |
| `/playtime` | `landclaims.playtime` | Show your playtime and available claims |
| `/claimhelp` | `landclaims.help` | Show help |

## Configuration

Configuration is stored in `mods/Community_LandClaims/config.json`:

```json
{
  "chunksPerHour": 2,
  "startingChunks": 4,
  "maxClaimsPerPlayer": 50,
  "playtimeUpdateIntervalSeconds": 60
}
```

## Setup Permissions

```
perm group add Adventure landclaims.claim
perm group add Adventure landclaims.unclaim
perm group add Adventure landclaims.list
perm group add Adventure landclaims.trust
perm group add Adventure landclaims.untrust
perm group add Adventure landclaims.playtime
perm group add Adventure landclaims.help
```

## Building

Requires Java 25 and Maven.

```bash
mvn clean package
```

The built JAR will be in `target/LandClaims-1.0.0.jar`.

## License

MIT
