# Changelog

All notable changes to Ecotale will be documented in this file.

## [1.0.2] - 2026-01-19

### Fixed
- **MultipleHUD crash fix** - HUD updates now use incremental `update()` instead of full `show()`
  - Prevents crash when other mods' HUDs fail during MultipleHUD's build loop
  - Adds defensive exception handling to prevent crash propagation

---

## [1.0.1] - 2026-01-19

### Added
- **MultipleHUD compatibility** - Optional support for the [MultipleHUD](https://www.curseforge.com/hytale/mods/multiplehud) mod
  - Auto-detects MultipleHUD at runtime via reflection
  - If present, uses its API to allow multiple HUDs to coexist
  - Falls back gracefully to vanilla HUD if not installed

---

## [1.0.0] - 2026-01-18

### First Release

Complete economy system for Hytale servers.

### Features

#### Core Economy
- **Player balances** - Persistent storage with atomic operations
- **Transfers** - Player-to-player payments with configurable fees
- **Admin controls** - Give, take, set, reset balances

#### User Interface
- **Balance HUD** - On-screen display with smart formatting (K, M, B)
- **Admin Panel GUI** - 5 tabs: Dashboard, Players, Top, Log, Config
- **Pay GUI** - Player-to-player payment interface
- **Multi-language** - 6 languages supported

#### Storage
- **H2 Database** - Default, fastest, embedded
- **MySQL** - Shared database for multi-server
- **JSON** - Human-readable files for debugging

#### API
- `EcotaleAPI` - Public static methods for all economy operations
- `PhysicalCoinsProvider` - Interface for coin drop plugins
- Rate limiting and thread safety built-in
- Cancellable events for balance changes

### Commands
| Command | Permission |
|---------|-----------|
| `/balance` | None (all players) |
| `/pay` | None (all players) |
| `/eco` | `ecotale.ecotale.command.eco` |

### Languages
- English (en-US)
- Spanish (es-ES)
- Portuguese (pt-BR)
- French (fr-FR)
- German (de-DE)
- Russian (ru-RU)

### Technical
- Shadow JAR with all dependencies included
- Thread-safe with per-player locking
- Async database operations
- Optimized for 500+ concurrent players
