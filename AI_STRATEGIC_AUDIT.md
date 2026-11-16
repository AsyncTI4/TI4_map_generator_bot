# Strategic intelligence audit — PR #5846

Baseline: `b3766a0a2d0a69b98cf13e2ef7caccbaf2e8ee90`, including the November 15
incoming-force cache and the removal of JOINT_WIN_THREAT. The earlier local audit
checkout was removed between sessions; results below are rerun on this baseline.

## Architecture and assessment

The runtime perceives Discord prompts, asks the faction brain for a decision,
then presses a real engine button. The brain dispatches an ordered list of rules;
the first applicable rule wins. Specialized planners generate and score tactical
actions, strategy-card choices, production, trades and component actions.

Existing strengths include objective-aware scoring reserves, initiative-aware
win estimates, combat odds, defensive and staging plans, opponent victory denial,
hidden-hand boundaries, scoped memoization and seeded real-handler self-play.
The new tick cache shares only board facts that do not depend on the deciding
player's hidden information. The current policy is not a generic minimax or MCTS
search. Its principal limitation is the fidelity and consistency of its estimates,
plus sequencing across independently evaluated actions.

`AI_LOOKAHEAD_PLAN.md` now reports approximately 2,400 CPU-seconds per full game.
Its 48-game joint-threat shadow study changed only 0.7% of triggered decisions,
so that experiment was dropped. This audit does not reintroduce it. Those are
measurements supplied by the repository, not new measurements made here.

## Changes

### Feasible public-scoring sequences

`ScoringPaths` previously used a website display shortlist as a strategic point
estimate. That shortlist prioritizes two-point objectives before qualification,
then truncates to the number of scoring windows. An unavailable two-point
objective can hide a ready one-point objective. It also omits the AI's home-control
check. Both that path and `ScoringStakes` can count the same trade goods twice.

The new calculation uses the existing AI qualification checks and explores short
payment sequences against a shrinking wallet. Free objectives fill the remaining
scoring slots. It recognizes separately affordable spend objectives, rather than
arbitrarily allowing only one spend. It adds no heuristic weights and does not
change engine rules or access an opponent's hidden hand. The experiment switch
is selected by the deciding player, including when evaluating a rival.

Relevant rules: scoring requires fulfillment at the scoring window (LRR 61.5);
one public per status phase (LRR 61.6); payment at scoring (LRR 61.10); home-system
control (LRR 61.16). Imperial adds a public-scoring window. Existing non-public
point estimates and the existing active Winnu hero scoring opportunity are retained.

The search is deliberately bounded to the scoring windows already modeled.
`Wallet.plan` chooses one payment for each objective; exploring objective orders
does not enumerate every alternative planet allocation. The estimate is therefore
a feasible lower bound, not a proof of the optimal allocation in all cases.
Future income, intervening readying, secret-objective payments and territory lost
before scoring remain outside this calculation.

### Experimental results must exclude unfinished games

The original A/B summary awarded win credit to the current point leader even when
a game stalled or aborted. Paired estimates now require both games to finish and
report the unfinished-game count. Failures remain important: selective exclusion
can bias the surviving sample and is not evidence that the policy works.

## Algorithm choices and roadmap

| Priority | Change and strategic reason | Cost | Evaluation |
| --- | --- | --- | --- |
| 1 | Consistent legal public-scoring estimates prevent fictitious wins and missed scoring paths | Small bounded payment search | Rule-grounded scenario matrix; treatment/control switch |
| 1 | Exclude unfinished pairs from win estimates | Small, summary only | Regression tests across every ending |
| 2 | Combine public and secret payments, Imperial timing, readying and token reservations | Medium; nondominated wallet search | Endgame snapshots with known winning sequences |
| 2 | Calibrate win probabilities, including correlated objectives and action costs | Medium offline work, cheap production lookup | Held-out Brier scores and reliability curves, then win-rate ablation |
| 3 | Add token-lock and gathering features identified in the existing plan | Medium/high | Decision-change census and paired continuations |
| 3 | Compare elective macro-actions through a shared choice boundary | High; preserve mandatory prompt ordering | Replay disagreements and bounded rollouts |
| 4 | Bounded beam search or determinized rollouts over a few meaningful choices | High runtime cost | Budgeted latency and held-out mixed-policy tournaments |
| 5 | Learned value/policy ranking and broader opponent populations | High training cost | Held-out factions, positions, policies and competent human games |

