# Plan: expert-level agenda play for the AI

Worktree: `C:/Users/willh/OneDrive/Documents/GitHub/TI4_map_generator_bot/.claude/worktrees/vigorous-cerf-c2baec`, branch `claude/ai-player-nekro-72bbce`. All paths below are relative to that root. The plan draws on your four guidance notes (`ai-agenda-value-review`, `ai-agenda-politics-guidance`, `ai-agenda-timing-windows`, `ai-mech-notes`) and on maps of the current code. Nothing has been changed or built.

## How the work is ordered

0. **Integrate branches first.** `claude/ai-strength-audit` is 66 commits ahead of this branch, and this branch is 3 commits ahead of it. The audit branch already has the experiment flags (`ti4/ai/AiExperiments.java`, `src/test/java/ti4/ai/runtime/AbSummary.java`), a promise layer (`ti4/ai/promise/Promise.java`, `PromiseBook.java`, `PromiseChecks.java`, `Presence.java`, `ti4/ai/trade/PromiseDeals.java`), vote deals (`ti4/ai/agenda/VoteDeals.java`, `PromisedVotes.java`), agenda card swings (`ti4/ai/agenda/AgendaSwing.java`, `ti4/ai/actioncards/ShenaniganCards.java`, `EarlyDeadlyPlot.java`) and `ti4/ai/strategy/SpeakerPromise.java`. Every later milestone builds on those classes. None of them gets a parallel copy.
1. **Measurement before any behaviour change.** The agenda tracker and agenda-heavy harness (milestone 2) land first. The milestone 1 value table goes behind one experiment flag, and the baseline is taken with that flag off. Final VP is too noisy for this (±0.26 VP at 12 pairs), so the harness needs an agenda-level metric.
2. **Values next** (milestone 1). Most later features price things through `AgendaForecast` utilities: vote prices, Veto bounties, rider gains and bluff risk.
3. **Forecast correctness before new card plays and deals.** Every card rule and every deal compares two forecasts.
4. **Negotiation last, and kept simple.** It is the only layer that depends on human behaviour the AI can't read.

Execution order: 0 → 2 → 1 → 3 → 4 → 5 … 13.

Rules for every milestone:
- No comments in `src/main/java`.
- Official content only. Filter out `dspn*`, Edyn, Kyro, gamma and absol ids.
- Values are in TG units (`ValueScale`: 1 resource = 1 influence = 0.8 TG).
- Keep heuristics simple. No fog-of-war branches in `ti4.ai`.
- `AiSegregationTest` (`src/test/java/ti4/ai/AiSegregationTest.java`): engine code must not reference `ti4.ai`. An engine seam I add (for example making `AgendaHelper.getVotingOrder` public) is a plain helper that ti4.ai calls.
- New unowned buttons reach the AI only once their prefix is in `NekroBrain.PUBLIC_WINDOW_PREFIXES` (`src/main/java/ti4/ai/nekro/NekroBrain.java:118`).
- Never write markers into `currentAgendaVotes`, because resolution reads it.
- **Hidden information:** a value function may read real hand or secret contents only when the holder is the viewer. Every other holder, including a rival modelled as voting for itself inside the forecast, gets priors (see 1b).
- **No forecasts inside value functions:** nothing under `AgendaValues` may call `BallotPlanner` or `AgendaForecast`. `BallotPlanner.choose` → `AgendaForecast.of` → `AgendaUtility.of` → `AgendaValues` would recurse.
- Each behaviour change gets its own `AiExperiments` flag.

---

## Milestone 0: branch integration (S-M, first)

- Decide the direction: merge `claude/ai-strength-audit` into this branch, or rebase this branch's 3 commits onto it. Either way, both lines end up on one branch before agenda work starts. Cherry-picking `AiExperiments` alone is not enough.
- What this brings in: `AiExperiments`, the `-Dai.selfplay.ab` mixed-brain switch and the `SelfPlayEnvironment` control-seat wiring, `AbSummary`, the committed `AiGameWrites` fix, the promise layer (including the `PROMISE_DEALS` experiment with vote promises), `VoteDeals` (outlook, bestSale, bestPurchase), `PromisedVotes` (binding, forbidsRider, verdict), `AgendaSwing` (Veto, Bribery, Deadly Plot, Confounding), `ShenaniganCards`, `EarlyDeadlyPlot` (AGENDA_SABOTAGE) and `SpeakerPromise`.
- After integrating, re-derive every line citation in this plan. They are taken from the current branch.
- Run all of `ti4.ai.*Test` on the merged branch, and write down which audit experiments are on by default.

---

## Milestone 1: point-law bug and your value table (M overall, many small changes, behind flag `AGENDA_VALUE_TABLE`)

**Goal:** `AgendaValues` returns your numbers for every official agenda, and the discard bug is fixed. The bug fix (1a) ships unflagged. The value table ships behind the flag and is A/B'd on the milestone 2 harness against the flag-off baseline.

### 1a. Point laws lose their point when discarded (S)
- `src/main/java/ti4/ai/agenda/LawChangeValues.java:21-47`: delete `POINTS_KEPT_ON_DISCARD` and its three uses (`judicialAbolishment` at 30, `miscountDisclosed` at 37, `newConstitution` at 43).
- A discarded point law costs its holder a full point (`ScoringStakes.scoreValue` for 1 VP), not the keep-chance-weighted election value. When a rival loses one, you gain its `rivalPointStake`.
- Tests in `src/test/java/ti4/ai/agenda/AgendaValuesTest.java`:
  - Abolishing your own Shard costs about one point.
  - Abolishing a rival's Shard is positive.
  - New Constitution with your Holy Planet law in play is negative for you.
  - Miscount Disclosed on a rival's Crown counts it at full value.

### 1b. Shared helpers and the viewer seam (S-M)
- **Viewer seam (new, before any own-hand term).** `AgendaValues.of(game, voter, agenda, outcome, ownVote)` (`src/main/java/ti4/ai/agenda/AgendaValues.java:26`) has no viewer, and `AgendaForecast.baseUtilities` (`src/main/java/ti4/ai/agenda/AgendaForecast.java:193-203`) calls it with every rival as voter.
  - Thread a `viewer` (or a `readsOwnHand` flag, like the `readsOwnSecrets` that `AgendaUtility.of` already takes) down into `AgendaValues` and the value classes.
  - Real hand and secret contents are read only when holder == viewer.
  - Every other holder gets priors: `getAcCount × ActionCardPrior.AVERAGE_CARD` for hands, `SecretOdds` for secrets. This follows the existing `AgendaValueMath.handValue` pattern (`src/main/java/ti4/ai/agenda/AgendaValueMath.java:112-117`).
  - **Leak test:** swap a rival's hand and secrets, and the AI's forecast stays the same.
- `AgendaValueMath` gets:
  - `roundTable(game, r2, r3, r4plus)`, where round 1 counts as round 2.
  - `endsSoon(game)`: `GameHorizon.roundsLeft <= 1` (`src/main/java/ti4/ai/eval/GameHorizon.java:14-50`).
  - `speakerPosition(game, seat)`, from `Helper.getSpeakerOrFullPriorityOrder`.
  - `isEasySecret(seat, secret)`: one threshold on `SecretValue.keepValue`, used by Archived Secret, Search Warrant and Classified Leaks. It applies to the viewer's own secrets only; rivals use `SecretOdds.chanceReady`.
- Faction checks follow the existing alias style (`ForAgainstLawValues.YSSARIL` at line 45, and the `startsWith("keleres")` pattern at `AllianceValue.java:123`). Creuss is `ghost` and Last Bastion is `bastion`. Before coding, check the aliases for Jol-Nar, Cabal, L1Z1X and Deepwrought, and the planet id for Mallice, against the faction and planet data. A wrong alias fails silently, so each one gets a test.

### 1c. Elect Player constants (S), in `src/main/java/ti4/ai/agenda/ElectionValues.java`

Your flat numbers are the holder's worth. The only scaling kept is the existing `winsWithPoint` override (`ElectionValues.java:185-194`), which stops the AI handing a rival the winning point. An end-game scale-up of point elections is not in the plan. It is a possible later experiment behind its own flag (still open).

