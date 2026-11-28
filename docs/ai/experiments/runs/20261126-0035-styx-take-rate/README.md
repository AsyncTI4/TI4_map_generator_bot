# 20261126-0035-styx-take-rate

**Kind:** benchmark

**Question:** Styx take rate with the Fracture in every game, after the movement model, Styx gathering and pooled boost re-split work plus the AI PR perf fixes (target 80%+, humans 87%; seed 9700 games 1-16 were 5/16 = 31% before)

**Verdict:** Better, still short of the target. Styx was taken in 18 of 32 games (56%) against the 80% target and the human 87%. On the same first 16 seeds it went from 5/16 to 8/16. The first take comes late, in round 4.9 on average (range 3 to 8), with the Fracture in by round 1 or 2. 10 of the 14 games without a take ended in round 5, so they were over before the usual take round; in games that reached round 6 the rate was 15 of 22 (68%). Styx was attacked in 20 games and taken in 18. The takes came from Letnev 11, Sardakk 6, Sol 2, Hacan 1 and Nekro 2 (counting changes of hands); Xxcha never took it. Follow-up: find out why the strike waits until round 4-5.

## Code

- Commit `513e5d1ce672c91cede95b0fde3eb2e85a8a073b` on `HEAD`: Merge remote-tracking branch 'origin/claude/ai-player-nekro-72bbce' into claude/ai-styx-take
- Uncommitted changes: none

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 32 on 10 threads, base seed 9700
- Tested: benchmark
- Properties: `{ai.selfplay.fracture=true, ai.selfplay.full=true, ai.selfplay.games=32, ai.selfplay.queue.id=20261125-234612-styx-take-rate, ai.selfplay.record=true, ai.selfplay.record.dir=C:\Users\willh\OneDrive\Documents\GitHub\TI4_map_generator_bot\.claude\worktrees\ai-styx\docs\ai\experiments, ai.selfplay.record.label=styx-take-rate, ai.selfplay.record.question=Styx take rate with the Fracture in every game, after the movement model, Styx gathering and pooled boost re-split work plus the AI PR perf fixes (target 80%+, humans 87%; seed 9700 games 1-16 were 5/16 = 31% before), ai.selfplay.seed=9700, ai.selfplay.threads=10}`

## Results

- Endings: {VICTORY_POINTS=32}
- Average final round 5.75, leader points 10.188, table points 45.156
- Batch wall time 31095 s, 1722.546 ms per tick

The rows are in `games.csv`; the console output is in `console.log.gz`.

## Queue

- Job `20261125-234612-styx-take-rate` from ai-styx-session, done (exit 0)
- Waited 0.8 h after approval, ran 8.67 h including the build (estimate 7 h)
- Load over 104 samples: CPU 84% average, 100% max; other Maven builds in 7 samples (c:/users/willh/onedrive/documents/github/ti4_map_generator_bot/.claude/worktrees/ai-agenda, c:/users/willh/onedrive/documents/github/ti4_map_generator_bot/.claude/worktrees/det-turn-ranking)
