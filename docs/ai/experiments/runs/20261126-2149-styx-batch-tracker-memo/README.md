# 20261126-2149-styx-batch-tracker-memo

**Kind:** benchmark

**Question:** Does the tracker memo fix remove the batch slowdown under 10-thread load? Same 32 games, 10 threads, seed 9700 and Fracture as styx-take-rate (513e5d1, 31095 s batch wall, game 2 = seed 9701 took 28123 s, games 1-2 h otherwise), with only the fix added (92289cc6a4 on claude/ai-replay-9701: isolated harness memo, plan inputs kept per decision) plus HARNESS timing. Compare per-game wall time and harness share; the fix can change some games' play, so compare times, not outcomes.

**Verdict:** Kept. All 32 games replay exactly as in styx-take-rate (same endings, rounds, ticks, scores and winners), so the fix does not change play and the times compare one to one. Batch wall 31,095 -> 25,448 s (-18%), summed game wall 270,333 -> 220,112 s (-19%). The ten games that were slow because of the harness got 25-51% faster: seed 9701 28,123 -> 14,119 s, 9719 13,048 -> 6,436, 9721 16,439 -> 9,821, 9728 10,907 -> 6,387, 9724 9,619 -> 5,752, 9727 14,243 -> 9,504. The other 22 are within -6%..+6%, which is load noise (other Maven builds ran during all 86 load samples). The harness loop is now 0.8-5.4% of each game's wall time (seed 9701: 515 s, agenda tracker 452 s, against 987 s alone before the fix). Seed 9701 is still the slowest game, 96% of it in ticks: 13,604 s in the batch against 6,752 s of decision CPU alone, so the rest is CPU contention between 10 game threads, not the harness. Follow-ups: the agenda tracker is still the largest harness part (up to 416 s a game); dropping its loss text would remove most of it. Port the fix to the branch the next batches run from.

## Code

- Commit `92289cc6a4f849d793914b458e28dcea9a46265d` on `HEAD`: Keep the harness's AI probes in an isolated memo and keep plan inputs for the whole decision
- That commit is on the local branch claude/ai-replay-9701 only: the fix ported onto the batch base without SaveMemo. The fix for this branch landed as `2dd825c0eb`.
- Uncommitted changes: none

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 32 on 10 threads, base seed 9700
- Tested: benchmark
- Properties: `{ai.selfplay.fracture=true, ai.selfplay.full=true, ai.selfplay.games=32, ai.selfplay.queue.id=20261126-214718-styx-batch-tracker-memo, ai.selfplay.record=true, ai.selfplay.record.dir=C:\Users\willh\OneDrive\Documents\GitHub\TI4_map_generator_bot\.claude\worktrees\replay-9701\docs\ai\experiments, ai.selfplay.record.label=styx-batch-tracker-memo, ai.selfplay.record.question=Does the tracker memo fix remove the batch slowdown under 10-thread load? Same 32 games, 10 threads, seed 9700 and Fracture as styx-take-rate (513e5d1, 31095 s batch wall, game 2 = seed 9701 took 28123 s, games 1-2 h otherwise), with only the fix added (92289cc6a4 on claude/ai-replay-9701: isolated harness memo, plan inputs kept per decision) plus HARNESS timing. Compare per-game wall time and harness share; the fix can change some games' play, so compare times, not outcomes., ai.selfplay.seed=9700, ai.selfplay.threads=10, ai.timing=true}`

## Results

- Endings: {VICTORY_POINTS=32}
- Average final round 5.75, leader points 10.188, table points 45.156
- Batch wall time 25448 s, 1402.541 ms per tick

The rows are in `games.csv`; the console output is in `console.log.gz`.

## Queue

- Job `20261126-214718-styx-batch-tracker-memo` from replay-9701-session, done (exit 0)
- Waited 0 h after approval, ran 7.1 h including the build (estimate 9 h)
- Load over 86 samples: CPU 88% average, 100% max; other Maven builds in 86 samples (c:/users/willh/onedrive/documents/github/ti4_map_generator_bot/.claude/worktrees/ai-styx, c:/users/willh/onedrive/documents/github/ti4_map_generator_bot/.claude/worktrees/savememo-baseline, c:/users/willh/onedrive/documents/github/ti4_map_generator_bot/.claude/worktrees/test-speed-baseline, c:/users/willh/onedrive/documents/github/ti4_map_generator_bot/.claude/worktrees/xenodochial-pike-095907)