| Agenda | Change |
| --- | --- |
| Archived Secret (68-78) | Own: 1 TG if all secrets are scored or any unscored secret `isEasySecret`, else 4. Rivals: `SecretOdds.chanceReady`, and 4 when unknown. |
| Committee Formation (80-89) | roundTable 4/2/1. The +2 for a known good election comes in milestone 6. |
| Imperial Arbiter (41, 91) | 5. |
| Commerce (95) | roundTable 3/2/1, +2 for Keleres. |
| Exploration (103) | 1. |
| Industry (112) | 1, or 3 for Keleres or Creuss before round 4. |
| Peace and War (47, 49) | 5. |
| Policy (120) | 1, or 0 when `endsSoon`. +2 for Yssaril unless `endsSoon`. |
| Sciences (124) | `max(1, 4 - (max(round, 2) - 2))`, +2 for Jol-Nar. Round 1 gives 4. Gets a round-1 test. |
| Antiquities (133) | Flat 5. |
| Prophecy (137) | 1. |
| Thalnos (196) | 0.25. |
| Research Grant Reallocation (245-260) | 3. The use side is in milestone 13. |
| Search Warrant (262-269) | Own: `2 TG × min(2, 3 - scored - easy unscored)`. Hard secrets in hand don't count against the cap, because the holder discards them. -1 TG if the holder has an easy unscored secret, more for `SecretPhase.ACTION`. Rivals: counts and `SecretOdds` only. Test: a holder with 2 hard unscored secrets values it at 4 TG. |
| Political Censure (220-243) | Own: 3 if the hand is good (sum of `ActionCardValue.keepValue` above a threshold, or at least one A/S card via `ExpertTiers`), else 5. Rivals: `getAcCount` and `AVERAGE_CARD`, through the viewer seam. |
| Shard of the Throne / Crown of Emphidia (173-194) | 6 and 1, as you gave them. |
| Armed Forces Standardization | Unchanged. |

All of these stay behind `electedGain` and `rivalGainCost`, so the threat-scaled rival weighting stays.

### 1d. Elect Planet constants (S), `src/main/java/ti4/ai/agenda/PlanetElectionValues.java:106-197`
- Core Mining 1, DMZ 2, Holy Planet 3, each Research Team 1, Senate Sanctuary 1, Terraforming 1.
- Compensated Disarmament (88-104): add a `needsTradeGoods(seat)` check from `Wallet.of` (`src/main/java/ti4/ai/scoring/Wallet.java:46`). Without that need the own-planet gain is zero, so the threat-weighted leader target wins.
- Colonial Redistribution: value unchanged. Bribes are a later variant (milestone 10).

### 1e. Law and directive fixes that need only existing inputs (S each)

In `src/main/java/ti4/ai/agenda/ForAgainstLawValues.java`:
- **Anti-Intellectual Revolution (34-35, 83-86):** already matches your note (1.0 × 1.5 TG per round, Nekro/Propagation exempt), apart from the 5-round `lawRounds` cap (`AgendaValueMath.java:31,91-92`). Add assertions: about 1.5 TG per round left for non-Nekro seats, 0 for Nekro. Done: it uses the true rounds left.
- **Homeland Defense (166-177):** For = 0. The Against PDS loss stays.
- **Executive Sanctions (137-151):** own cost is the keep value of the cards after the top 3 in `ActionCardValue.discardOrder` (`src/main/java/ti4/ai/actioncards/ActionCardValue.java:210`), viewer only. Rivals use excess × `AVERAGE_CARD`. Yssaril stays exempt.
- **Fleet Regulations (153-164):** subtract `FleetLimit.bonus` (`src/main/java/ti4/ai/eval/FleetLimit.java:18`) for Letnev's Armada.
- **Publicize Weapon Schematics (194-198):** drop or lower `WAR_SUN_SUSTAIN_PER_ROUND`. Test: a war-sun owner with a hand prefers For.
- **Regulated Conscription (206-223):** For bonus for Cabal and L1Z1X. The swarm-share term stays.
- **Shared Research (225-246):** For bonus for Bastion only. Against counts each seat's ship value in its home system, through `relativeToRivals`.
- **Wormhole Reconstruction (248-278):** For scales with fleet strength against the table mean. Against sums the unit cost of locked ships, not a tile count.
- **Representative Government (280-306):** clamp to ±2 TG a round.
- **Checks and Balances (319-344):** For = the existing `pointGapBehind` term (320-325) + a `speakerPosition` term. Late seats get +, early seats get -. The early-seat penalty drops when the speaker is a contender (`ScoringStakes.contender`).
- **Nexus Sovereignty (346-358):** the Mallice controller also likes For.
- **Enforced Travel Ban (111-135):** add wormholes in `Territory.of(game, seat)` times plastic weakness (`FactionAggression`, `src/main/java/ti4/ai/eval/FactionAggression.java:29`). Positive for weak seats, negative for aggressive ones.
- **Arms Reduction:** add a leader-target term through `ScoringStakes.contender`, so the table likes For when it hurts the leader. The anger/grudge input is skipped for now.

In `src/main/java/ti4/ai/agenda/DirectiveValues.java`:
- **Incentive Program (108-131):** Against bonus for Jol-Nar, Deepwrought and Hacan, plus a points-lead term. Faction scoring stats are deferred.
- **Swords to Plowshares (211-232):** leader term (`ScoringStakes.contender`) plus the count of revealed spend objectives (`ObjectiveCatalog.isSpend`).
- **Clandestine Operations (295-312):**
  - Against: the value of the cheapest ships over the new fleet limit in each system, including `FleetLimit.bonus`.
  - For: a cost when the seat's tactic pool or strategy pool drops to zero. It is the token at a marginal-token premium; for strategy tokens, add the value of the secondaries lost.
  - Both sides go through `relativeToRivals` ("maximum damage to others, little to yourself").
- **Economic Equality** and **Ixthian Artifact:** a small For bias in each seat's utility, unless that seat is badly hurt (a large TG loss, or a devastating Ixthian blast). Your notes say tables usually vote For (spite aside) and like Ixthian because it is fun.

In `src/main/java/ti4/ai/agenda/CardElectionValues.java`:
- **Classified Document Leaks (47-84):** the viewer's own secret is worth 3 TG. Each seat's gain is weighted by `SecretPhase` and readiness (`SecretOdds` for rivals), with a bonus for ACTION secrets when the seat leads or is far behind.
- **Galactic Crisis Pact (31-34, 106-117):**
  - Diplomacy is 0.
  - Technology is 0 unless ready resources minus a **static** vote-exhaustion estimate leave at least 4. Tech specialty discounts count as free.
  - Leadership and Warfare subtract the same static estimate.
  - The estimate is a fixed share of ready influence: the `BallotPlanner` reserve-style cap, or the snapshot count from `AgendaHelper.getAgendaStartVoteCounts`. Never `BallotPlanner` or a forecast; this is the recursion rule above.

**Follow-ups found by `AgendaExamTest`** (disabled there as `milestone 1e (follow-up)`; they ride the `AGENDA_VALUE_TABLE` flag A/B):
- **Articles of War For-voter payout netting** (`ArticlesOfWar.aStrongMechFactionVotesAgainst`): a strong-mech seat (Mahact) votes For, because the 3 TG every For voter collects when Against wins outweighs its For loss once the rivals' smaller losses are netted off. The payout should only count when Against is likely to win anyway, and the seat's own For votes should not be what passes the law.
- **Classified Document Leaks early secret** (`ClassifiedDocumentLeaks.votesForItsOwnScoredSecretEarly`): in round 2 the rivals' flat 50% later-score chance (`MAX_LATER_CHANCE`) cancels the own secret's 3 TG, so the seat abstains. The later chance should scale with the rivals' real odds of meeting the objective.

**Miscount Disclosed table behaviour** (count the votes left, place a few votes on a winnable outcome to steer others, maybe with bribes) is not covered here. It is a later item in milestone 9.

### 1f. Public Execution value (M), `ElectionValues.java:148-170`
- Replace "the single heir gains the speaker token" with a speaker-order shift. When the speaker is executed, every seat after it moves up one pick, worth a share of `SpeakerTokenValue.of` (`src/main/java/ti4/ai/strategy/SpeakerTokenValue.java:10`).
- Add target bonuses for Yssaril and Ral Nel, whose hand and silencing matter more.
- **Points leaders as targets:** check that `rivalGainCost`'s threat weighting already makes leaders the preferred target, and pin it with a test. Add an explicit contender bonus only if the test fails.
- The -10 in `AgendaPolicy.outcomeScore` (`src/main/java/ti4/ai/agenda/AgendaPolicy.java:229`) is reached only when `AgendaValues` has no value, and Execution has a case (`AgendaValues.java:55`). So it is left alone.

