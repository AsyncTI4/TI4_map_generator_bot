# 20261128-0407-agenda-prior-measure

**Kind:** ab

**Question:** What is the average full agenda deck spread a kept vote is priced from, per seat and round? Replays the 12 styx-take-ab games (seed 9900, STYX_TAKE A/B, Fracture, 12 threads; batch at d9f34394a1, 2.2 h) with only the AGENDAPRIOR probe added, which runs in an isolated memo, so every game should match styx-take-ab exactly.

**Verdict:** Measured; used as the default. All 12 games replay styx-take-ab exactly (same endings, rounds, ticks, scores and winners), so the probe changes nothing. 600 AGENDAPRIOR lines (one per seat per agenda; no known next agenda in any): full deck spread mean 4.107, sd 1.43, median 3.65, p10 2.97, p90 6.30, max 11.93; by round r1 2.98 (24), r2 3.41 (132), r3 3.57 (144), r4 3.97 (162), r5 5.52 (102), r6 6.15 (36); by seat 3.88 (sardakk) to 4.44 (xxcha); final agendas 4.10, others 4.11. With 43 to 127 votes at the table, the kept vote 0.25 x spread / table votes was at the 0.02 floor in 586 of 600 cases (max 0.026), while one full look-ahead took 10.4 s on average under 12-thread load. A single constant 4.1 is used (AgendaPrior.AVERAGE_DECK_SPREAD); the round trend does not matter because the floor binds. The full look-ahead stays behind AGENDA_DECK_LOOKAHEAD, off by default. Follow-up: an A/B of AGENDA_DECK_LOOKAHEAD if the points need checking; AgendaPeek still averages the deck for deckExpected, but only when a seat peeks.

## Code

- Commit `41c3811714554e6d2f7011a5083a9f89f0611334` on `HEAD`: Probe the full agenda deck look-ahead a kept vote is priced from
- Uncommitted changes: none

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 12 on 12 threads, base seed 9900
- Tested: [STYX_TAKE]
- Properties: `{ai.selfplay.ab=STYX_TAKE, ai.selfplay.agendaprior=true, ai.selfplay.fracture=true, ai.selfplay.full=true, ai.selfplay.games=12, ai.selfplay.queue.id=20261127-183501-agenda-prior-measure, ai.selfplay.record.dir=C:\Users\willh\OneDrive\Documents\GitHub\TI4_map_generator_bot\.claude\worktrees\agenda-prior-average\docs\ai\experiments, ai.selfplay.record.label=agenda-prior-measure, ai.selfplay.record.question=What is the average full agenda deck spread a kept vote is priced from, per seat and round? Replays the 12 styx-take-ab games (seed 9900, STYX_TAKE A/B, Fracture, 12 threads; batch at d9f34394a1, 2.2 h) with only the AGENDAPRIOR probe added, which runs in an isolated memo, so every game should match styx-take-ab exactly., ai.selfplay.seed=9900, ai.selfplay.threads=12}`

## Results

- Endings: {VICTORY_POINTS=12}
- Average final round 5.917, leader points 10.083, table points 45.75
- Batch wall time 8123 s, 1084.905 ms per tick
- Treatment minus control points: 0.417 ± 0.521 (t 0.80) over 6 complete pairs
- Treatment win share: 0.667 ± 0.167 (t 1.00) (null 0.500)
- Unfinished games (their pairs excluded): 0

The rows are in `games.csv`; the console output is in `console.log.gz`.

## Queue

- Job `20261127-183501-agenda-prior-measure` from agenda-prior-session, done (exit 0)
- Waited 9.49 h after approval, ran 2.29 h including the build (estimate 2.5 h)
- Load over 28 samples: CPU 75% average, 100% max; other Maven builds in 0 samples (none)
