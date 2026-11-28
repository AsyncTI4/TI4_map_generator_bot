# 20261109-aa-seed5000

Rebuilt on 2026-11-22 from the notes the run was reported in; the raw data was not kept.

**Question:** How noisy is the paired point difference when both halves run the same code?

**Verdict:** Calibration: noise ±0.26 VP at 12 pairs; same-seed pairs still diverge (pair SD ~0.9 VP), ~35 pairs needed for a 0.3 VP effect.

## Code

- Commit: 8240743145 (claude/ai-strength-audit base)

## Setup

- Harness: `AiSelfPlayTest#sixAiSeatsPlayFullGames`
- Games: 24, complete pairs 12, base seed 5000
- Tested: A/A

## Results

- Treatment minus control points: +0.01 ± 0.26
- Treatment win share: —
- Round / leader / table: 6.1 / 10.2 / 44.9

24/24 games ended on points. About 25 CPU-minutes per game; 24 games on 10 threads took 2.4 h.