### 1g. Mech ratings (M): new `src/main/java/ti4/ai/eval/MechRating.java`
- Modelled on `FlagshipRating` (`src/main/java/ti4/ai/eval/FlagshipRating.java:12-51`: tier constants, a `RATINGS` map, `of(Player)`).
- Per official faction mech:
  - `articlesStake`: a flat per-faction value of what Articles of War For takes away. It doesn't scale with board count, because DEPLOY mechs (Letnev, Yssaril, Sol, Saar, Titans, Yin, Naalu TE, Crimson) come from reinforcements.
  - `onBoardAbility`: a small per-mech term, set only for mechs with on-board abilities (Xxcha cannon, Arborec production/shield, Deepwrought production, Empyrean cancel).
  - `homeWorth`: the value of a Rearmament home mech.
- Your marked ratings override; the rest use the ai-mech-notes priors (Articles column very high to none; Rearmament column home production, shield/cannon, home defense).
- **Articles of War (`ForAgainstLawValues.java:308-317`):**
  - Seat stake = `articlesStake + onBoardAbility × mechs on board`.
  - Keep `relativeToRivals`, and weight up neighbours' stakes through `Player.getNeighbouringPlayers` / `Adjacency`. That is your pattern (1): rival mechs next to you, such as Xxcha's cannon or facing Sardakk or Titans.
  - Keep the 3 TG Against-pays-For term, and push Against only with real vote weight.
- **Rearmament Agreement (`DirectiveValues.java:314-325`):**
  - Against = mechs on board × (the existing body downgrade `MECH_WORTH - INFANTRY_WORTH` + ability quality).
  - For = `homeWorth`, only when a mech is in reinforcements and the seat controls a home planet. Seats with all 4 mechs out gain nothing.
  - Tests: Arborec, Saar or Naaz with several mechs out fight Against. Hacan, Nekro or Jol-Nar support Against cheaply.

### Guidance covered
The whole value review except the items marked **design**, Miscount Disclosed table behaviour (milestone 9), the Arms Reduction anger term (skipped), Dictate Policy (milestone 3) and Covert (milestones 3 and 6).

### Tests (`AgendaValuesTest`)
- One holder-worth assertion per constant, on a solo table or with voter = elected.
- Round-table cases for Commerce, Sciences (round 1 too) and Committee.
- One test per faction bonus. These double as the alias checks.
- AIR assertions, a Search Warrant hard-secrets case, and the Checks and Balances behind-on-points case.
- The viewer-seam leak test.
- `MechRatingTest`: every official faction mech has a rating, and homebrew falls back to the prior.
- Update the tests that will break: Peace/War order (line 137) and the Core Mining/DMZ signs (163-184).
- `everyOfficialAgendaHasAFiniteValue` (63-93) stays.

### Risks
- Big value drops (Sciences ~12 → 4, Commerce ~8 → 3) move the thresholds built on these values: `AgendaPolicy.importantFor` and `veryBadFor` (`VERY_BAD_LOSS` 3.5, `AgendaPolicy.java:70,137-147`), the Quash threshold, and the `NotesForTrade` SECRET_IMPORTANT_AGENDA trigger. Re-check each and add a regression test per threshold. The flag A/B (milestone 2) measures the net effect.
- Base-only agendas (Elect Planet cards, Shard, Crowns, Rep Govt) never appear in PoK self-play, so only unit tests cover them.

---

## Milestone 2: measurement harness (M; before milestone 1's flag is turned on)

**Goal:** an agenda-local metric that shows a single change, with a baseline taken before any value or forecast change.

- **A/B slot pairing in `RoundTwoSelfPlayTest`.** The `-Dai.selfplay.ab` switch and `AbSummary` come from milestone 0, but only `AiSelfPlayTest#sixAiSeatsPlayFullGames` uses them. Add the same control-seat pairing to `RoundTwoSelfPlayTest` explicitly.
- **Add `src/test/java/ti4/ai/runtime/AgendaTracker.java`.**
  - Modelled on `DebateTracker` (`src/test/java/ti4/ai/runtime/DebateTracker.java:19`) and wired through `ObjectiveTracker.observe` (`ObjectiveTracker.java:126,146`).
  - Per agenda, keyed by `currentAgendaInfo`, it records round, agenda id, speaker, votes per seat, riders, outcome, and per-seat VP and TG deltas from this reveal to the next.
  - It reports only realized agenda utility, rider net value, agenda-sourced VP and harness defects.
- **Agenda-heavy self-play mode:**
  - Add a `-Dai.selfplay.roundtwo.playagenda` flag. Today `forceagenda` exists (lines 164, 322) but the agenda phases are skipped (30, 200, 343). With the flag, `forceagenda` credits custodians and the agenda phases are played.
  - The harness plays two rounds (`RoundTwoSelfPlayTest.java:30`), so that is 2 agenda phases, about 4 agendas a game. Add a `-Dai.selfplay.roundtwo.rounds=N` option; 3 rounds gives about 6 agendas.
  - Pair games with `objectiveseed`. The full `AiSelfPlayTest#sixAiSeatsPlayFullGames` stays as the guard run.
- **Harness defect:** `RefereeRules` (`src/main/java/ti4/ai/selfplay/RefereeRules.java:57,111`) presses tie-break buttons for AI owners after the referee delay. Give that step a long minAge, as `agendaResolution_` already has, so the AI's own `breakAgendaTie` always acts first. Count referee tie presses as defects.
- **Expert exam:** a new `AgendaExamTest` in `src/test/java/ti4/ai/agenda/`. Hand-built table positions, one or more per guidance bullet, each asserting the expert answer: which outcome to vote, whether to abstain, which rider, whether to Veto. Cases are added as milestones land. It is the cheapest regression signal and reads like your notes.
- **Baseline:** run A/A and the flag-off baseline here, before milestone 1's flag is turned on.
  - **Status (2026-11-11): not run yet, so milestone 2 is not done.** `AGENDA_VALUE_TABLE` and `GALACTIC_THREAT_COST` are already on by default, so the control seats of an A/B are the flag-off baseline.
  - From a frozen worktree, on at least 2 `-Dai.selfplay.objectiveseed` values: `mvn -o test -Dtest=RoundTwoSelfPlayTest -Dai.selfplay.roundtwo=true -Dai.selfplay.roundtwo.playagenda=true -Dai.selfplay.roundtwo.rounds=3 -Dai.selfplay.games=N -Dai.selfplay.ab=none` (A/A), then the same with `-Dai.selfplay.ab=AGENDA_VALUE_TABLE`.
  - Record the `AGENDA A/B` lines here (utility, rider net, agenda VP, agenda TG, SE over pairs) and the `AGENDA DEFECTS` line (referee-broken ties, fallback-broken ties, waits over budget).
  - Every seat's realized utility is scored with the expert table whatever its flags (`AgendaUtilityProbe`), so treatment and control share one yardstick. That yardstick is also the treatment's own value model, so read utility gains on the value-table flag together with agenda VP and the full-game guard.

---

## Milestone 3: Dictate Policy and the Covert prior (S)

- **Dictate Policy:**
  - In `AgendaUtility.of` (`src/main/java/ti4/ai/agenda/AgendaUtility.java:16-30`, next to the Drive the Debate term), for the viewer's own secret only.
  - If the viewer holds Dictate Policy and fewer than 3 laws are in play, add `scoreValue(1) × P(reaching 3 laws)` to For on any law. P is 1 when this law makes 3, and lower when more laws are needed.
  - `SecretValue.agendaChance` is private (`src/main/java/ti4/ai/secrets/SecretValue.java:231`), and its Dictate branch is just laws/`LAWS_FOR_DICTATE_POLICY`. Expose a new public `SecretValue.lawCountChance(game)` helper rather than calling it.
  - Once vote buying exists (milestone 10), this term makes the AI buy For votes with no extra code.
