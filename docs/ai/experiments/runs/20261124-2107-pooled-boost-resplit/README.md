# 20261124-2107-pooled-boost-resplit

**Question:** Does re-splitting shared one-ship move boosts (Gravity Drive, Tempesta, Imperator) across multi-system strikes (POOLED_BOOST_RESPLIT) change points or win share?

**Verdict:** Inconclusive. The batch was interrupted by a PC restart after 43 of 48 games; 20 complete pairs give +0.16 ± 0.21 VP, within noise.

Decision (2026-11-25): kept as final at 20 pairs; no second batch. POOLED_BOOST_RESPLIT stays on, as it is neutral-to-slightly-positive and follows the rules.

## Code

- Commit: b2ff2c28e2 (merge of claude/ai-agenda-expert into claude/ai-styx-take)

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`, full games, 10 threads
- Games: 48 planned, base seed 9800, pairs share a seed
- Tested: POOLED_BOOST_RESPLIT

## Results

Scored by hand from `.git/ai-ab-queue/logs/20261124-210550-pooled-boost-resplit.log` with the harness rules
(control seats are those with `(index + slot) % 2 == 0`, only pairs with both games ending on victory points count),
because the run never reached its own summary. Slots 37, 40, 44, 46 and 47 did not finish, so pairs 18, 20, 22
and 23 are left out.

- Games finished: 43, all VICTORY_POINTS
- Complete pairs: 20
- Treatment minus control points: +0.16 ± 0.21
- Treatment win share: 0.55 ± 0.08 (null 0.500)
- Round / leader / table: 5.93 / 10.14 / 45.53

The console log is `console.log.gz`.