Full MCTS and two-player minimax are not justified by these findings. More depth
would amplify inconsistent resource and timing estimates. A small macro-action
search, built on validated estimates, has a better cost/benefit case. The current
rollout experiments use point margin; that is not the same as victory probability.

## Remaining risks

WinOdds combines correlated opportunities approximately independently. Action-
phase finish estimates do not fully order two players' future actions. Denial of
one contender may be undervalued when another contender also threatens to win,
which requires coordinated sequencing rather than merely changing a probability
threshold. Secret readiness and many tactical thresholds are still heuristics.

The A/B harness splits win credit across tied highest scores instead of retaining
the engine's actual winner and every initiative/speaker tie rule (LRR 98.7–98.8).
Mixed-policy self-play does not establish performance against competent humans.
Cross-JVM seeded replay is documented as imperfect. No new win-rate claim is made
without completed games.

## Validation

Scenario accuracy is distinct from playing strength. The same 73-case suite ran
against the unchanged updated PR and against the proposed implementation:

| Measure | Updated PR baseline | Proposed |
| --- | --- | --- |
| Correct scoring scenarios | 41/73 | 73/73 |
| Failed scenarios | 32 | 0 |
| Unaffordable stage II hiding affordable stage I | Reports 0 instead of 1 | Reports 1 |
| Imperial, 10 trade goods, costs of 10 and 5 | Reports 3 instead of 2 | Reports 2 |
| Imperial, 15 trade goods, costs of 10 and 5 | Reports 3 | Preserves 3 |
| Lost home, otherwise affordable public | Reports a score | Reports 0 |

The suite contains a 68-position matrix (0–16 trade goods, Imperial held/not held,
home held/lost), plus five named scenarios. These are deliberately targeted
counterexamples, not a random sample of game decisions. Five additional tests
cover token payments, resource/influence competition, free objectives, the active
Winnu hero, state preservation, and the deciding player's experiment perspective.

The final relevant regression run passed 1,215 tests with zero failures/errors.
This covers scoring, strategy, trading, the new summary test, segregation, slash
command limits and testbed data. It took 1 minute 51 seconds including recompilation.
Scoped Spotless checks and `git diff --check` passed. No testbed scripts, presets
or shortcuts were edited.

Runtime is bounded by the modeled scoring slots: normally one or two, three with
the existing active-hero opportunity. With ten spend candidates, a three-slot
search visits at most 820 payment choices. One-slot evaluation uses the existing
qualification results directly; scoped memoization shares repeated estimates.
These are algorithmic bounds, not measured decision-latency or throughput claims.

No full-game A/B result is claimed. The previous session's exploratory run did not
produce a retained completed result before interruption, and its directory was
subsequently removed. The new repository measurements put a 12-game batch at
roughly eight CPU-hours. This iteration uses the permitted representative
strategic benchmark fallback. Win-rate improvement against competent opponents
remains unmeasured; that is a material limitation, not a positive result.

Reproduction from this checkout (PowerShell, JDK 26):

```powershell
$agent = '-DargLine=-javaagent:C:\Users\willh\.m2\repository\org\mockito\mockito-core\5.23.0\mockito-core-5.23.0.jar'
mvn -o compiler:compile compiler:testCompile
mvn -o surefire:test '-Dtest=ScoringPathsTest' '-Dai.experiments.off=READY_PUBLIC_POINTS' $agent
mvn -o surefire:test '-Dtest=ti4.ai.scoring.*Test,ti4.ai.trade.*Test,ti4.ai.strategy.*Test,AbSummaryTest,AiSegregationTest,SlashCommandLimitsTest,TestBedDataTest' $agent
```

The baseline command intentionally fails. Mockito is preloaded because runtime
attachment stalled in the earlier sandboxed run. Local logs:
`baseline-paths.log`, `validation.log`, `validation-final.log`, `formatting.log`.

For a future paired strength experiment, use `AiSelfPlayTest#sixAiSeatsPlayFullGames`
with `ai.selfplay.full=true`, `ai.selfplay.ab=READY_PUBLIC_POINTS`, an even game
count, fixed `ai.selfplay.seed`, and a thread count that avoids contention. Report
completion rate, paired uncertainty, faction/position rotation, and actual
winner handling alongside the result. Do not interpret a small noisy batch as
proof that the AI is stronger.
