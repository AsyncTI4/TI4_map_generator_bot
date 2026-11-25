# 20261110-attack-memory

Rebuilt on 2026-11-22 from the notes the run was reported in; the raw data was not kept.

**Question:** Leave-one-out: what does remembering attacks add?

**Verdict:** Kept.

## Code

- Commit: before 14bebc007b

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 48, complete pairs 24, base seed 7000
- Tested: ATTACK_MEMORY

## Results

- Treatment minus control points: +0.29 ± 0.22
- Treatment win share: 0.50 ± 0.07
- Round / leader / table: —

Same stack-overflow caveat as the other 2026-11-10 leave-one-out runs.