- **Covert Legislation, non-speakers:**
  - Replace the flat `COVERT_ELECTION_GAIN` (`ElectionValues.java:62,216`) with the average `AgendaValues` over the agendas still possible for the announced target type.
  - That average uses the deck composition from public data (all official agendas minus discards and laws), not the deck order.
  - Use the announced target only. Never read the type stored in `currentAgendaInfo`, which is not announced.
- **Tests:**
  - A Dictate Policy holder with 2 laws in play votes For on a mildly bad law, while a non-holder votes Against.
  - A Covert non-speaker's value doesn't change when the deck's top card is swapped (leak check).

---

## Milestone 4: forecast and ballot correctness (M-L, in this order)

**Goal:** the opponent model and the AI's own ballot get the rules right. Everything after this is measured against this forecast.

### 4a. Voting order bug (S, first)
- `AgendaForecast.votingOrder` (`AgendaForecast.java:171-178`) ignores Zeal (Argent votes first) and Hack Election. So `activeVoter` (280-286) can zero the votes of seats that haven't voted yet.
- Make `AgendaHelper.getVotingOrder` (`src/main/java/ti4/helpers/AgendaHelper.java:1552-1600`) public, use it, and find the speaker by seat rather than by position.
- Test: with Argent active, the seats after it keep their votes.

### 4b. Abstained values and the speaker's free Against (M)
- Split the forecast's utilities into an abstained value (`ownVote = ""`) and `penalty[voter][outcome]` (the dependsOnOwnVote difference, `AgendaValues.java:13-38`).
- `winner()` (365-373) and `AgendaPolicy.chooseOutcome` (`AgendaPolicy.java:98-109`) judge the speaker's tie-break with abstained values.
- `simulate` (306-333) gets a real **abstain** choice in each seat's softmax, priced as the seat's abstained utility of the outcome the speaker would pick. Penalty agendas then show the table abstaining.
- `BallotPlanner.expectedValue` (`src/main/java/ti4/ai/agenda/BallotPlanner.java:88-94`) keeps adding the viewer's own penalty only for the ballot it actually casts.
- Covers Conventions, Representative Government and Wormhole Research: the speaker picks Against for free, and the AI knows it both as voter and as speaker.
- Tests:
  - All seats are near-indifferent on Conventions, and the forecast gives Against about 100%.
  - The AI speaker on a 0-0 tie picks Against.
  - The AI non-speaker abstains on Wormhole Research when the speaker gains from Against.
  - Mutiny and Unconventional are checked separately, since they value the own vote differently.
- **Status (2026-11-12): first step done behind `AGENDA_VALUE_TABLE`.** A voter whose utilities spread by 1 TG or less abstains in the forecast instead of voting at random, so an indifferent table leaves the outcome to the speaker.
- **Status (2026-11-12): done for own-vote agendas** (`AgendaValues.dependsOnOwnVote`: Conventions, Representative Government, Wormhole Research, Articles of War, Mutiny, Unconventional Measures).
  - **Abstained values (unflagged bug fix):** the forecast keeps a table `ownVoteTerms[voter][winner][ballot]` (ballot = each outcome or abstain), the difference from the joining value, read with the voter's own view only for the viewer. The speaker's tie-break, in the forecast (`winner`, `heldBallot`) and for the AI speaker (`AgendaPolicy.chooseOutcome` through `AgendaForecast.tieBreakUtility`), is judged on the speaker's actual ballot: abstained values when it did not vote, since breaking a tie is not voting.
  - **Behind the new `AGENDA_ABSTAIN_CHOICE` (default on):** at its turn each simulated seat picks by softmax among the outcomes (joining values) and abstaining, priced at its abstained value of the outcome that wins without it (the tally so far plus later seats' provisional picks, the speaker's tie-break included); a seat whose best outcome is within 1 TG of abstaining abstains. The last voter (the speaker) sees the tally and best-responds: abstain or the fewest votes that make an outcome win, valued with its own-vote terms. An even tie for a speaker that did not vote goes to Against (the outcome no voter pays for). So on Conventions the table abstains and Against wins (`AgendaExamTest.theForecastExpectsAgainstWhenTheTableIsIndifferent`), the AI speaker picks Against on a 0-0 tie, the AI abstains on Wormhole Research when the speaker would pick Against, and on Mutiny the speaker ties or beats a thin For tally Against (`MutinyForecastTest.theSpeakerAnswersTheTallyAndPunishesTheForVoters`, `AbstainChoiceForecastTest`).
  - `BallotPlanner.expectedValue` already added the viewer's own-vote term only for the ballot it casts; unchanged.
  - **Deferred:** the abstain choice and the answering last voter on other agendas (tried: the speaker best-responding everywhere broke `castsNoMoreThanItNeedsOnAnUncontestedFirstAgenda` and several rider and tie tests, so it needs its own A/B); Galactic Threat and Sanction as voter-only penalties in the abstain price (the Galactic Threat voter cost stays as the user set it); pricing a For vote on Mutiny or Unconventional by the chance it loses (a seat still prices each outcome as if it wins, plus the Mutiny join shift); no self-play traces re-run yet.

### 4c. Opponents keep a vote back (S)
- In `remainingVotes` (253), on a first agenda while `AgendaRound.keepsAVoteBack` holds (`src/main/java/ti4/ai/agenda/AgendaRound.java:19-21`), cap each human seat at its snapshot minus its smallest planet.
- **Status (2026-11-12): done behind `AGENDA_VALUE_TABLE`** for every seat (not only humans): its smallest readied planet is kept back on a first agenda. `BallotPlanner` also prices a spent vote on a non-final agenda at 0.1 TG and casts the fewest votes within 0.3 TG of its best ballot.

### 4d. Argent Zeal (S)
- `AgendaVoting.spendPlanets` (`src/main/java/ti4/ai/agenda/AgendaVoting.java:171-201`) presses `exhaustForVotes_zeal_N` and counts those votes in the plan.
- For Argent on the first agenda, raise the per-vote reserve cost so it votes light and spends on the second agenda.
- Test: the Argent AI casts Zeal.

### 4e. Blood Pact and the Xxcha hero (S)
- In `AgendaVoting`, cast Blood Pact (Empyrean note) when both seats vote the same outcome.
- Cast the Xxcha hero's votes in the casting window when they are decisive. Today only `Pooling.java:12` reads the hero, and only for scoring.

### 4f. Cost control
- Utilities are cached per Frame. Hypothetical forecasts recompute only what changed.
- Track ms/tick against the self-play CPU profile.

**Risk:** the 4b split touches every consumer of `forecast.utility`: `AgendaCardRules` gains, `GalacticThreatRules`, `CardScoring.imperialRiderChance`, `AgendaPolicy.expectedLoss`, `importantFor`, and the audit branch's `VoteDeals` and `AgendaSwing`. Re-run all of `ti4.ai.agenda.*Test` and `ti4.ai.actioncards.*Test`.

