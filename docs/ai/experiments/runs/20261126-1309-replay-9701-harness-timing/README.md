# 20261126-1309-replay-9701-harness-timing

**Kind:** benchmark

**Question:** Why did game 2 (seed 9701) of styx-take-rate take 7h49m? Replays slot 1 of seed 9700 alone at the batch's commit plus per-round HARNESS timing (reloads, trackers, checks, wall and CPU), -Dai.timing and a JFR recording with game file load/save traces. Batch game 2: 28123 s wall, 26806 s CPU, 12234 s in ticks, letnev 11 VP in round 6.

**Verdict:** The game itself is not slow: alone it replays exactly (same scores, 24,985 decisions, 219 turns, same agenda outcomes) in 7,866 s wall / 7,758 s CPU, against 28,123 s / 26,806 s in the batch. Alone, AI decisions take 6,752 s and the harness loop 1,004 s of CPU, almost all of it the agenda tracker (987 s, 938 s in round 5, one iteration 154 s). Save reloads cost 1.4 ms each (6 s in all), not the cause. JFR: the tracker time is `AgendaTrace$Window.recordBounties` > `lossText` > `AgendaPolicy.expectedLoss`, which runs the full ballot forecast outside a decision, so without a `DecisionMemo` scope, and recomputes `PlanInputs` for the reloaded Game instance. In the batch the same decisions cost 1.8x per call (same call counts, e.g. `trade.votePromises` 2,094 calls, 5,884 s vs 3,267 s): CPU contention from 10 game threads. Outside decisions it cost 14,735 s, 14.6x, which contention alone does not explain. Likely cause (not proven): the tracker leans on the 32-entry `PlanInputs` LRU that all games share, and other threads evict its entries during its long unmemoized calls. Other batch games show the same signature (seeds 9719, 9721, 9728: 45-55% of CPU outside decisions). Follow-up: run the tracker probes inside a `DecisionMemo` scope or drop the loss text, and keep the `PlanInputs` cache from evicting across games.

## Code

- Commit `e9c328fc28db08b1502b2cf863ae223452e832f4` on `HEAD`: Time the self-play harness loop per round, with an optional JFR recording
- That commit is on the local branch claude/ai-replay-9701 only. Its harness timing landed on the AI players branch as `891176d215`.
- Uncommitted changes: none

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 1 on 1 threads, base seed 9700
- Tested: benchmark
- Properties: `{ai.selfplay.firstslot=1, ai.selfplay.fracture=true, ai.selfplay.full=true, ai.selfplay.games=1, ai.selfplay.jfr=C:/Users/willh/OneDrive/Documents/GitHub/TI4_map_generator_bot/.claude/worktrees/replay-9701/jfr/replay-9701.jfr, ai.selfplay.queue.id=20261126-100934-replay-9701-harness-timing, ai.selfplay.record=true, ai.selfplay.record.dir=C:\Users\willh\OneDrive\Documents\GitHub\TI4_map_generator_bot\.claude\worktrees\replay-9701\docs\ai\experiments, ai.selfplay.record.label=replay-9701-harness-timing, ai.selfplay.record.question=Why did game 2 (seed 9701) of styx-take-rate take 7h49m? Replays slot 1 of seed 9700 alone at the batch's commit plus per-round HARNESS timing (reloads, trackers, checks, wall and CPU), -Dai.timing and a JFR recording with game file load/save traces. Batch game 2: 28123 s wall, 26806 s CPU, 12234 s in ticks, letnev 11 VP in round 6., ai.selfplay.seed=9700, ai.selfplay.threads=1, ai.timing=true}`

## Results

- Endings: {VICTORY_POINTS=1}
- Average final round 6.0, leader points 11.0, table points 50.0
- Batch wall time 7869 s, 1479.406 ms per tick

The rows are in `games.csv`; the console output is in `console.log.gz`.

## Queue

- Job `20261126-100934-replay-9701-harness-timing` from replay-9701-session, done (exit 0)
- Waited 2.97 h after approval, ran 2.21 h including the build (estimate 6 h)
- Load over 27 samples: CPU 41% average, 82% max; other Maven builds in 0 samples (none)
