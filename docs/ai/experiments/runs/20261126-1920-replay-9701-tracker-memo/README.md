# 20261126-1920-replay-9701-tracker-memo

**Kind:** benchmark

**Question:** Does the isolated harness memo and per-decision PlanInputs memo (claude/ai-tracker-memo e204f695c7) remove the agenda tracker's between-tick cost? Seed 9701 (slot 1 of seed 9700) alone with HARNESS timing. Before, at 513e5d1 alone: track.agendas 987 s of 1004 s harness CPU, 7758 s game CPU, 6752 s in decisions. The code differs from that run, so the game may play differently; compare the harness share, not the exact game.

**Verdict:** kept. The harness work between ticks fell from 1,004 s to 31 s of CPU, and the agenda tracker from 974 s to 3.9 s (no round above 3.7 s; it was 926 s in round 5). Correction (2026-11-26): this branch is cut from the nekro branch, not from the batch's code. It lacks the Styx work (movement model, Styx gathering, boost re-split) and has the ranking caches, so about 77 AI files differ from the 513e5d1 replay. Decision CPU fell from 6,664 s to 1,292 s, but that cannot be credited to the fix; neither can the game ending in round 5 with a different winner. The fix was ported onto the batch's code as 92289cc6a4 (claude/ai-replay-9701), and queue job 20261126-214718-styx-batch-tracker-memo re-runs the 32-game batch on it.

## Code

- Commit `e204f695c7c5d06e05dd89552e085407344ec856` on `HEAD`: Keep the harness's AI probes in an isolated memo and keep plan inputs for the whole decision
- That commit is on the local branch claude/ai-tracker-memo only. It landed on the AI players branch as `2dd825c0eb`.
- Uncommitted changes: none

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 1 on 1 threads, base seed 9700
- Tested: benchmark
- Properties: `{ai.selfplay.firstslot=1, ai.selfplay.fracture=true, ai.selfplay.full=true, ai.selfplay.games=1, ai.selfplay.queue.id=20261126-180906-replay-9701-tracker-memo, ai.selfplay.record=true, ai.selfplay.record.dir=C:\Users\willh\OneDrive\Documents\GitHub\TI4_map_generator_bot\.claude\worktrees\ai-tracker-memo\docs\ai\experiments, ai.selfplay.record.label=replay-9701-tracker-memo, ai.selfplay.record.question=Does the isolated harness memo and per-decision PlanInputs memo (claude/ai-tracker-memo e204f695c7) remove the agenda tracker's between-tick cost? Seed 9701 (slot 1 of seed 9700) alone with HARNESS timing. Before, at 513e5d1 alone: track.agendas 987 s of 1004 s harness CPU, 7758 s game CPU, 6752 s in decisions. The code differs from that run, so the game may play differently; compare the harness share, not the exact game., ai.selfplay.seed=9700, ai.selfplay.threads=1, ai.timing=true}`

## Results

- Endings: {VICTORY_POINTS=1}
- Average final round 5.0, leader points 10.0, table points 45.0
- Batch wall time 1448 s, 303.8 ms per tick

The rows are in `games.csv`; the console output is in `console.log.gz`.

## Queue

- Job `20261126-180906-replay-9701-tracker-memo` from replay-9701-session, done (exit 0)
- Waited 1.17 h after approval, ran 0.43 h including the build (estimate 3 h)
- Load over 6 samples: CPU 48% average, 69% max; other Maven builds in 4 samples (c:/users/willh/onedrive/documents/github/ti4_map_generator_bot/.claude/worktrees/ai-styx)