### 4g. Review fixes and open findings (2026-11-12, self-play traces)
- **Done:** Representative Government keeps no vote back in the forecast (each seat has its 1 vote). Predictive Intelligence is a ballot option, planned by the AI (behind `AGENDA_VALUE_TABLE`): it is cast only when its 3 votes are worth more than 0.5 TG times the chance its outcome loses (the card stays exhausted), its votes count in the ballot on every agenda, and a promise for fewer than 3 votes never adds them. `fewestNearBest` never drops below the floor the best ballot had to clear. The speaker's own Public Execution counts the speaker token it hands on (`SpeakerTokenValue.ofHolding`; it was 0 for the speaker).
- **Done (2026-11-12, behind `AGENDA_HELD_BALLOTS`): held ballots.** Why the Mutiny table was 93-100% sure: `simulate` had every later seat cast its whole stack (88% For each at a 6 TG spread), while real planners cast 1-3 votes, and it valued every rival at the reveal state, so it never saw Against getting better for the speaker as For voters with points piled up. Now, on a non-final agenda, each simulated seat casts the fewest votes that hold its outcome against the votes still uncast by later seats on other outcomes (a tie counts when the speaker prefers it), its whole stack when it cannot hold alone but can still win, and nothing when it is hopeless or already holds (at least 1 on own-vote agendas, to be counted). Hypothetical ballots (`withVotes`, promises) are placed at the seat's turn, so earlier seats do not answer them. On Mutiny each For vote shifts later seats' utilities (`DirectiveValues.mutinyForVoterJoins`) before they pick. Final agendas keep full stacks (the planner goes all in). Replayed, the trace table casts For 5, 1, 5 before Letnev and Sol, so For wins (`AgendaExamTest.VoteWhatTheForecastAssumed`, `MutinyForecastTest`).
- **Done (2026-11-12, trace review, unflagged bugs): Predictive Intelligence never votes alone.** The card adds 3 votes to votes cast, so a ballot needs at least 1 planet vote. The Mutiny trace's "Hacan 3" was the old path (card pressed first, its 3 covered the planned 1 vote, no planet). Now the planner has no card-only option, deals never name 3 on the card alone, a promise uses the card only above 3 votes, the card is pressed only when a planet vote is spent or can be, and a seat that used it exhausts at least one planet. The other PI ballots (15, 10, 8) were planets plus 3 and legal. The engine still accepts a card-only ballot from a human (deferred: it is a human-flow change in `AgendaHelper`).
- **Done (2026-11-12): "no ballot decision heard" was a tracer gap.** The engine skips a seat whose `getVoteTotal` is 0 and never asks it: Sol had spent every planet on Mutiny before Unconventional Measures, and Hacan had placed a Leadership Rider on Shared Research. The trace now says "skipped by the engine" with the reason. Nekro voting in aiselfplay163336 (rounds 2-3) was legal: the engine gave it a turn, so it held Elder Qanoj (Xxcha commander through the alliance), which lifts Galactic Threat.
- **Done (2026-11-12): voting order in the trace and the forecast.** Each AGENDA TRACE block has a VOTING ORDER line: every seat in the engine's order with its vote count at reveal, its ballot (PI marked), the tally and who broke a tie. `AgendaForecast.votingOrder` now uses the engine's `AgendaHelper.getVotingOrder` (Argent first, reversed speaker order, Hack Election), not just speaker rotation.
- **Done (4b, 2026-11-12): own-vote rivals.** Seats weigh abstaining against joining at their turn, the speaker answers the tally and breaks ties on abstained values (see 4b). Still open: a For voter on Mutiny does not price the point it loses if Against wins.
- **Open: kept-vote cost for vote-rich seats.** `NEXT_AGENDA_VOTE` (0.1 TG a vote) on top of the reserve planet makes an 18-vote seat pay 1.8 TG to vote at all. Charge only for votes below what the next agenda plausibly needs, or a flat cost for spending the reserve. Measure with the A/B harness before changing (Classified Document Leaks: Xxcha preferred lsc by 4.5 TG and abstained).

---

## Milestone 5: rider policy and weighing other players' riders (M)

### 5a. Typed rider effects in `AgendaForecast` (`readPlaced` / `utilities`, 215-304)
A `RiderEffect` has three parts:
- **Owner payoff.**
- **Voter penalty.** Sanction: voters on that outcome pay one fleet token. This needs a new `CommandTokenValue.fleet(game, seat, n)`; the class has only `tactic(...)` and `lockLoss(...)` (`src/main/java/ti4/ai/strategy/CommandTokenValue.java:13,17`). It is cheaper when the voter has spare fleet tokens. Sanction has no owner payoff, so it stops being read as a positive rider.
- **Rival cost:**
  - **Politics Rider:** each seat's `SpeakerTokenValue` change when the token moves. Seats that move up gain and the rest lose, which is how the rider "recruits votes".
  - **Galactic Threat:** unchanged from today as decided: -1 TG, or -0.5 TG once others voted that way (done). A tech-scaled cost tried on 2026-11-12 was reverted: the user's flat rule stands, see the Galactic Threat note below.
  - **Other riders:** the rival-gain term `rivalStake × payoff / TG per point`.

With these, a rider on a disliked outcome dissuades others, and the poison-pill Imperial Rider falls out of `withRider` with no new rule.

**Resolution order:**
- On Economic Equality, a Trade Rider's TG are kept.
- An Imperial Rider pays before the Dictate Policy and Drive the Debate checks, so at 9 VP an Imperial Rider is valued as a win ahead of rival secrets.

### 5b. Rider payoffs (`src/main/java/ti4/ai/agenda/RiderPayoff.java:17-49`)
- Warfare: dreadnought value. Construction: space dock value. Diplomacy: Diplomacy primary value.
- Keleres Rider promissory note: 1 action card + 2 TG.
- An opponent's Technology or Leadership Rider is valued from public data instead of the 2-card default.

### 5c. Rider play (`src/main/java/ti4/ai/actioncards/AgendaCardRules.java:138-246`)
- Add Warfare, Construction and Diplomacy to `RIDER_TITLES` (28-33), and register them in `AiActionCards` (`src/main/java/ti4/ai/actioncards/AiActionCards.java:81-86`).
- With a single outcome, always play: skip the `keepPrior` gate at line 177.
- **Keep the with-ballot baseline** in `bestRider` (173-178). `withRider` already zeroes the owner's votes unless the Xxcha commander keeps them (`AgendaForecast.java:134-142`), so rule (1), "likely without your votes", already holds, and the baseline correctly prices the votes given up.
- Test for rule (1): a rider is skipped when the outcome's chance under `withRider` is low.
- The hedge rule (2) comes free once 5a exists. Pin it with a test.
- Respect `PromisedVotes.forbidsRider` from the audit branch.
- Keleres Rider promissory note: in `AgendaNotes` (`src/main/java/ti4/ai/promissory/AgendaNotes.java`), play a foreign `queueAfter_pn_` whose id contains "rider", official ids only, through the same `rider_` placement.

### 5d. Payout follow-ups
- Warfare, Construction and Diplomacy payouts in `RiderPayouts` (`src/main/java/ti4/ai/agenda/RiderPayouts.java`).
- Add their button prefixes to `PUBLIC_WINDOW_PREFIXES`.

### 5e. Wait and see
- Check the engine's queue semantics first. After each after resolves, declines are wiped and everyone is asked again (`src/main/java/ti4/helpers/AgendaWhensAftersHelper.java:757-790`), so the chicken loop already exists.
- Make sure `declineAfters` (`NekroBrain.java:465-472`) re-decides on a re-ask, with the visible riders now in the forecast, instead of short-circuiting on the stored decline. No holding.

### Tests
In `AgendaForecastTest` and `AgendaCardRulesTest`:
- A rival Politics Rider lowers the utility of the seat that loses pick order.
- Indifferent seats abstain on a Sanction outcome, and the AI won't vote it.
- An Imperial poison pill flips a 2-outcome agenda.
- A single-outcome Elect Player always gets a rider.
- A Keleres rider note is placed.
- A Trade Rider survives Economic Equality.

**Risk:** fixing Sanction changes every seat's voting, so take a new self-play baseline.

---

## Milestone 6: legal agenda-deck knowledge (M)

- **New `src/main/java/ti4/ai/agenda/AgendaKnowledge.java`**, stored in seat memory (`context.memory()`, as `AgendaClock` does):
  - It is written in the same tick the AI presses a peek: Politics `topAgenda_` / `bottomAgenda_` (`PlannedPrimaries.placeAgenda`, `src/main/java/ti4/ai/strategy/PlannedPrimaries.java:147`), Insider Information, the Naalu commander, and the hidden Covert card for the speaker.
  - It holds an ordered list of known ids plus a fingerprint (deck size, discards, round, `agendaCount`), and is invalidated on a mismatch.
  - It is the only `ti4.ai` class that may call `game.lookAtTopAgenda`, `lookAtBottomAgenda` or `getSentAgendas` (PlannedPrimaries excepted).
  - **Guard test:** `AgendaDeckReadsTest` scans the source and fails if any other `ti4.ai` file calls them.
- **Covert speaker:** value the real hidden id through `AgendaValues.of(hiddenAlias, ...)`. It votes for what it wants the hidden card to resolve to, and gives no hints. Other seats' models of the speaker keep the prior.
- **Committee Formation +2** when a good Elect Player agenda is known to be coming.
- **Kept vote:** a new `SecondAgendaValue.marginalVote` replaces the flat `KEPT_VOTE` 0.05 (`BallotPlanner.java:16`). It uses the known next agenda if there is one, otherwise the deck-composition average.
- **`AgendaPeek` rewrite** (`src/main/java/ti4/ai/agenda/AgendaPeek.java`, marked WIP): keep a card on top when its expected `AgendaUtility` beats the deck average.
  - The legacy `AgendaPolicy` GOOD/BAD sets (38-72) stay: `TableCards` (`src/main/java/ti4/ai/actioncards/TableCards.java:164-174`) and `AgendaUtility.of` (19) still use them. Migrating `TableCards` to `AgendaValues`-based judgements is an optional follow-up.
