# Quick Reference: AI Player System

## Enable/Disable AI System

```yaml
# src/main/resources/config/application.yml
ai:
  enabled: true  # Set to false to disable completely
```

## Commands

| Command | Description | Example |
|---------|-------------|---------|
| `/ai join <color> [difficulty]` | Add AI player | `/ai join red simple` |
| `/ai leave <color>` | Remove AI player | `/ai leave red` |
| `/ai difficulty <color> <level>` | Change difficulty | `/ai difficulty red medium` |
| `/ai pause <color>` | Pause AI player | `/ai pause red` |
| `/ai resume <color>` | Resume AI player | `/ai resume red` |
| `/ai status [color]` | Get status | `/ai status red` |

## Difficulty Levels

- **simple** - Basic heuristics, safe play (currently implemented)
- **medium** - Intermediate strategy (placeholder)
- **hard** - Advanced strategy (placeholder)

## Configuration Options

```yaml
ai:
  enabled: false                    # Master on/off switch
  
  scheduler:
    delay: 5000                     # Polling interval in milliseconds
  
  max-actions-per-phase: 10         # Safety limit per phase
  action-timeout: 30000             # Timeout per action (ms)
  action-delay: 1000                # Min delay between actions (ms)
  action-jitter: 500                # Random jitter added to delays (ms)
  max-retries: 3                    # Retry limit for failed actions
  
  default-difficulty: simple        # Default for new AI players
  dry-run: false                    # Log only, don't execute
  verbose-logging: true             # Detailed decision logs
```

## Testing Configuration

For development/testing:
```yaml
ai:
  enabled: true
  dry-run: true           # See decisions without executing
  verbose-logging: true   # Show all decision logic
  scheduler:
    delay: 2000           # Faster polling for testing
```

## Log Messages

### Info Messages (Normal Operation)
```
[AI] Registered AI player: game=game123, player=red, difficulty=simple
[AI] Game=game123 Player=red Phase=action Action=RESEARCH Details=tech=sarween
[AI] Turn processed: game=game123, player=red, actionTaken=true
```

### Dry-Run Messages
```
[AI DRY-RUN] Would pick strategy card: 7
[AI DRY-RUN] Would take action: research
```

### Error Messages
```
[AI ERROR] Game=game123 Player=red Context=executeTurn[phase=action]
```

### TODO Messages (Placeholder Logic)
```
[AI TODO] Execute strategy card pick: 7
[AI TODO] Execute action: research
```

## File Locations

- **Core AI**: `src/main/java/ti4/ai/`
- **Commands**: `src/main/java/ti4/commands/ai/`
- **Config**: `src/main/resources/config/application.yml`
- **Docs**: `AI_PLAYER_SYSTEM.md`, `IMPLEMENTATION_SUMMARY.md`

## Current Limitations

⚠️ **This is a scaffold implementation**
- Strategy methods make decisions but don't execute commands yet
- Validation is placeholder logic
- Only "simple" difficulty implemented
- No persistence of AI configuration

See TODO comments in code for integration points.

## Safety Features

✓ Feature flag gating (`ai.enabled`)  
✓ Action limits per phase  
✓ Timeouts on actions  
✓ Rate limiting with jitter  
✓ Per-game concurrency locks  
✓ Dry-run testing mode  
✓ Defaults to passing when uncertain

## Next Steps for Integration

1. Replace TODO comments with actual command execution
2. Add real validators before actions
3. Implement board state analysis
4. Add persistence for AI configuration
5. Expand strategy heuristics

## Getting Started

1. Set `ai.enabled: true` in application.yml
2. Start game as normal
3. Use `/ai join <color> simple` to add AI
4. Monitor logs for AI decisions
5. Use `/ai status <color>` to check state

For testing: enable `dry-run: true` and `verbose-logging: true` first!
