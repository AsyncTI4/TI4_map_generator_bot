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
| [20261124-2107-pooled-boost-resplit](runs/20261124-2107-pooled-boost-resplit/README.md) | 2026-11-24 | b2ff2c28e2 | POOLED_BOOST_RESPLIT | 20 (43 of 48, interrupted) | +0.16 ± 0.21 | 0.55 ± 0.08 | 5.9 / 10.1 / 45.5 | Inconclusive: PC restart stopped the batch; kept at 20 pairs, flag stays on. |
| [20261126-0035-styx-take-rate](runs/20261126-0035-styx-take-rate/README.md) | 2026-11-26 | 513e5d1ce6 | benchmark | 32 |  |  | 5.75 / 10.188 / 45.156 | Styx taken 18/32 (56%), was 5/16 on seeds 1-16 now 8/16; first take r4.9, 10 of 14 misses ended r5. Below 80%. |
| [20261126-1309-replay-9701-harness-timing](runs/20261126-1309-replay-9701-harness-timing/README.md) | 2026-11-26 | e9c328fc28 | benchmark | 1 |  |  | 6.0 / 11.0 / 50.0 | replays exactly in 2.2 h alone; batch slowness is the harness agenda tracker under 10 threads (likely shared PlanInputs cache) |
| [20261126-1920-replay-9701-tracker-memo](runs/20261126-1920-replay-9701-tracker-memo/README.md) | 2026-11-27 | e204f695c7 | benchmark | 1 |  |  | 5.0 / 10.0 / 45.0 | kept: harness between-tick CPU 1004 s -> 31 s (agenda tracker 974 -> 3.9 s); decision CPU not comparable (different base: no Styx work, has ranking caches) |
| [20261126-2149-styx-batch-tracker-memo](runs/20261126-2149-styx-batch-tracker-memo/README.md) | 2026-11-27 | 92289cc6a4 | benchmark | 32 |  |  | 5.75 / 10.188 / 45.156 | kept: same 32 games exactly, batch wall 31,095 -> 25,448 s (-18%); harness-slow games 25-51% faster (9701 28,123 -> 14,119 s); harness now <= 5.4% of a game |
| [20261127-1539-styx-take-ab](runs/20261127-1539-styx-take-ab/README.md) | 2026-11-27 | d9f34394a1 | [STYX_TAKE] | 6 | +0.42 ± 0.52 | 0.67 ± 0.17 | 5.917 / 10.083 / 45.75 | Inconclusive on points; kept. All 6 first Styx takes by STYX_TAKE seats, none by control; Styx taken in 6/12 games (50%), short of 80%. |
| [20261128-0407-agenda-prior-measure](runs/20261128-0407-agenda-prior-measure/README.md) | 2026-11-28 | 41c3811714 | [STYX_TAKE] | 6 | +0.42 ± 0.52 | 0.67 ± 0.17 | 5.917 / 10.083 / 45.75 | measured: same 12 games exactly; mean full deck spread 4.1 (r1 3.0 to r6 6.2), kept vote at the 0.02 floor in 586/600, 10 s per look-ahead; 4.1 is now the default, look-ahead behind AGENDA_DECK_LOOKAHEAD |