- **Insider Information:** a new `AgendaCards` card in the after window, played on the first agenda only.
- **Maw of Worlds** (`src/main/java/ti4/ai/explore/MawOfWorldsRules.java:41-44`): raise the vote cost when Mutiny hasn't been discarded or a known important agenda is next.
- **Tests:**
  - Each knowledge source has a positive test.
  - A non-holder's values don't change when the deck top is swapped.
  - Fingerprint invalidation.
- **Risk:** `Determinizer.shuffleHiddenDecks` (`src/test/java/ti4/ai/runtime/Determinizer.java:73`) must keep known positions and the Covert top card fixed for rollouts.

---

## Milestone 7: the AI's own hidden and extra votes (M)

Built on `AgendaSwing` and `ShenaniganCards` from the audit branch. First check what they already cover (Bribery, for one), and extend them rather than adding parallel rules.
- **Predictive Intelligence** (`AgendaVoting.java:177-193` before integration): press it only when `withVotes(+3)` materially changes the outcome's chance and the gain beats keeping the card ready for token redistribution.
- **Distinguished Councilor:** press `distinguished_` (`AgendaHelper.java:769`) after casting, when +5 is decisive by more than the card's keep value.
- **Bribery:** when holding it, spend the smallest TG that flips or secures the outcome, instead of `ShenanigansRules`'s automatic "No Bribery" (`src/main/java/ti4/ai/agenda/ShenanigansRules.java:20-42`). `BallotPlanner` casts at least 1 vote while holding Bribery.
- **Opponent hidden votes, as a margin buffer only:** don't trust a 1-3 vote lead while rivals hold unknown action cards (from `getAcCount`, never their hands). No per-sample card priors.
- **Rule order:** every new play runs before `ShenanigansRules.react`, or the AI refuses first.
- **Tests:**
  - PI is not pressed on a landslide and is pressed when decisive.
  - Bribery spends the minimum.
  - A narrow lead with unknown rival cards is not treated as safe.

---

## Milestone 8: card targeting (M each, ships piecemeal)

All of these go through `AgendaForecast` (`without`, `withRider`, a forced outcome) against the card's keep value. Where `AgendaSwing`, `EarlyDeadlyPlot` or `ShenaniganCards` already plays the card, re-tune that code instead of adding a new AiCard.
- **Veto:** the self-interest threshold, re-tuned after milestone 1. Payment comes in milestone 11.
- **Quash** (`src/main/java/ti4/ai/xxcha/QuashRules.java:35-43`): cheaper when the Xxcha hero economy applies. Payment comes in milestone 11.
- **Deadly Plot** (extend `EarlyDeadlyPlot` / `AgendaSwing`):
  - Eligibility: the AI voted for, or predicted, another outcome.
  - Play it when the winning outcome's loss exceeds the keep value plus the cost of exhausting all planets. That cost is much higher on the first agenda.
  - While Deadly Plot is held, lower the vote-commitment reserve in `BallotPlanner` (it "lets you vote loosely").
- **Confounding Legal Text** (extend `AgendaSwing`): play it when electing yourself is worth more than the card, for example to steal Censure or the Arbiter.
- **Confusing Legal Text:** play it when you were elected to a punishing agenda, retargeting to your best rival.
- **Assassinate Representative:**
  - In the after window, target `argmax(utility(without(rival)))`. It is strongest on Mutiny.
  - Skip Xxcha seats with the commander or Alliance, since they can still vote.
  - Prefer to wait until the riders are down, through re-ask (5e).
- **Hack Election and Ancient Burial Sites:** low-value dumps. Burial Sites goes at the start-of-agenda window (`StartPhaseService.java:908`).
- **Diplomatic Pressure:** a when-window play aimed at the seat holding the most valuable foreign note (`NoteGiving.giveRank`).
- **Political Secret** (`AgendaNotes.java:65-70`):
  - Use `forecast.without(owner)` on every agenda type, not only Elect Player.
  - Skip owners with the Xxcha commander or Alliance.
  - Value only active abilities blocked. Passives such as Letnev's fleet bonus still work.
  - On the giving side, the AI keeps Political Secret back, and Xxcha keeps Political Favor back.
- **Committee Formation use:** a new `CommitteeFormationRules` that runs before players vote, using `presetCommitteeFormation` (`AgendaHelper.java:1676-1700`; add the prefix if it is unowned).
  - Use it when the best election plus rider gains beat keeping the law, coordinated with an Imperial Rider on itself (predict yourself, then elect yourself).
  - In the forecast, a rival holder's Elect Player agendas collapse toward that holder's best outcome.
- **Galactic Threat:** Nekro skips the prediction when its best chance is low (`GalacticThreatRules.java:81-112`).
- **Sabotage of agenda cards** (`SabotagePolicy.java:79-92`): value a rival's Veto or rider by the forecast delta, not the baseline.
- **Tests:** a prompt-fixture test per card in the `VetoCardTest` style, and the rule order in `NekroBrainTest`.

---

## Milestone 9: Mutiny, Unconventional Measures, Seed of an Empire, Miscount Disclosed (S-M, simple first)

- **Seed of an Empire:** a default lean toward Against, since Seed usually lets the players in last gain points. Switch to the AI's own side only when that side can form a majority block, where a block is the seats tied at the lowest or highest point totals and their votes, counted from the snapshot.
- **Mutiny:** abstain when later or stronger voters (the speaker last) could punish. Otherwise, if the AI can't win, go For silently and hope.
- **Unconventional Measures:** the same rule with lower stakes.
- **Miscount Disclosed:** count the votes left. When an outcome is winnable, place a few votes on it to steer others. Bribes join once milestone 10 exists.
- **Escalate only if needed:** add correlated block sampling in `simulate`, and an explicit `forVoters` overload for bluff propagation, only if `AgendaExamTest` or the tracker shows the simple version failing.
- **Tests:**
  - The AI with few votes and stronger punishing voters after it abstains or votes Against on Mutiny.
  - A 4,4,5,5,6,7 Seed table leans Against for a mid-table AI.
  - A low-block AI with a majority votes its own side.

Table talk and Support for the Throne swaps come in milestone 12.

---

## Milestone 10: vote selling and buying (M, one simple loop on the audit branch's classes)

- **Start from what exists:** `VoteDeals` (outlook, bestSale, bestPurchase), `PromisedVotes` (binding, forbidsRider, verdict), and `Promise` / `PromiseBook` / `PromiseChecks` / `PromiseDeals` under the `PROMISE_DEALS` experiment. Audit what they already do, then fill gaps there. No new `AgendaPromises`, `VoteValue`, `Trust` event or `OfferResponder` vote path.
- **Simple loop (AI-to-AI first):**
  - **Buying:** when the AI cares, it offers 1-2 TG per vote to seats that don't care and haven't voted, until chance(wanted) is about 0.8 or the marginal gain falls below the price.
  - **Selling:** when it doesn't care, it sells its own votes at a reserve (its utility gap plus the value of its kept vote).
  - Pay up front.
- **Price:** `clamp(reserve + 1, 1, gain × share)`, under a per-agenda cap (your caps: Veto 1-3 TG, Quash 3-5 TG).
- **Transaction rule (LRR 94.6, `docs/rules/reference/transactions.md:17`):** one transaction with each other player per agenda. A vote payment is that pair's single transaction for the agenda. An up-front payment and an after-vote payment are exclusive, and the payment must not be bundled with any other deal with that seat in the same agenda. Keep `TradeLegality.window` (`TradeLegality.java:95-107`) as the guard, with no windowless purpose. If after-vote settlement is used for humans (through the button deal card), it is the only leg, or it moves to the next legal window.
- **Waiting:** reuse whatever hold the audit branch's deals use. If there isn't one, add a bounded wait (about 3 min with AIs, 10 min with humans) with a hard give-up, and test that agendas never stall (cf. the earlier 24 h Veto stall).
- **Deferred until A/B shows the simple loop helps:** avoidance bribes on Public Execution and Colonial Redistribution, vote extortion, and Hacan commander pricing.
- **Tests:** in the `PromiseDealsTest` / `PromisedVotesTest` style:
  - The buyer picks the cheapest sufficient seller.
  - The seller accepts only at or above its reserve.
  - The promised ballot overrides the planner.
  - One pair never makes two transactions in one agenda.
  - A hold times out cleanly.
