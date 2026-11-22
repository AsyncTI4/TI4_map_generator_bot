# 20261110-promise-deals

Rebuilt on 2026-11-22 from the notes the run was reported in; the raw data was not kept.

**Question:** Leave-one-out: what do promise deals add?

**Verdict:** Kept.

## Code

- Commit: before 14bebc007b

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 48, complete pairs 24, base seed 7000
- Tested: PROMISE_DEALS

## Results

- Treatment minus control points: +0.47 ± 0.20
- Treatment win share: 0.68 ± 0.05
- Round / leader / table: —

One to three games of this series of runs were lost to a stack overflow in the Mecatol take (since fixed); those games still counted in their pairs.
