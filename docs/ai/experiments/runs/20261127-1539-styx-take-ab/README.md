# 20261127-1539-styx-take-ab

**Kind:** ab

**Question:** Does STYX_TAKE raise the Styx take rate and points? (6 pairs, Fracture in every game)

**Verdict:** Inconclusive on points (+0.42 ± 0.52 VP, 6 pairs); kept on by default. The push works where it applies: all 6 first Styx takes came from STYX_TAKE seats (Letnev in 4 of its 6 treatment games, Nekro in 2), none from control seats; one control Nekro retook Styx from Letnev. Styx was taken in 6 of 12 games (50%), still short of the 80% goal; 2 of the 6 takers won. No game stalled. Follow-ups: the longer seeded run; why no seat touched the Fracture prizes in 4 games (slots 3, 5, 8, 9) and only Hacan took Lethe and Phlegethon in slots 0 and 1.

## Styx takes

From the Fracture section of each game summary, matched to `games.csv` by seed and final points.

| Slot | Seed | First Styx take | Side | Taker points | Winner |
| --- | --- | --- | --- | --- | --- |
| 0 | 9900 | none (Lethe, Phlegethon to control Hacan r6) | | | xxcha (T) |
| 1 | 9900 | none (Lethe, Phlegethon to treatment Hacan r5) | | | letnev (c) |
| 2 | 9901 | letnev r3, retaken by nekro r4 | T (retake c) | 7 | sol (c) |
| 3 | 9901 | none | | | letnev (c) |
| 4 | 9902 | letnev r4 | T | 8 | sardakk (T) |
| 5 | 9902 | none | | | sardakk (c) |
| 6 | 9903 | letnev r6 | T | 10 | letnev (T) |
| 7 | 9903 | nekro r5 | T | 10 | nekro (T) |
| 8 | 9904 | none | | | letnev (T) |
| 9 | 9904 | none | | | nekro (T) |
| 10 | 9905 | letnev r3 | T | 5 | sardakk (T) |
| 11 | 9905 | nekro r6 | T | 9 | hacan (T) |

## Code

- Commit `d9f34394a1e4328b5a9765593d1b4eb5f64220d3` on `HEAD`: Put the Styx take behind the STYX_TAKE experiment
- Uncommitted changes: none

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 12 on 12 threads, base seed 9900
- Tested: [STYX_TAKE]
- Properties: `{ai.selfplay.ab=STYX_TAKE, ai.selfplay.fracture=true, ai.selfplay.full=true, ai.selfplay.games=12, ai.selfplay.queue.id=20261127-153813-styx-take-ab, ai.selfplay.record=true, ai.selfplay.record.dir=C:\Users\willh\OneDrive\Documents\GitHub\TI4_map_generator_bot\.claude\worktrees\ai-styx\docs\ai\experiments, ai.selfplay.record.label=styx-take-ab, ai.selfplay.record.question=Does STYX_TAKE raise the Styx take rate and points? (6 pairs, Fracture in every game), ai.selfplay.seed=9900, ai.selfplay.threads=12}`

## Results

- Endings: {VICTORY_POINTS=12}
- Average final round 5.917, leader points 10.083, table points 45.75
- Batch wall time 7811 s, 1023.153 ms per tick
- Treatment minus control points: 0.417 ± 0.521 (t 0.80) over 6 complete pairs
- Treatment win share: 0.667 ± 0.167 (t 1.00) (null 0.500)
- Unfinished games (their pairs excluded): 0

The rows are in `games.csv`; the console output is in `console.log.gz`.

## Queue

- Job `20261127-153813-styx-take-ab` from ai-styx-session, done (exit 0)
- Waited 0 h after approval, ran 2.2 h including the build (estimate 4 h)
- Load over 27 samples: CPU 73% average, 94% max; other Maven builds in 2 samples (c:/users/willh/onedrive/documents/github/ti4_map_generator_bot/.claude/worktrees/ai-faction-usage)