- **Risk:** humans can't be read (`OfferResponder` strips human `DETAILS`, `Stinginess.java:146-151`), and promises aren't binding. So prices are capped, and human deals go through an explicit button deal card with a trust ledger.

### Review fixes and open findings (2026-11-12, self-play traces)
- **Done:**
  - A promise is valued at the votes it names (`VoteDeals.valuedAt`), not re-sized by whoever decides.
  - Deal sizes are totals the seller's readied planets can make (plus Predictive Intelligence's 3), so a promise never casts more than it names.
  - Deal sizing and the spent-vote price use the deciding seat's (the viewer's) experiment flags.
  - When the buyer values a deal, the seller's cost leaves out the Galactic Threat dislike that the buyer's forecast already put in the seller's utilities, and adds the flat cost once.
  - A seat that has started its ballot (stored `agendaBallot|`) refuses vote offers for another outcome, and a seat that promised or offered its votes buys and sells only for that outcome (`PromiseDeals.backedOutcome`). This was how Letnev and Hacan broke promises they accepted mid-ballot.
- **Open: vote-market pricing.** Prices are set at the buyer's gain less its margin (`PromiseDeals.sellerPrice`), and buyers accept the first ask, so Nekro paid 3-5 TG for a Galactic Threat voter where 1-2 TG is the table rate. Price at `max(voteFloor(sellerCost), 1)` plus a small premium, capped by the buyer's gain. When several sellers give the same marginal value, offer the minimum to the cheapest first, and cap Nekro's bounty at 1-2 TG.
- **Open: selling against your own preference.** Add a floor when the seller's utility for the target is far below its best outcome (for example over 2 TG): require a price of the stake times a decisiveness margin, or refuse. The forecast can call 5 votes non-decisive and be wrong. Hacan sold its votes for '8' on Galactic Crisis Pact at 3 TG while valuing it 9 TG below its favourite. Predictive Intelligence then added 3 more, which the PI fix above now stops.
- **Open: blocs and counter-bids.** `sellingVotes` locks out a rival buyer once another buyer has an open offer on a seller, and offers are valued one at a time, so seats never build a bloc. Allow counter-bids on open (not accepted) offers. Value a bundle together: rank sellers by votes per TG and add them until the outcome flips, then take it if the total gain beats the total price. Seen on Public Execution (Sol declined 8+9+7 votes, which together with its own 6 beat Xxcha's 18) and Colonial Redistribution (Sol stood to lose 21.8 TG and never bought).
- **Open: declined decisive blocs (Minister of Antiquities).** Hacan declined 14-vote blocs at 3-4 TG that would have won it the relic. The likeliest cause was re-sizing the promise below 14 votes in `protection`, which `valuedAt` now fixes. If it recurs, log `buyerGain`, trust and margin for each declined `VOTE_FOR` offer, and check `ScoringStakes.contender`.
- **Done (2026-11-12, user's notes after the traced self-play):**
  - **Vote prices by agenda** (behind `AGENDA_VALUE_TABLE`): on the last agenda a deal names the seller's whole castable stack (unspent votes vanish, and the spent-vote price is 0 there); only first-agenda deals are sized to the fewest votes near the best surplus.
  - **Deals at the seller's turn** (behind the new `CONDITIONAL_VOTE_DEALS`, default on): a buyer offers only to a seat about to vote (`VoteDeals.aboutToVote`: the active voter or the next one in `agendaVoting`, the first voter in `agendawaiting`), and a seller offers its own votes only at its turn. Price is set then. A ballot planned smaller than an accepted promise for the same outcome is rebuilt (`AgendaVoting.keepingPromise`).
  - **Pay on resolution** (same flag, decided by the AI proposer): accepting a vote deal moves nothing. Once the promiser voted the target (`KEPT`) and `resolvedAgendaOutcome` for that agenda is the target, the promise becomes `OWED`; the AI payer posts a bill with a pay button it owns (or the AI payee does, for a human payer) and presses it (`aiPromisePay_`), which pays and marks it `PAID`. A lost outcome owes nothing. It is the leg of the transaction struck on accepting, so it uses no new window (LRR 94.6). Prices and accept/decline value the deal as conditional: the seller expects `chance × price` (times its trust in a human buyer), the buyer pays `chance × price`, where chance is the outcome's chance with the votes; no conditional deal below a 20% chance.
  - **Simplest bloc** (same flag): when no single seller is decisive, the buyer values the seat about to vote together with up to three later sellers (cheapest per vote first, whole stacks) and offers the first its share if the joint gain beats the summed floor prices weighted by the joint chance. Later members are approached at their own turns; since payment is conditional, a bloc that falls apart costs the buyer nothing. Up to 4 vote offers per agenda under the flag (2 without).
- **Open:** a human payer who never presses the bill is not yet recorded as a broken promise; a bill not settled before the agenda phase ends is dropped (no action-phase settlement). Counter-bids on open offers, and the open pricing items above, are unchanged.

---

## Milestone 11: paying for Veto, Quash and Deadly Plot (S-M)

- **One offer each way, built on the promise layer:**
  - **Holder:** an AI holding Veto or Deadly Plot (or Xxcha with a strategy token for Quash) whose table pain is high announces once: "I hold Veto, n TG and I play it". Table pain is the sum of each seat's `max(0, -expectedUtility)`. Payments received lower its own `veryBadFor` / `worthQuashing` / Deadly Plot threshold.
  - **Payer:** an AI losing more than about 3 TG pays an announced holder, or posts "I pay X to whoever vetoes".
- Payment counts as that pair's single transaction for the agenda (LRR 94.6).
- A holder that took payment and didn't play is tracked through the existing `PromiseBook` / Trust.
- **Tests:**
  - The holder announces and waits, the payer pays, the holder plays.
  - On timeout, the agenda ends in a normal pass.
- **Done (2026-11-12, behind the new `DISCARD_BOUNTIES`, default on): pay to kill a doomed loss.** In the when window, an AI seat whose expected loss with its own best ballot is 2 TG or more posts once per agenda: up to `floor(loss - 0.5)` TG to whoever plays Veto (none if it holds Veto itself), and to Xxcha for a Quash when Xxcha can Quash. Offers pool across payers within the caps (Veto 3, Quash 5); a later payer offers only what the cap leaves (`DiscardBounties`, stored value `aiDiscardBounties`). The holder adds the pooled bounty (from others) to its signed loss: Veto when `bounty - EU >= very-bad threshold` (`AgendaPolicy.worthDiscardingFor`), Quash when `loss + bounty > strategy token + margin`. A Veto holder or Xxcha waits up to 90 s in the when window while an AI seat in pain has TG and has not posted yet. After the agenda is replaced, the payer finds a non-canceled Veto play since its offer (or Xxcha's strategy tokens below what they were) and records an `OWED` `DISCARD_AGENDA` promise to that holder, paid through the same bill and pay button (it uses that pair's window). If voting starts on the same agenda, the bounty lapses.
- **Open:** Deadly Plot bounties; holders announcing "I hold Veto, n TG and I play it"; a holder that takes a bounty is not tracked (it is paid only after playing, so there is nothing to break).

---

## Milestone 12: coordinated multi-party plans (L, deferred; AI-only first)

Start only after milestones 10-11 show gains in A/B. Each item is built on the `TradeTerms` / `TradeCardRules` announced-shape pattern (`src/main/java/ti4/ai/trade/TradeTerms.java:20-40`), `SpeakerPromise` and the promise layer.
- **Speaker free-Against announcement:** "if nobody votes I pick Against". Non-speaker AIs that read it raise the value of abstaining. No payment.
- **Boat float on Articles of War:**
  - Trigger: the table's mech stake is small (MechRating).
  - Plan: For voters cast exactly 1 vote, the designated Against seat casts enough to tie, and the speaker's `breakAgendaTie` (`NekroBrain.java:486-510`) honours the plan. Each For voter settles 1 TG to the Against seat, as that pair's one transaction.
  - `BallotPlanner` needs to accept a forced 1-vote option.
- **Seed of an Empire Support swaps and lending:** relax `ThroneSale.allowed` (`src/main/java/ti4/ai/trade/ThroneSale.java:28-36`), with win-race guards.
- **Keleres rider split:** a note for TG after the payout, or splitting the 2 TG.
- **Mutiny announcements:** not for now (no bluffing on Mutiny).
- **Tests:** end-to-end TG nets for the boat float on a 6-seat fixture, a single defector, and the speaker honouring the plan.
- **Started (2026-11-12):** the simplest AI-to-AI vote bloc is in milestone 10 (joint valuation, one offer at a time at each seller's turn, conditional payment). The boat float, Support swaps and Keleres split are still open.

---

## Milestone 13: the use side of elected laws (S-M, any time after milestone 1)

- **Imperial Arbiter:** hold it until `GameHorizon.roundsLeft <= 1`, then swap a poor last-round strategy card (4, 5 or 7) to a rival. The engine's Arbiter swap flow hasn't been mapped yet. Find the button family first, and add its prefix if it is unowned.
- **Prophecy of Ixth:** when holding the law, add the extra fighter roll to fighter value in the production planners (`getLaws().containsKey("prophecy")` together with the holder).
- **Research Grant Reallocation:** when elected, prefer techs with no prerequisites, since each prerequisite costs a token. Weigh the tokens lost through `CommandTokenValue`.

---

## Your answers (2026-11-11)

- **Deals with humans:** an explicit button deal card, like the Hacan agent deal. The AI assumes humans follow through. If a human ever breaks a deal, every AI at the table records it and never trusts that player again (a per-game trust ledger).
- **Price caps:** 1-3 TG to pay for a Veto, 3-5 TG for a Quash. Several players often pool the cost, so milestone 11 needs a multi-party deal that collects TG from several seats for one payment.
- **Integration:** done. This branch is based on the audit branch, with the Nekro branch merged in.
- **Anti-Intellectual Revolution:** uses the true rounds left (implemented).
- **Bluffing and betrayal on Mutiny and Unconventional Measures:** not for now. The AI stays honest and keeps its deals.
- **Table talk:** allowed ("I hold Veto", boat float proposals, ...). Answers are collected with buttons and the reaction system.
- **Galactic Threat:** voting for the outcome Nekro predicted costs the voter 1 TG of disincentive, or 0.5 TG once other (non-Nekro) players have already voted that way (user's decided rule, implemented in `GalacticThreatCost.ofBallot` behind `GALACTIC_THREAT_COST`; a tech-scaled cost from 2026-11-12 was reverted). Vote deals and promise deals charge it to the seller. Nekro's payoff side is the technology it would actually copy: the forecast's `threatWorth` is the best technology of the non-Nekro voters on the winning outcome, so a speaker pick with no votes is worth 0, and Nekro picks the prediction with the highest `threatWorth`. A new mechanism for milestones 5 and 10: any player may offer TG to players who vote the way their rider points, paid if it resolves. Nekro offering 1-2 TG is one case.
- **Mech priors:** accepted as work-in-progress values to verify.

### Rules corrections
- **Speaker tie-break** (no votes, or tied votes) is not voting, for any effect that refers to players who voted. Milestone 4b relies on this.
- **Argent Zeal:** Argent votes first and also gets extra votes, one per player in the game. Milestone 4d must count them.
- **Conspirators** is a Discordant Stars ability (homebrew). It is dropped from milestone 4a.
- **Xxcha hero:** changed in Thunder's Edge. The combined-votes effect is now a nerfed version on Xxcha's breakthrough. Milestone 4e values it from the current card data.
- **Predictive Intelligence** is public, so rivals' forecasts count its +3 votes. It stays exhausted if the vote goes the other way, so milestone 7 keeps the decisive-only rule.

### Still open
1. **End-game scale-up of point elections.** Ships as flat values plus the `winsWithPoint` guard (now also on Shard, Political Censure and Holy Planet).
2. **Public Execution.** Implemented as a share of `SpeakerTokenValue` for moving up one pick, plus 1.5 TG for Yssaril or Ral Nel as targets.
3. **Arms Reduction anger term.** Skipped for now.
4. **Incentive Program stage I / stage II stats.** Deferred; flat 1.5 TG stage II faction bonus.
5. **Homeland Defense.** For = 0; PDS holders prefer For because Against hurts them.
6. **Magnitudes the notes only gave a direction for** (Travel Ban wormholes, Bastion, Cabal/L1Z1X, Wormhole Reconstruction plastic, Checks and Balances speaker lean, Mallice). Picked by the implementer; see `ExpertLawValues`.

---

## Measurement: how to tell agenda play improved

1. **Unit and exam tests (every change).**
   - `AgendaValuesTest` holds your number for each agenda. `AgendaExamTest` asserts the expert answer per guidance bullet in a realistic position. The leak tests (viewer seam, Covert, deck reads) guard hidden information.
   - They run with `mvn -Dtest='ti4.ai.agenda.*Test,ti4.ai.actioncards.*Test' test` (mvn, no mvnw).
   - Building in vigorous-cerf rewrites the dev bot's `target/classes`, so restart the dev bot afterwards.
2. **Agenda-local self-play metric (milestone 1's flag and every forecast or behaviour milestone).** On the `playagenda` harness (about 4 agendas a game at 2 rounds, about 6 at 3), `AgendaTracker` reports per seat:
   - realized agenda utility: the `AgendaUtility` of the winner at reveal minus the mean over outcomes, in TG;
   - rider net value;
   - agenda-sourced VP;
   - harness defects: referee-broken ties, and waits over budget.
3. **Mixed-brain A/B.** Each milestone sits behind an `AiExperiments` flag and runs with `-Dai.selfplay.ab=<flag>` from a frozen worktree (never recompile under a running batch).
   - Run an A/A first to calibrate the noise of the per-agenda metric.
   - Primary: treatment minus control realized agenda utility per agenda, with SE over pairs (`AbSummary`), confirmed on 2+ seeds.
   - Guard: final VP and win share on full games, reporting the average final round, leader VP and table total.
4. **Single-decision branching.** For rider placement, Politics placement, Covert and Committee, branch from an agenda-reveal snapshot (`EnvironmentSnapshotTest` / `RolloutExperimentTest`). Use common random numbers and a `Determinizer` that keeps known agenda positions fixed.
5. **Human comparison.** The `SelfPlayEnvironment` eventsdir recorder logs AI ballots against human games for review by hand.
6. **CPU budget.** Track ms/tick in agenda phases. The forecast reruns 256 samples per hypothetical, so caching is required.
7. **Base-only agendas** (Elect Planet cards, Shard, Crowns, Rep Govt) never come up in PoK self-play, so only unit tests cover them.
8. **Optional testbed check.** A dev-bot testbed run would help for the Discord flows: Zeal, Blood Pact, Committee, Bribery, Deadly Plot, Distinguished Councilor and human vote deals. This is a suggestion only; no testbed scripts get written unless you ask.

## Sizes at a glance
| Milestone | Size |
| --- | --- |
| 0 Branch integration | S-M |
| 2 Measurement harness (done before 1's flag is on) | M |
| 1 Values and point-law bug (flagged) | M (MechRating M) |
| 3 Dictate Policy and Covert prior | S |
| 4 Forecast and ballot correctness | M-L |
| 5 Riders | M |
| 6 Agenda-deck knowledge | M |
| 7 Own hidden and extra votes | M |
| 8 Card targeting | M per card group |
| 9 Mutiny, Unconventional, Seed, Miscount | S-M |
| 10 Vote selling and buying (simple loop) | M |
| 11 Paying for Veto, Quash and Deadly Plot | S-M |
| 12 Coordinated plans (deferred) | L |
| 13 Arbiter, Prophecy and Research Grant use side | S-M |

Milestone 1a, milestone 3 and milestone 13 can ship on unit tests alone. Milestone 1's value table ships behind its flag after the milestone 2 baseline. Milestone 4 lands before milestones 5 through 12.