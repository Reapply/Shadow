# Shadow Hub

A Minecraft hub/lobby plugin built with Kotlin for PaperMC servers, designed to create an engaging hub experience for networks.

## Features

### Server Selector
- Customizable compass item for server navigation
- Configurable GUI menu for server selection
- Support for custom item models and descriptions
- BungeeCord integration for seamless server transfers

### Spawn Management
- Configurable spawn location
- Automatic teleportation on join/respawn
- Admin command: `/setspawn` for easy spawn point setup
- Persistent spawn location storage

### Launch Pads
Three types of launch pads with unique properties:
- Stone Pressure Plate: Basic launch (2.0x multiplier)
- Heavy Weighted Plate: Medium launch (3.0x multiplier)
- Light Weighted Plate: High launch (4.0x multiplier)

Each pad features:
- Custom particle effects
- Unique sound effects
- Configurable cooldowns
- Momentum preservation

### Hub Protection
- PvP disabled
- Damage prevention
- Hunger disabled
- World protection features

## Requirements

- Java 21 or higher
- PaperMC 1.21.1
- Kotlin 2.1.0
- BungeeCord/Velocity for server teleportation

## Configuration

The plugin uses several configuration files:
- `config.yml`: Main configuration file (server selector, GUI settings)
- `spawn.yml`: Spawn location settings

## Building

```bash
./gradlew build
```

The built plugin will be in `build/libs/`

## Dependencies

- PaperMC API
- Twilight Framework
- Kotlin Standard Library

## Permissions

- `shadow.setspawn`: Allows setting the spawn location

## License

This project is open source and available under the MIT License.
