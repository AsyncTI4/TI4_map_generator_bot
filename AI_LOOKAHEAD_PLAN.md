# AI lookahead and thinking-time plan

Tracks the work toward the AI thinking further ahead: gathering a fleet over several turns, weighing the ships a
command token locks in, and using the longer thinking time an async game allows. Each step sits behind an
`AiExperiments` flag or a debug switch, and stops if its check fails. Tick a step when its check has run, and write
the result next to it.

## Where this comes from

Two studies on 2026-11-10 and 2026-11-11 (code reading, logs and local game data; no new game code was run for them):

- Chess or Go style search is not feasible here. A real-engine 3-round playout costs about 1,200 CPU-s, two
  continuations of one position differ by about 1.8 VP after 3 rounds, and telling options 0.25 VP apart needs about
  207 paired playouts per option (138 CPU-hours per decision). There is no score for a whole position. A playout run
  with today's policy can't discover gathering, because that policy never gathers.
- The two gaps (gathering, token lock) are missing planner features, not missing search depth.
- Real pace: at 6-seat tables the median table averages about 101 minutes per stored turn (p10 41, p90 241; overnight
  gaps included). Under 1% of tables average under 15 minutes.
- Today's limits: `AiSettings.DECIDE_TIMEOUT` is 20 s and a decision over it is discarded; self-play turns the limit
  off; all AI games share 2 worker threads (`AiRuntime.WORKER_THREADS`); a press checks only that its button still
  exists, not that the game is unchanged.
- Testing is the binding constraint, not hosting: every budget level and every feature needs its own A/B run.

## Steps

- [ ] **1. Measure.** Debug timing over the major flows (perception, each brain rule, planner rankings, defense, win
  odds, pricing, game loads and saves, actuation) with a batch summary of where time goes and the decision-time tail.
  Also: production core count and memory (`nproc`, `free -h` on the host), how often decisions exceed 20 s without
  contention, the margin between the planner's top two plans, how often the six rules ahead of the tactical rule decide
  the turn, and the cost of a cold ranking.
  - Check: if a cold ranking costs about 6x a warm one, round-level search (about 333 CPU-s) does not fit a
    2-minute budget.
  - Timing report (built 2026-11-11): add `-Dai.timing=true` to a self-play batch. The end of the batch prints
    `TIMING` lines: decision wall and CPU percentiles, buckets, how many decisions would pass the live 20 s limit,
    time by outcome and by the rule that decided, every span by self time (calls, mean, longest call), and time outside
    decisions (`ai.tick`, `ai.load`, `ai.seat`, `ai.perceive`, `ai.actuate`, `harness.observe`). Timing spans use
    `Thinking.measure`, which never cuts a decision short and does not count toward the depth limit, so turning the
    report on cannot change a decision. Each brain rule shows as `rule.<Class>.<method>`.
    - Done: timing report.
    - Still to do: host cores and memory, the top-two plan margin, a cold-ranking cost.
  - First measurement, 2026-11-11: 6 full games, 6 threads on 16 cores (uncontended), seed 9300.
    - Game shape: final round 6.2, leader 10.2 VP, table 46.0.
    - Decisions: 166,820, about 2,400 CPU-s per game, nearly all of it AI thinking. Median 1 ms, p90 19 ms, p99
      2.6 s, p99.9 7.6 s, max 32 s.
    - Uncontended, only 6 decisions passed 20 s (0.004%), so step 2's check says leave the limit alone. Contention,
      not the limit, made decisions slow in the 14-thread batches.
    - Where it goes (self time, share of all decision time):
      | Span | Share | Calls |
      | --- | --- | --- |
      | `winOdds.compute` | 12.7% | 791k |
      | `strike.estimate` | 11.3% | 2.4M |
      | `defense.space` | 9.6% | 5.7M |
      | `tactical.for.attack` | 8.9% | — |
      | `incoming.force` | 8.4% | 59M |
      | `defense.threat` | 8.0% | 21M |
      | `secrets.keepValue` | 4.1% | — |

      `tactical.rank.base` (35k rankings, 330 ms mean) contains 76% of all decision time; only 10.8k of those
      rankings are the seat's own `takeTurn`.
    - Idle decisions (no rule fired) are 65% of decisions and 28% of decision time (38 ms mean). Polls of an
      unchanged game re-run rules that find nothing to do. A cache keyed on the save's revision, not the `Game`
      instance (each tick reloads it), is the obvious first optimization.
    - Other heavy nested paths: `trade.votePromises` (8.2k calls, 3,856 s total), `agenda.forecast` (3,740 s total),
      `strategy.planInputs` (4,141 s total), `production.plan` (3,890 s total).
    - This run used the first build, in which timing frames still counted toward the depth limit; no decision was cut
      short.
- [ ] **2. Stop discarding slow decisions.** Give the AI's own turn about 60 s, count calls for the logic limits, and
  keep wall clock only as a backstop.
  - Check: if under 1% of decisions exceed 20 s without contention, leave the limit at 20 s.
- [ ] **3. Joint win threat.** Five rivals at 0.40 each pass the per-rival 0.5 test, yet one of them finishes first 92%
  of the time (`WinOdds`, `WinRace`). Use the combined chance, as `DenialMap` already does.
  - Check: a 48-game shadow run that changes under 5% of triggered decisions means drop it.
- [ ] **4. Cheaper testing.** Paired continuations from snapshots with `decisionOverride`; randomized flags per seat.
  - Check: if the standard error is well above 0.08 VP at a one-round horizon, go back to whole-game A/B.
- [ ] **5. One-round own-seat lookahead, logging only.** Imagine each top plan on a board copy and rank again. Needs
  fresh memo scopes: `DecisionMemo` keys carry no game and `Player.equals` compares only the user id.
  - Check: under 5% of activations change, or flipped decisions don't gain in step 4's test, means stop and skip 8-9.
- [ ] **6. Token lock and gathering as planner features** (independent of step 5).
  - Token lock: score a plan with what the remaining tokens can still do this round; add a candidate that attacks with
    the smallest force that wins.
  - Gathering: credit `computeFuture` with all ships in reach for a next-round strike, save the strike intent between
    turns and re-check it every turn, price a next-round Diplomacy block and defender production.
  - Check: a census showing packing changes few activations, then a 120-pair A/B (about 13.6 h) judged against a bar
    set in advance.
- [ ] **7. Final-round win race.** Own immediate points and rival removals only, with closed-form odds, from goal
  minus 2-3.
  - Check: under 5% of triggered decisions change, or no better calibrated than the simple VP-short lookup, means drop.
- [ ] **8. Round-level plan search**, only if 5 or 6 show a gain: typed result (tile, card, component, pass), its own
  1-thread pool, a game-version check before pressing.
  - Check: about 33 h of A/B; power is poor (29% at a true +0.125 VP), so decide the acceptance bar in advance.
- [ ] **9. Pace-scaled budget and the middle tier** (multi-round strike plans, more hidden-card samples), only if 8
  gains. Store pace in the save, not in memory.
- [ ] **10. Real-engine playouts, offline only**, for tuning and calibration (endgame first, from round 4).

## Done along the way

- 2026-11-11: three harness leaks fixed (commit 7a9b10daf7): rollouts saw the next objectives, snapshots left the
  attack ledger's picture behind, copying a game stamped the live game as modified.

## Unknowns

- Production vCPU count, RAM and heap headroom.
- True per-turn durations (no per-turn timestamps are stored).
- Model bias of lookahead and search, which is what really limits them.
- How humans pace and play with an AI seat at the table.
