# Oryza Lobby Service

Waiting lobby service for Minecraft Java Edition built with Minestom and RabbitMQ.

## Features

- **Procedural Waiting Scene**: Generated in-memory waiting hall with zero binary world dependencies.
- **3-Tier Text Display Panel**: Displays hostname, server status, and informative waiting messages.
- **Per-Player Isolation**: Each player operates in their own `SharedInstance` with no visibility of other players.
- **Automatic Transfer**: Sends Minecraft Transfer packet (packet ID 0x73) once the backend comes online.
- **AMQP Integration**: Communicates with Network Manager for target resolution, status updates, and heartbeats.

## Building and Running

```bash
make check
make build
make dev
```
