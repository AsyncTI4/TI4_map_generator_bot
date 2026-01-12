# AI Player System

## Overview

The AI Player System introduces computer-controlled players to Twilight Imperium 4 games. It uses a pluggable strategy architecture that makes legal, deterministic decisions while reusing existing command handlers and validators.

## Architecture

### Core Components

1. **PlayerStrategy Interface** (`ti4.ai.PlayerStrategy`)
   - Defines decision-making methods for all game phases
   - Returns null to indicate "pass" or "no action"
   - Implementations should be legal-first and deterministic

2. **SimpleHeuristicStrategy** (`ti4.ai.SimpleHeuristicStrategy`)
   - Basic AI implementation using simple heuristics
   - Prioritizes safe actions: research tech > build > explore > pass
   - Default strategy for difficulty level "simple"

3. **AiPlayer** (`ti4.ai.AiPlayer`)
   - Wrapper holding AI configuration and state
   - Delegates decisions to a PlayerStrategy
   - Tracks actions taken per phase

4. **AiTurnRunner** (`ti4.ai.AiTurnRunner`)
   - Orchestrates AI turns across different game phases
   - Handles retries and fallback to passing
   - Adds delays with jitter to prevent bursts

5. **AiScheduler** (`ti4.ai.AiScheduler`)
   - Manages AI player registration and scheduling
   - Polls games for AI turns (configurable interval)
   - Provides per-game locks for concurrency control

6. **AiConfig** (`ti4.ai.AiConfig`)
   - Central configuration with feature flag gating
   - Controls timing, limits, and behavior
   - Supports dry-run mode for testing

### Commands

All commands are under `/ai`:

- `/ai join <color> [difficulty]` - Add an AI player
- `/ai leave <color>` - Remove an AI player
- `/ai difficulty <color> <simple|medium|hard>` - Change difficulty
- `/ai pause <color>` - Pause an AI player
- `/ai resume <color>` - Resume a paused AI player
- `/ai status [color]` - Get AI status and statistics

## Configuration

Edit `src/main/resources/config/application.yml`:

```yaml
ai:
  enabled: false                    # Master feature flag
  scheduler:
    delay: 5000                     # Polling interval (ms)
  max-actions-per-phase: 10         # Safety limit
  action-timeout: 30000             # Per-action timeout (ms)
  action-delay: 1000                # Base delay between actions (ms)
  action-jitter: 500                # Random jitter (ms)
  max-retries: 3                    # Retry limit for failed actions
  default-difficulty: simple        # Default strategy
  dry-run: false                    # Log without executing
  verbose-logging: true             # Detailed logging
```

## Getting Started

### 1. Enable AI System

Set `ai.enabled: true` in `application.yml`:

```yaml
ai:
  enabled: true
```

### 2. Add AI Player to Game

```
/ai join red simple
```

This adds an AI player controlling the "red" faction with simple difficulty.

### 3. Monitor AI Status

```
/ai status red
```

Shows current state, difficulty, and actions taken.

### 4. Pause/Resume During Development

```
/ai pause red
/ai resume red
```

## Development and Testing

### Dry-Run Mode

Enable dry-run to see AI decisions without executing:

```yaml
ai:
  dry-run: true
  verbose-logging: true
```

AI will log intended actions like:
```
[AI DRY-RUN] Would pick strategy card: 7
[AI DRY-RUN] Would take action: research
```

### Logging

All AI decisions are logged with context:
```
[AI] Game=game123 Player=red Phase=action Action=RESEARCH Details=tech=sarween
```

Errors are logged with stack traces:
```
[AI ERROR] Game=game123 Player=red Context=executeTurn[phase=action]
```

## Safety Features

1. **Feature Flag Gating**: Master `ai.enabled` flag disables entire system
2. **Action Limits**: Max actions per phase prevents runaway behavior
3. **Timeouts**: Per-action timeouts prevent hanging
4. **Rate Limiting**: Configurable delays with jitter prevent bursts
5. **Concurrency Control**: Per-game locks prevent race conditions
6. **Dry-Run Mode**: Test decisions without affecting game state
7. **Conservative Defaults**: AI defaults to passing when uncertain

## Current Limitations

This is a **scaffold implementation** with the following known limitations:

1. **Placeholder Logic**: Strategy methods contain basic heuristics
2. **No Command Execution**: TODO comments mark where actual command services need integration
3. **Limited Validation**: Legality checks are simplified placeholders
4. **No Persistence**: AI state is in-memory only (except game saves)
5. **Basic Phase Support**: Only strategy, action, agenda, and status phases
6. **Single Difficulty**: Only "simple" strategy is implemented

## Roadmap

### Phase 1: Foundation (Complete ✓)
- [x] Core interfaces and classes
- [x] Command structure
- [x] Configuration system
- [x] Logging and observability

### Phase 2: Integration (TODO)
- [ ] Integrate with existing command services
- [ ] Add validators before action execution
- [ ] Implement actual action execution
- [ ] Add persistence for AI configuration

### Phase 3: Strategy Enhancement (TODO)
- [ ] Expand SimpleHeuristicStrategy with real logic
- [ ] Add board state analysis
- [ ] Implement MediumStrategy
- [ ] Implement HardStrategy

### Phase 4: Testing (TODO)
- [ ] Unit tests for strategies
- [ ] Integration tests with real games
- [ ] Performance testing
- [ ] Edge case validation

## Adding New Strategies

1. Create a new class implementing `PlayerStrategy`:

```java
package ti4.ai;

public class MediumStrategy implements PlayerStrategy {
    @Override
    public String chooseAction(Game game, Player player) {
        // Your logic here
    }
    
    // Implement other methods...
    
    @Override
    public String getDifficulty() {
        return "medium";
    }
}
```

2. Update `AiConfig.getStrategyClassName()`:

```java
case "medium" -> "ti4.ai.MediumStrategy";
```

3. Update `AiScheduler.createStrategy()`:

```java
case "medium" -> new MediumStrategy();
```

## Troubleshooting

### AI Not Taking Actions

1. Check `ai.enabled: true` in configuration
2. Verify AI player is registered: `/ai status red`
3. Check if AI is paused: `/ai resume red`
4. Review logs for errors
5. Enable verbose logging: `ai.verbose-logging: true`

### Actions Failing

1. Enable dry-run mode to see intended actions
2. Check action limits: `ai.max-actions-per-phase`
3. Review error logs for validation failures
4. Verify game state is valid

### Performance Issues

1. Increase polling delay: `ai.scheduler.delay: 10000`
2. Reduce actions per phase: `ai.max-actions-per-phase: 5`
3. Disable verbose logging: `ai.verbose-logging: false`

## Contributing

When adding AI features:

1. Maintain legality-first approach
2. Add validators before executing actions
3. Default to passing when uncertain
4. Log decisions with context
5. Add configuration options for new behaviors
6. Write tests for new strategies
7. Update this README

## License

Same as parent project.
