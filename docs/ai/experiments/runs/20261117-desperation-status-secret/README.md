# 20261117-desperation-status-secret

Rebuilt on 2026-11-22 from the notes the run was reported in; the raw data was not kept.

**Question:** Desperation on status-phase secrets only.

**Verdict:** Harmful; off by default since c1cb4e5e70.

## Code

- Commit: c94304b0b5..c1cb4e5e70

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 24, complete pairs 12, base seed 9700
- Tested: DESPERATION_STATUS_SECRET

## Results

- Treatment minus control points: -0.51 ± 0.28
- Treatment win share: 0.375
- Round / leader / table: —
