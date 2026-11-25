# AI A/B and benchmark runs

One row per run, oldest first. Points and win share are treatment minus control over complete pairs, mean
± standard error; "Round / leader / table" is the average final round, leader points and table points of the
whole batch. Fill in the verdict after reading a run. See README.md for how to read this table.

Rows dated before 2026-11-22 were rebuilt from the notes they were reported in: their raw data was not kept and
some commits and seeds are unknown.

| Run | Date | Commit | Tested | Pairs (games) | Points | Win share | Round / leader / table | Verdict |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| [20261109-aa-seed5000](runs/20261109-aa-seed5000/README.md) | 2026-11-09 | 8240743145 (claude/ai-strength-audit base) | A/A | 12 (24) | +0.01 ± 0.26 | — | 6.1 / 10.2 / 44.9 | Calibration: noise ±0.26 VP at 12 pairs; same-seed pairs still diverge (pair SD ~0.9 VP), ~35 pairs needed for a 0.3 VP effect. |
| [20261110-promise-deals](runs/20261110-promise-deals/README.md) | 2026-11-10 | before 14bebc007b | PROMISE_DEALS | 24 (48) | +0.47 ± 0.20 | 0.68 ± 0.05 | — | Kept. |
| [20261110-win-break-denial](runs/20261110-win-break-denial/README.md) | 2026-11-10 | before 14bebc007b | WIN_BREAK_DENIAL | 24 (48) | +0.29 ± 0.20 | 0.66 ± 0.08 | — | Kept. |
| [20261110-attack-memory](runs/20261110-attack-memory/README.md) | 2026-11-10 | before 14bebc007b | ATTACK_MEMORY | 24 (48) | +0.29 ± 0.22 | 0.50 ± 0.07 | — | Kept. |
| [20261110-win-threat-feasible](runs/20261110-win-threat-feasible/README.md) | 2026-11-10 | before 14bebc007b | WIN_THREAT_FEASIBLE | 24 (48) | -0.09 ± 0.16 | 0.50 ± 0.07 | — | Kept on: neutral, and it reads rivals' real paths to points. |
| [20261110-all-seed9100](runs/20261110-all-seed9100/README.md) | 2026-11-10 | eaaf376392 (documented in) | all | 24 (48) | +0.29 ± 0.21 | 0.60 ± 0.07 | 5.6 / 10.2 / 42.5 | Everything on beats everything off; all experiments stay on. |
| [before-20261116-strength-bundle](runs/before-20261116-strength-bundle/README.md) | before 2026-11-16 | unknown | threat, token price and stall timing bundle | 24 (48) | — | 0.35 | — | Reverted: threat and denial now come from feasible point paths; the stall, token and tech-tempo terms were dropped. |
| [20261112-agenda-value-table-seed7](runs/20261112-agenda-value-table-seed7/README.md) | 2026-11-12 | unknown (before the agenda trace fixes) | AGENDA_VALUE_TABLE | 12 (24) | -0.19 | — | — | Inconclusive: agenda utility +0.087 ± 0.089 TG (A/A noise ±0.096 TG at 12 pairs). |
| [20261112-agenda-value-table-seed11](runs/20261112-agenda-value-table-seed11/README.md) | 2026-11-12 | unknown (before the agenda trace fixes) | AGENDA_VALUE_TABLE | 12 (24) | +0.06 | — | — | Inconclusive: agenda utility +0.098 ± 0.112 TG. |
| [20261117-desperation-combined](runs/20261117-desperation-combined/README.md) | 2026-11-17 | before c94304b0b5 | desperation (one combined 1.5x experiment) | — (—) | -0.65 ± 0.23 | — | — | Split into three experiments and the boost lowered. |
| [20261117-desperation-public](runs/20261117-desperation-public/README.md) | 2026-11-17 | c94304b0b5..c1cb4e5e70 | DESPERATION_PUBLIC | 12 (24) | -0.13 ± 0.27 | — | — | Neutral; off by default since c1cb4e5e70. |
| [20261117-desperation-status-secret](runs/20261117-desperation-status-secret/README.md) | 2026-11-17 | c94304b0b5..c1cb4e5e70 | DESPERATION_STATUS_SECRET | 12 (24) | -0.51 ± 0.28 | 0.375 | — | Harmful; off by default since c1cb4e5e70. |
| [20261117-desperation-action-secret](runs/20261117-desperation-action-secret/README.md) | 2026-11-17 | c94304b0b5..c1cb4e5e70 | DESPERATION_ACTION_SECRET | 12 (24) | -0.51 ± 0.25 | 0.375 | — | Harmful (t -2.08); off by default since c1cb4e5e70. |
| [20261124-2107-pooled-boost-resplit](runs/20261124-2107-pooled-boost-resplit/README.md) | 2026-11-24 | b2ff2c28e2 | POOLED_BOOST_RESPLIT | 20 (43 of 48, interrupted) | +0.16 ± 0.21 | 0.55 ± 0.08 | 5.9 / 10.1 / 45.5 | Inconclusive: PC restart stopped the batch. |
