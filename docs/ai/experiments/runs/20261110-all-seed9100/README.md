# 20261110-all-seed9100

Rebuilt on 2026-11-22 from the notes the run was reported in; the raw data was not kept.

**Question:** Do all the experiments together beat all of them off, after the stack overflow fix?

**Verdict:** Everything on beats everything off; all experiments stay on.

## Code

- Commit: eaaf376392 (documented in)

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 48, complete pairs 24, base seed 9100
- Tested: all

## Results

- Treatment minus control points: +0.29 ± 0.21
- Treatment win share: 0.60 ± 0.07
- Round / leader / table: 5.6 / 10.2 / 42.5

All 48 games ended on points.
