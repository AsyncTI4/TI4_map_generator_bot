# AsyncTI4 Map Generator Bot — Copilot Instructions

## Project Overview

This is the **AsyncTI4 Game Management Bot**: a Java/Spring Boot Discord bot for running asynchronous Twilight Imperium 4 (TI4) games. It handles slash commands, button interactions, map image generation, server management, drafting, statistics, and much more. It uses [JDA (Java Discord API)](https://github.com/discord-jda/JDA) and is deployed as a long-running Spring Boot application.

---

## Tech Stack

- **Language**: Java 21
- **Build**: Maven (`pom.xml`); Spring Boot 4.x parent POM
- **Discord API**: JDA 6.x (`net.dv8tion:JDA`)
- **Database**: SQLite via Hibernate/JPA (`spring.datasource.url = jdbc:sqlite:${DB_PATH}/tibot.db`); `ddl-auto=update` auto-creates/migrates columns
- **Image generation**: Custom Java AWT rendering pipeline (`ti4.image.*`)
- **Data**: Game content (factions, tiles, technologies, etc.) stored as JSON and `.properties` files under `src/main/resources/data/`
- **Formatting**: Spotless Maven plugin enforces code style (run `mvn spotless:apply` before pushing)
- **Code analysis**: SpotBugs + FindSecBugs (`spotbugs-exclude.xml`)
- **AWS**: S3 used for image/data uploads (via `scripts/`)

---

## Repository Layout

```
src/
  main/
    java/ti4/
      AsyncTI4DiscordBot.java     # Spring Boot entry point
      commands/                   # Slash command hierarchy (Command → ParentCommand → Subcommand)
      listeners/                  # JDA event listeners + annotation framework
        annotations/              # @ButtonHandler, @ModalHandler, @SelectionHandler
      map/                        # Core game state: Game, Player, Tile, UnitHolder, Leader
        persistence/              # GameManager (in-memory cache), GameSaveService, GameLoadService
      helpers/                    # Utility classes; ButtonHelper* are the largest files (~8000+ lines)
      image/                      # Map/tile/player-area rendering, Mapper (data loader), TileHelper
      model/                      # Immutable data models (FactionModel, TileModel, PlanetModel, etc.)
      service/                    # Business logic organized by domain
      spring/                     # Spring components: REST API, JDA setup (JdaService), security, websocket
      cron/                       # Scheduled tasks (auto-ping, stats upload, cleanup, etc.)
      draft/                      # Milty draft, Franken draft logic
      buttons/                    # Button factory (Buttons.java)
      message/                    # Discord message helpers (MessageHelper), logging (BotLogger)
      settings/                   # GlobalSettings
      selections/                 # SelectionManager
      json/                       # Jackson serialization helpers
    resources/
      data/                       # Game content JSON/properties files
        factions/                 # One JSON file per faction set (base.json, pok.json, ds.json, etc.)
        technologies/, planets/, tiles/, leaders/, etc.
      config/application.yml      # Spring Boot config (DB path, JPA, server port 8081)
  test/
    java/ti4/                     # JUnit 5 tests; BaseTi4Test initializes static data before suites
```

---

## Build & Test

```bash
# Format code (REQUIRED before committing — build fails without it)
mvn spotless:apply

# Full build + tests (what CI runs)
mvn --batch-mode --update-snapshots --no-transfer-progress verify test-compile

# Run tests only
mvn test

# Install git pre-push hook that auto-formats
mvn spotless:install-git-pre-push-hook
```

**CI** (`.github/workflows/run-tests.yml`) validates JSON files and runs `mvn verify` on PRs to `master`.

**Environment variables required** to run locally or in tests:
- `DB_PATH` — directory for SQLite DB and game save files
- `RESOURCE_PATH` — path to `src/main/resources`

---

## Key Architectural Patterns

### 1. Slash Commands

- Commands implement `Command<SlashCommandInteractionEvent>` (or extend `ParentCommand`/`Subcommand`).
- All commands are registered in `SlashCommandManager`.
- Subcommands extend `Subcommand` (which extends JDA's `SubcommandData`) and implement `execute(SlashCommandInteractionEvent)`.
- Command groups extend `SubcommandGroup`.

### 2. Button / Modal / Selection Handlers (Annotation-Driven)

**Do NOT add to `ButtonListener.java`'s if/else chain — it is deprecated.**

Instead, annotate any method anywhere in the codebase with `@ButtonHandler("prefix")`:

```java
@ButtonHandler("myButton_")
public void handleMyButton(ButtonInteractionEvent event, Game game, Player player, String buttonID) {
    // buttonID.startsWith("myButton_") routes here
}
```

- `@ButtonHandler(value, save=true)` — `save=true` means the game is persisted after handling.
- `@ModalHandler("prefix")` — routes modal submissions.
- `@SelectionHandler("prefix")` — routes selection menu interactions.
- `@NamedParam("paramName")` — inject a named substring from the component ID.
- Methods can be non-public; parameter types (Game, Player, events, String) are injected by type via `AnnotationHandler` using reflection.
- Handlers are discovered via classpath scanning (Reflections library) of the `ti4` package.

### 3. Core Game State

- **`Game`** (~4700 lines): master game state object. Holds tiles, players, settings, round number, etc.
- **`Player`** (~3400 lines): per-player state (hand cards, techs, units, faction, color, etc.).
  - `Player.getColor()` can return the string `"null"` when unset — always filter nulls when using `Mapper.getColor()`.
  - `Player.getSecretsUnscored()` is `@NotNull` and always returns a non-null `HashMap`.
- **`Tile`**: a hex tile on the map; contains `UnitHolder` objects (space + planets).
- **`Leader`**: agent/commander/hero cards with exhaustion state.
- **`GameManager`**: static in-memory cache (`ConcurrentHashMap<String, ManagedGame>`); use `GameManager.save(game, reason)` to persist.
- Game files are saved as text files to `DB_PATH/maps/` (one file per game) plus SQLite for statistics.

### 4. Data / Content System

- **`Mapper`** (in `ti4.image`): static utility class; loaded at startup via `Mapper.init()`. Provides access to all game content: factions, tiles, planets, technologies, colors, leaders, etc.
  - `Mapper.getColors()` returns a new `ArrayList` from a `HashMap`-backed map — order is not guaranteed.
  - `Mapper.getColor("null")` returns `null` — always guard against this.
- **`TileHelper`**: maps tile IDs to `TileModel`, planet IDs to `PlanetModel`.
- **`AliasHandler`**: resolves human-readable aliases (e.g. abbreviations) to canonical IDs; loaded from `.properties` alias files.
- All `Model` classes (in `ti4.model`) are immutable data containers loaded from JSON.
- Game content JSON files are in `src/main/resources/data/`. Adding a new faction/tech/etc. means adding a new JSON entry; no Java code needed for data-only additions.

### 5. Image Rendering

- Map images are generated via `MapRenderPipeline` using Java AWT.
- Tile images, player area images, and other visuals are composed from PNG assets under `src/main/resources/`.
- `PositionMapper` maps tile positions to pixel coordinates.

### 6. Emojis

- Emojis are Discord application emojis (not Unicode). They are defined as Java enums implementing `TI4Emoji` (e.g., `MiscEmojis`, `FactionEmojis`, `UnitEmojis`).
- `ApplicationEmojiService.spoofEmojis()` is called in tests to avoid Discord API calls.

### 7. Logging

- `BotLogger` (in `ti4.message.logging`) is the primary logger — use it instead of raw `System.out` or SLF4J.
- Discord errors 10008 (Unknown Message) and 10015 (Unknown Webhook) are intentionally ignored via `DiscordHelper.isIgnorableError()`.

### 8. Statistics & Persistence

- `PlayerEntity` (Spring Data / Hibernate entity) tracks per-player statistics. Has an `is_replaced` boolean column set when `statsTrackedUserID != userID`.
- `PersistAllEntitiesService` persists all game entities to SQLite on a cron schedule.
- `ExpeditionWinRateStatisticsService` merges `obsidian` and `firmament` factions into a combined key `"obsidian & firmament"`.

### 9. Cron Jobs

Scheduled tasks live in `ti4.cron.*`. They are started by `JdaService` after bot initialization.

---

## Common Gotchas & Workarounds

1. **Formatting must pass**: The build will fail if `mvn spotless:apply` has not been run. Always format before committing. The pre-push hook automates this.
2. **Tests require environment variables**: Set `DB_PATH` and `RESOURCE_PATH` when running tests outside Docker. `BaseTi4Test` handles this for the test suite via `Storage.setResourcePath(...)`.
3. **`JdaService.testingMode = true`**: Must be set in tests to disable Discord API calls and randomness.
4. **`ButtonHelper` is enormous** (~8300 lines): when searching for button logic, prefer searching for the `@ButtonHandler` annotation near where the button is created rather than grep-searching `ButtonHelper`.
5. **Color "null" string**: `Player.getColor()` returns the string `"null"` (not Java null) when unset. Always filter before calling `Mapper.getColor(...)`.
6. **`ButtonHelper.getTilesWithYourCC`** copies the tile map into a new `HashMap` on every call — avoid calling it in a tight loop; cache the result instead.
7. **`@ButtonHandler` save flag**: `save=true` (default) causes game state to be persisted after the handler runs. Set `save=false` on read-only handlers.
8. **`Mapper.getColors()`** returns a new `ArrayList` from a `HashMap` — iteration order is not guaranteed across calls.
9. **`JsonMapperManager.basic()`** returns a shared instance; it does NOT suppress empty lists (`NON_EMPTY` is not configured).
10. **New content (JSON)**: Adding new game content (factions, technologies, etc.) is done by editing JSON files under `src/main/resources/data/` — no Java changes needed for pure data additions.
11. **Command registration**: New slash commands must be added to `SlashCommandManager`. New button/modal/selection handlers are auto-discovered via classpath scanning — no registration needed.

---

## Adding New Features — Quick Reference

| Feature | Where to add |
|---|---|
| New slash command | Create class extending `Subcommand` or `ParentCommand`, add to `SlashCommandManager` |
| New button handler | Annotate method with `@ButtonHandler("prefix")` anywhere in `ti4.*` |
| New modal handler | Annotate method with `@ModalHandler("prefix")` |
| New selection menu handler | Annotate method with `@SelectionHandler("prefix")` |
| New game content (faction, tech, etc.) | Edit JSON files in `src/main/resources/data/` |
| New game setting | Add to `GameSettings` or relevant settings class in `ti4.helpers.settingsFramework` |
| New emoji | Add to relevant `TI4Emoji` enum in `ti4.service.emoji.*` |
| New scheduled task | Create class in `ti4.cron.*`, register in `JdaService` |
| New REST endpoint | Add controller in `ti4.spring.api.*` |

---

## Testing

- Tests extend `BaseTi4Test` which calls `globalBeforeAll()` once to initialize `Mapper`, `TileHelper`, `AliasHandler`, `PositionMapper`, `SelectionManager`, and spoof emojis.
- JUnit 5; run with `mvn test`.
- No Discord connection is made during tests (`JdaService.testingMode = true`).

---

## Deployment Notes

- The bot is deployed on a Hostinger server via SSH. Deployment workflows are in `.github/workflows/`.
- The bot runs as a JAR: `java -jar <jar> <discordBotToken> <discordUserID> <discordServerID>`.
- Spring Boot serves on port 8081 (internal metrics/API).
- Images and data are uploaded to S3 via Python scripts in `scripts/`.
