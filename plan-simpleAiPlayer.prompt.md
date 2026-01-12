## Plan: Add Simple AI Player

Introduce an `AiPlayer` using a pluggable `PlayerStrategy` to make legal, low-effort decisions. Reuse existing command handlers and validators so the AI issues the same actions as humans, minimizing engine changes. Provide opt-in config, simple commands to add/configure AI seats, and a lightweight scheduler to trigger AI turns.

### Goals
1. Be non-invasive: reuse existing action/command paths.
2. Be legal-first: never issue illegal moves; default to pass.
3. Be deterministic/simple: heuristics over complex search.
4. Be observable: clear logging and easy to disable.

### Assumptions
1. Game engine exposes validators and action handlers used by Discord commands.
2. Turn/phase state can be queried per game and per player.
3. Commands can be executed programmatically (without Discord message author).

### Architecture Overview
1. `PlayerStrategy` interface: decisions per phase/action.
2. `AiPlayer`: holds config, uses a strategy, issues commands.
3. `AiTurnRunner`: orchestrates a full AI turn across phases.
4. `AiScheduler`: triggers AI when it’s their turn (event-driven preferred; polling fallback).
5. `AiCommands`: admin/user commands to add/configure AI seats.
6. `AiConfig`: central config + feature flag gating.

### Implementation Steps
1. Define `PlayerStrategy` in src/main/java/ti4/ai/PlayerStrategy.java with methods like chooseAction(), chooseTargets(), selectUnits(), chooseTech(), chooseExplore(), chooseAgendaVote(), etc. Keep parameters generic and serializable.
2. Implement `SimpleHeuristicStrategy` in src/main/java/ti4/ai/SimpleHeuristicStrategy.java:
	- Prioritize safe actions: research tech, build units, claim objectives, explore if free.
	- Movement: prefer adjacent systems with low risk; avoid combats unless favorable.
	- Agenda: basic value map for common outcomes.
	- Default to pass when no beneficial legal actions.
3. Create `AiPlayer` in src/main/java/ti4/ai/AiPlayer.java that:
	- References a game and seat/playerId.
	- Uses existing validators before executing.
	- Issues actions via existing command services (same signatures as user commands).
4. Implement `AiTurnRunner` in src/main/java/ti4/ai/AiTurnRunner.java:
	- Detect current phase; for each phase, call strategy and attempt actions.
	- Handle retries/backoff if an action is rejected; fall back to pass.
	- Idempotency: if already acted in a phase, do nothing.
5. Add scheduling via `AiScheduler` in src/main/java/ti4/ai/AiScheduler.java:
	- Event-driven: subscribe to “turn advanced”/“awaiting player input” events.
	- Polling fallback: `@Scheduled(fixedDelay=...)` checking current player and phase.
	- Per-game lock to avoid concurrent runs; skip if AI paused.
6. Commands (`AiCommands`) in src/main/java/ti4/commands/AiCommands.java:
	- `!ai join <game> [difficulty]` add an AI seat.
	- `!ai leave <game>` remove AI seat.
	- `!ai difficulty <game> <simple|medium|hard>` switch strategy.
	- `!ai pause|resume <game>` toggle scheduler participation.
	- `!ai status <game>` report AI state/log summary.
7. Config gating (`AiConfig`) in src/main/java/ti4/ai/AiConfig.java + src/main/resources/config/application.yml:
	- `enableAiPlayers: true|false` feature flag.
	- Defaults for delay, max actions per phase, timeouts.
	- Strategy selection by difficulty.
8. Legality & safety:
	- Always call existing validators before executing actions.
	- If illegal/rejected, log and try alternative; else pass.
	- Rate-limit command issuance; add small jitter to avoid bursts.
9. Concurrency & idempotency:
	- Per-game mutex (e.g., `ConcurrentHashMap<gameId, Lock>`).
	- Guard against double-execution if multiple triggers fire.
10. Persistence:
	- Store AI seat membership and config with normal game state.
	- AI-runtime transient state (e.g., last decision) is in-memory only.
11. Observability:
	- Log decisions and outcomes via BotLogger with concise context.
	- Optional “dry-run” mode that prints intended actions without executing (for testing).
12. Testing:
	- Unit tests for `SimpleHeuristicStrategy` decision outputs given mocked state.
	- Integration test: a small game where AI takes a turn and ends in a valid state.
	- Toggle tests: ensure `enableAiPlayers=false` disables scheduler/commands.

### Minimal File Scaffold (names only)
1. src/main/java/ti4/ai/PlayerStrategy.java
2. src/main/java/ti4/ai/SimpleHeuristicStrategy.java
3. src/main/java/ti4/ai/AiPlayer.java
4. src/main/java/ti4/ai/AiTurnRunner.java
5. src/main/java/ti4/ai/AiScheduler.java
6. src/main/java/ti4/ai/AiConfig.java
7. src/main/java/ti4/commands/AiCommands.java

### Risks & Unknowns
1. Command services may require a Discord author context; ensure a service API exists to invoke actions programmatically.
2. Turn/phase events may not be available; polling must be carefully rate-limited.
3. Validators may be incomplete; strategy must be conservative to avoid illegal states.
4. Complex rules edge cases (agenda, attachments, promissories) may need stubs initially.

### Rollout Plan
1. Hide behind `enableAiPlayers=false` initially.
2. Ship scaffold + dry-run logging; validate decisions.
3. Enable for test games only; gather logs.
4. Expand heuristics for common phases; iterate.