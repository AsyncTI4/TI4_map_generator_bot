# Outdated statements in AI_PLAYER.md and the old PR description

Part of the [AI player architecture docs](/src/main/java/ti4/ai/README.md). Statements found in [AI_PLAYER.md](/AI_PLAYER.md) or the earlier PR description that the code contradicts or no longer supports, as of the review on 2026-11-17.

Each item names a statement in [AI_PLAYER.md](/AI_PLAYER.md) or in the earlier description of this PR, and what the code
does instead.

### Core runtime, brain, perception and actuation

1. **Decision variants.** AI_PLAYER.md ("Decision. The rules return `Press`, `Unsure`, `Wait` or `Idle`") and the PR
   body diagram (`Press / Unsure / Wait / Idle`) omit `Announce`, `Propose` and `Post`, which `AiDecision` defines and
   `AiTickRunner.runSeat` handles.
2. **Abort storm.** AI_PLAYER.md: "Three such aborts in 10 minutes log an abort storm error." The code logs the error
   when **every active AI seat** has 3 consecutive aborts (`AiLane.everySeatAborting`, `ABORT_STORM_LIMIT = 3`), once
   until some seat decides normally; there is no 10-minute window (10 minutes, `RECENT_ABORT`, only decides whether a
   "stuck" record names the last abort). `AiTickRunnerThinkingTest.anAbortStormAcrossEverySeatIsLoggedOnceAsAnError`
   checks the code's behaviour.
3. **Paused seats.** AI_PLAYER.md's command table: "While paused, an AI acts only through the table: blocking choices are
   posted for any player to make." `handleIdle` makes a paused seat hand nothing over unless a delegation request
   (`/ai delegate`) is pending (`AiTickRunnerPausedTest.aPausedSeatHandsNothingToTheTableHoweverLongTheGameHasWaited`).
4. **Who may choose for the AI.** AI_PLAYER.md ("The first player to press one makes that seat press …", "blocking
   choices are posted for any player"), and the `/ai delegate` reply text ("posted for any player to choose") say any
   player. With `EXPLOIT_GUARDS` on, which is the default, `AiConfusionService.mayChooseFor` allows only the game owner
   or a bot developer, and the delegation text says so.
5. **Fallback log storage.** AI_PLAYER.md: "the last 10 in the game stored values `aiFallbackLog|slot0` to `slot9`
   (base64) with `aiFallbackLog|next`". `FallbackLog` stores them through `AiLogStore` in a per-game `.properties` file
   under `<storage>/ai_logs/`, keys `slot0`…`slot9` and `next`; they are not game stored values, so they do not roll
   back with `/game undo` and are not in the game file. A record identical in round, faction, phase, kind, reason and
   prompt to a stored one is logged but not stored again.
6. **Kill switch.** AI_PLAYER.md: "It respects the circuit breaker, the kill switch and the lease". The kill switch
   (`AI_PLAYERS_ENABLED`) gates only the seat's own choices and the referee; with it off, stall hand-overs still post
   public prompts and chosen delegated presses are still pressed (`pressDelegated` checks `mayMutate` only).
7. **Private prompts.** AI_PLAYER.md: "Private prompts are only ever declined". In self-play, `fallBack` calls
   `chooseWithoutHumans` before the private-prompt branch, so a private prompt can get the first safe option, not only
   a declining one. In mixed games the "safe default" also matches `done`, `skip`, `cancel` and `deletebuttons`.
8. **"Acts only by pressing buttons" / "No special engine paths"** (PR body). AI code also writes game state directly
   through `AiGameWrites.persist` (hostility, attack ledger, relations, promises, deals, bounties, speaker promises,
   extortion), posts its own messages (`Announce`, `Post`, `Propose`, delegation copies, notices), and changes the AI
   seat's user settings (`AiSeatService.ensureTradePreferences`, `writeSeatPreferences`).
9. **Perception description.** AI_PLAYER.md lists own buttons, own thread, combat threads and public windows. The code
   also reads strategy card threads (owned buttons, newest message under 15 minutes), keeps buttonless AI table talk and
   the Leadership rider payout line, reads only the 2 newest combat threads per seat that involve it, and only the last
   50 (100) messages per channel.
10. **"Every decision sees fresh state."** Within one tick all seats decide on one perception snapshot; observer rules
    may save the game during any seat's decision (`AiGameWrites.persist`), so later seats in the same tick read a game
    object that changed while their prompts did not.
11. **Thinking: "The seat then idles (the stall timeout asks the table to choose)".** Accurate, with the detail that the
    seat goes straight into `handleIdle` in the same tick; the stall threshold still applies, the abort itself hands
    nothing over. The warning text it quotes, `AI thinking cut short game=… trail=[…]`, does not match the code:
    `AbortReport.line` logs `AI thinking aborted game=… path=[…] calls=[…] keys=[…] inputs=[…]`.
12. **Agenda softmax** (outside scope, noted while checking randomness). AI_PLAYER.md: "The softmax temperature of the
    forecast (4 trade goods)". `AgendaForecast.TEMPERATURE` is 3.0.
13. **GLOSSARY.md, Relations:** "every TG of value a Player has cost the seat … lowers it". `Relations.hostility` lowers
    the level by `floor(hostility / 2.0)`, one point per 2 trade goods.
14. **Loop guard.** AI_PLAYER.md: "when a seat falls back on the same unsure choice 6 times in one turn". The key is
    the fallback's reason text plus the turn (`reason@activePlayerID@lastActivePlayerChange`), so it counts every
    fallback kind with that reason (`UNSURE`, `NO_PROGRESS`, `TIMEOUT`), not only unsure choices; it is not keyed on
    the prompt.
15. **Seams** (PR body): "The only seams are `SlashCommandManager` … and `TestBedService.findNonDeveloper`" matches the
    imports found. The engine change list in the PR body omits `GameSaveService.writeSnapshot` / `GameSnapshots`
    (copies for rendering no longer set `lastModifiedDate`); its link to AI change detection is an inference.

### Seats, commands, fallback and self-play setup

1. **Fallback storage.** AI_PLAYER.md ("Fallback" bullet) says the last 10 are kept "in the game stored values
   `aiFallbackLog|slot0` to `slot9` (base64) with `aiFallbackLog|next`". Code: `FallbackLog` uses `AiLogStore` keys
   `slot0..slot9` / `next`, stored in `<storage>/ai_logs/<game>.properties`, not in the game save
   (`FallbackLogTest.recordsLiveInTheirOwnFileAndNotInTheGameSave` asserts the stored-value map stays empty). They
   survive a restart via the file, but not `/game undo`.
2. **Who may choose for a confused AI.** AI_PLAYER.md ("**any player** may pick for it", and "The first player to
   press one makes that seat press the original button"), the old PR description ("any player can pick for it"), the pinned
   help text and the `/ai delegate` reply ("posted for any player to choose") all say any player. Code: with
   `EXPLOIT_GUARDS` on by default, `AiConfusionService.mayChooseFor` allows only the game owner or a developer, and the
   delegation message says so.
3. **Paused seats.** AI_PLAYER.md (`/ai pause` row: "blocking choices are posted for any player to make") and the old PR
   body ("a paused AI posts blocking choices for any player to make"). Code: a paused seat without a delegation request
   posts nothing (`handleIdle` returns after `clearStall`); choices are handed over only after `/ai delegate`, as the
   `/ai pause` reply says.
4. **Faction targeting.** AI_PLAYER.md: "Every command except `add` takes an optional `faction_or_color`". `watch`,
   `play`, `profile` and `debug` take none.
5. **`/ai play` default faction.** AI_PLAYER.md: "default a random Base, PoK or Thunder's Edge faction". Code:
   `AiPlay.randomFaction` is uniform over the 11 `SUPPORTED_FACTIONS` only; any base/PoK/codex/TE faction is accepted
   only when named.
6. **WIP factions.** AI_PLAYER.md presents `AiSettings.WIP_FACTIONS` as the mechanism keeping them out of defaults. The
   constant has no production reader; the exclusion comes from `DEFAULT_FACTIONS`. The `/ai play` human may get a WIP
   faction at random.
7. **Hidden aggression.** The old PR description lists "Hidden aggression profile per seat" as a feature and AI_PLAYER.md
   describes the profile as holding "aggression base and mode". The values are stored but no decision code reads
   them; AI_PLAYER.md's Status table keeps "Hidden aggression levels with drift and lock" under V2, which matches.
8. **Self-play fallback.** AI_PLAYER.md: "picks a declining option (or else the first option that doesn't open a form)".
   Code also excludes `ultimateUndo…`, hit cancels and early round passes, and for TIMEOUT/REQUESTED on a system picker
   prefers a `ringTile_` holding only its own units (`stalledChoice`).
9. **Stall threshold.** AI_PLAYER.md: "If that lasts 3 minutes". At fast pace in self-play it is 1 minute
   (`AiSettings.stallThreshold`).
10. **`/ai remove` restore.** AI_PLAYER.md: "map tile; speaker and game flags once the last AI seat leaves". Code
    restores the speaker for each removed seat that holds it (not only the last), restores game flags only for the last,
    and also removes all units of the seat's color from every tile.
11. **Kill switch.** AI_PLAYER.md says the runtime "respects … the kill switch". Code: the switch stops own decisions,
    the referee and new seats/games, but the runtime keeps polling, stall hand-overs are still posted, and delegated
    choices still execute.
12. **Loop guard key.** AI_PLAYER.md: "falls back on the same unsure choice 6 times in one turn". The counter keys on
    the fallback reason and the active player's turn start, and counts NO_PROGRESS and TIMEOUT fallbacks as well as
    UNSURE ones, not the specific prompt.
13. **Old PR body command table** omits `/ai play`, `/ai profile` and `/ai debug`.
14. **Private prompts "only ever declined".** AI_PLAYER.md (Fallback section): "Private prompts are only ever
    declined, and only when a declining option exists." Code: `safeDefault` also accepts a button whose handler id or
    label contains `deletebuttons`, `done`, `skip` or `cancel` (after `decline`, `pass`, `no_`, `not follow`), so a
    private "Done" confirmation can be pressed.

Statements checked and found consistent: synthetic id `7100000` + 9 digits; name "<faction> AI"; profile in player
stored values; `/ai status` never shows aggression; `/ai fallbacks` access and detail rules; `/ai delegate` without a
seat applies only to seats waiting on something; 2 attempts per prompt and state; 1.5 s gap; 150 actions/hour; caps
per seat and game; 3 failures in 10 min per game, 10 across games; referee step list, 60 s agenda delay, 10 min tie
delay, never ending the game, pausing any seat stopping the referee; `RedistributionGuard` behaviour; self-play
seeds through `RandomHelper.current()` for seat ids and profile seeds.

### Shared valuation and technology automation

1. AI_PLAYER.md, _race for the custodians_: "and is empty under another player's space cannon". With experiment
   `CANNON_COVER_RISK` on (the default; `AiExperiments.enabled` is true unless disabled), the arrival stays and the win
   chance is multiplied by the landing's survival against the cannon (`coverSurvival`); the arrival is empty only when
   survival ≤ 0. The quoted rule applies only to a seat with the experiment disabled
   (`CustodiansCoverTest.withTheExperimentDisabledOneRivalPdsTwoStillStopsTheRace`). `CANNON_COVER_RISK` is not
   mentioned in AI_PLAYER.md.
2. AI_PLAYER.md, same paragraph: a transport hops "not at all from a system holding its own command token or once the
   seat has passed". In code such a transport is not excluded: it arrives from next round on
   (`base + max(1, hops)`), only this round's landing is ruled out.
3. AI_PLAYER.md, technologies: "Production Biomes' 4 trade goods instead of passing ... (the other 2 to the player
   furthest behind)". The recipient is `Beneficiary.leastThreatening`: never the points leader if another is offered,
   then lowest (victory points + best public objective it can score now), ties by fewest trade goods + commodities.
   The text also omits that Production Biomes is used instead of a tactical action when it beats the best plan
   (`TechRules.insteadOfTacticalAction`, `usesProductionBiomesAsTheActionWhenItsTradeGoodsUnlockAnObjective`).
4. AI_PLAYER.md, technologies: "Nullification Field when the player activating a system with its ships could bring in
   a bigger fleet". The code nullifies when `NullificationOutlook` reports scoring at stake or a worth of at least twice
   the strategy token's cost, and needs a spare token; `TechRulesTest` shows it letting a lone-destroyer attack stand
   and nullifying moves that complete an objective or hit ships holding a presence objective.
5. AI_PLAYER.md: "technologies it does not use: Transit Diodes, Chaos Mapping, Instinct Training, Quantum Datahub
   Node, ...". `StandardBrain`'s rule list includes `TransitDiodesRules::next` (`td`, `absol_td`) and `DatahubRules::next`
   (`qdn`), and `StandardBrain.takeTurn` calls `TransitDiodesRules.start`. Instinct Training (`it`) is used too:
   `ti4.ai.actioncards.InstinctTraining.available` offers it to `SabotagePolicy` as a way to cancel an action card for
   a strategy token, and `SabotageRules` records the use.
6. AI_PLAYER.md, Psychoarchaeology: matches, but omits that planets planned for an expedition
   (`ExpeditionUnlock.plannedPlanets`) are kept.
7. Old PR body: "`eval` (~1.9k)" lines. `ti4.ai.eval` is now 2,530 lines in 28 files.
8. Old PR body: "Hidden aggression profile per seat". That profile is separate from `FactionAggression`, which is a
   public, fixed per-faction factor; the two should not be conflated when describing this package.

Statements checked and consistent with the code: `FactionStrength` empty; `Territory` zones and the 1.5 / 1.0 / 0.3 /
−1.0 planet bonuses (in `TacticalPlanner`); `MecatolStake` (a quarter point for at most two later rounds, nothing in
the last); `FlagshipRating` tiers and spare-resource amounts (0 / 1.5 / 3 / 4.5 / 6 / 3.75); `FleetLimit` (fleet pool +
Mahact tokens + 2 for Armada, unlimited under Darktalon Treilla); Lazax Gate Folding one-way into Mecatol Rex in
`Adjacency.forMovement`; legendary holding values in `BoardView.planetValue`; Entropic Scar purchase rule; `CombatOdds`
described as exact odds.

### Tactical planning

1. AI_PLAYER.md (Expand): "keeping at least one ground force (a mech when there is one) on each home planet". The home
   garrison is `DefenseOutlook.groundToKeep`, which returns 0 when leaving costs ≤ 0.5 more; with nobody in reach
   nothing stays (`HomeDefenseTest.roundOneWithNobodyInReachHoldsNothingBack`). A mech is kept first only when
   something is kept.
2. AI_PLAYER.md (Defense): "ships, ground forces and fighters leave a guarded system only while the cost stays within
   half a point". `GARRISON_TOLERANCE` is 0.5 in the planner's trade-good scale, where a point is 8.0; the tolerance is
   1/16 of a point.
3. AI_PLAYER.md (Fracture): "A step is worth 0.8 of that strike when one move is left and 0.85 times less per further
   move (0.68, then 0.58)". `stageShare` for the neutral is `FractureCampaign.routeShare` (1.0 with an own ingress,
   0.5 without) × 0.85^(steps − 1): 1.0, 0.85, 0.72 (or half that). No 0.8 factor exists in `TacticalPlanner`.
4. AI_PLAYER.md (Offense gates): "Planets with enemy structures are taken only in their owner's home system, on Mecatol
   Rex, with Conquer the Weak or against a player close to winning." `StrikeRules.conquering` is computed but never
   read and `StrikeEvaluator.ground` has no structure check; only non-attack landings (`landingTargets`,
   `TacticalRules.nextLanding` without a plan) skip planets with enemy structures.
5. AI_PLAYER.md (Offense "Not yet"): "Magen Defense Grid … in the odds" and "memory of past attacks". Both exist:
   `StrikeEvaluator` adds `MagenDefenseGrid.placedInfantry` and `startOfCombatHits`, and `AttackLedger` (experiment
   `ATTACK_MEMORY`, on by default) feeds anger and fear. Likewise "L4 Disruptors" appears in that list and in the
   `StrikeEvaluator` TODO, yet `L4Disruptors.shieldsInvaders` zeroes the defender's space cannon against landings in
   `StrikeEvaluator.ground` and `DefenseOutlook.computeGround`.
6. AI_PLAYER.md (end of the tactical section): "Once it has taken an action, it only ends its turn." Second tactical
   actions exist (`TacticalRules.secondAction`, Fleet Logistics or an extra-action grant, urgent plans only); the file
   says so elsewhere.
7. AI_PLAYER.md (Produce): "Activating a system to build is only worth it for at least 4 units, unless the build
   scores or advances an objective." `TacticalPlanner.produce` also accepts a build costing ≥ 6 resources, a defense
   build (`build.defense()`), or any plan with a ground shift.
8. AI_PLAYER.md (Territory): the foreign-area cost "fading to nothing after round 4" — it starts fading after round 4
   and reaches 0 at round 8 (`earlyGameWeight`).
9. Old PR body: "plans command tokens across the round". Inside this scope there is no cross-action token plan; there
   is the scoring reserve, the budget filter, the move-out rule and the opening holds. The status-phase split
   (`TokenPlan`, `ti4.ai.strategy`) only counts up to 4 distinct targets of `TacticalPlanner.ranked`.
10. Old PR body: "ranks activation plans by expected loss and gain" — accurate in substance; "keeps a home defense
    level that scales with round, enemy reach and point lead" — matches `homeDefenseLevel` (round, reach, lead and
    near-win).

### Combat and production

1. AI_PLAYER.md, "Ready for dice": "a ready message naming the seat that is newer than the round's start, which is the
   newest bot message in the thread that is not a declaration". In `ReadyForDice.opponentReady` the reference point is
   the newest message containing "Start of Combat Round #" (`newestRoundHeader`). The newest non-declaration message
   (`startedAt`) only anchors the seeded pause.
2. AI_PLAYER.md, "Produce", step 2 "Core. Carriers up to two": no fixed cap. `Builder.expansionCarriers` adds one carrier
   when none is on the board, then more while carriers < claimable systems within two carrier moves and available ground
   forces exceed carriers; `cargoCarriers` adds more while cargo exceeds capacity.
3. AI_PLAYER.md, step 2 "the infantry the dock is short of a carrier load, and two mechs (four for _Mechanize the
   Military_)": code builds infantry to 2 spare (`MIN_SPARE_INFANTRY`) in this phase and to 4 spare later
   (`planCoreGround`); four mechs only while the Mechanize campaign is active (ETA slack <= 2, feasible), plus one more
   when the mech has deep space cannon and a rival is within two moves.
4. AI_PLAYER.md, step 4 "dreadnoughts up to three": `WANTED_DREADNOUGHTS` = 4 (5 with Fracture interest), counted over
   the whole board plus planned.
5. AI_PLAYER.md, the step list omits phases the code runs: defensive bundles (`planDefensiveUnits`, after scoring),
   Gravleash ship variety, spare infantry to 4 (`planCoreGround`), Auto-Factories trigger seed/top-up.
6. AI_PLAYER.md, step 5 "Fill ... fighters ... then infantry": in round 1 the fill is infantry first, then fighters
   (`fillProduction`; [ProductionPlannerTest](/src/test/java/ti4/ai/tactical/ProductionPlannerTest.java)
   `fillsWithInfantryBeforeFightersInRoundOne`).
7. AI_PLAYER.md, "Plain destroyers are only built for objectives": destroyers are also defensive bundles
   (`DEFENSE_SHIPS`), presence ships, Auto-Factories trigger ships (cheapest counted type) and the filler option of
   `oneShipOptions`.
8. AI_PLAYER.md, "each carrier keeps room for two infantry": the reserved room is
   `max(min(2 x carriers, boardable ground forces), ground forces already in space)`, so it is capped by the ground
   forces that could board.
9. AI_PLAYER.md, combat: "a single hit an opponent's ability assigns to one of its units (it sustains if it can,
   otherwise takes the loss)": correct, but the loss taken is the first offered button, not a chosen cheapest unit.
10. AI_PLAYER.md roadmap row and old PR description ("Still to do: ... the mech secrets (Mechanize the Military, Occupy the
    Fringe)"): the code plans both secrets (`GroundCampaign`, `ProductionPlanner.planFringeInfantry` and mechs wanted,
    `TransitDiodesRules`, `GroundDrops` campaign values, mech-first landing order in `TacticalRules.landingOrder`).
    Whether they are "done" is a judgement, but "to do" is no longer accurate as stated.
11. AI_PLAYER.md, "Ready for dice": the memory key is given as `ready|<position>@<holder>#<round>`; the code key is
    `ready|<channelId>|<position>@<holder>#<round>` (`ReadyForDice.forRoll`), and the automation variant is
    `automation|<channelId>|...`.

Statements checked and consistent: AI_PLAYER.md, Assault Cannon "destroys its own cheapest non-fighter ship" (the AI
picks the lowest `lossCost` among the offered buttons, and the engine offers only non-fighter ships for this loss); the retreat threshold 35% to 20% with a victory point (8.0) as critical; retreat
after fighting the announced round; never retreating from home; the evacuation inequality; the seeded pause windows
3-10 s and 15-45 s; the 3-minute AI ceiling and the 120 s human grace; neutral stand-in timings 10 s / 120 s;
Fracture fleet 1 carrier, 3 dreadnoughts, 6 infantry at 0.75 per resource; scoring value 0.5 per resource and 0.3 of a
point enabler; Sling Relay's 1.5 stall and 1.0 for an objective ship.

### Strategy cards and technology research

1. **Tech tempo.** AI_PLAYER.md says the research cards value a technology at "(4 + 1) / 3 = 1.67 trade goods per point (`ResearchPolicy.TECH_TEMPO`)" and gives the example "3 × 1.67 − 4 + 1.5 = 2.5". The code computes `TECH_TEMPO = (ValueScale.RESOURCE × 4 + STRATEGY_TOKEN)/3` = (0.8 × 4 + 1)/3 = **1.4**, so the example figures are outdated.
2. **Construction follow.** AI_PLAYER.md says it follows "when a revealed or held structure objective is 1 or 2 structures short". The code follows when `StructurePolicy.bestSecondary.value − StrategyTokenCost.toSpend ≥ 0.5` and a token is spare (`followConstruction`). A dock's base value (4 in rounds ≤ 3) passes without any objective. The token-saving test (`wantsToFollow`) uses `nearsStructureObjective` (≤ 3 short, or the perimeter ≤ 3 short) or `cheapDockWorthFollowing`.
3. **Thunder's Edge Warfare timing.** AI_PLAYER.md says it plays "to reactivate a system or with no tactic token". The code (`CardTiming.warfare`) also plays it at once in round 1, and for any non-reactivating Warfare target scoring ≥ 1.0. It holds the card only while the main use is a home double build or build-then-move and home is unlocked.
4. **`PickPrior` source.** AI_PLAYER.md says the nudge comes "from how often human Nekro players take that card". The code has separate tables for Hacan, Xxcha, Sardakk, Letnev and Sol. Every other faction uses the table named `NEKRO_PICKS_BY_ROUND`.
5. **`ResearchPolicy` source.** AI_PLAYER.md says it is "informed by the technologies Nekro players most often end games with". `ResearchPrior` has data for five factions only and returns 0 for Nekro. The generic values are a hand-set table with no stated source.
6. **Leadership and the custodians race.** AI_PLAYER.md lists "acting first in a race for the custodians" as a Leadership use. `LeadershipUses` has no such benefit. `InitiativeRace.gain` is added to every card at pick time, and favours low initiatives in general.
7. **Politics follow.** AI_PLAYER.md says "with 2 or more spare strategy tokens and room for 2 cards". The code requires room ≥ 2, `hasSpareStrategyToken`, and `strategicCC − tokens wanted by all other cards (including the scar token) ≥ 1`. That is one token left after the other wanted follows, not "2 or more spare".
8. **Technology follow.** AI_PLAYER.md says "a technology worth at least 3 ... affordable after the scoring reserve". It leaves out that the worth threshold drops to 1.8 when idle trade goods cover the 4 resources (`IDLE_WORTH_SHARE` 0.6).
9. **Leadership follow.** AI_PLAYER.md says it buys "up to 3 command tokens". The code allows 4 when the seat holds a playable Strategize and has no strategy token (`TokenPurchase.maxTokens`).
10. **Speaker choice.** AI_PLAYER.md says Politics "makes itself speaker (or the player furthest behind)". With `SPEAKER_SALE` on (the default) and no full priority-track mode, a seat whose own speaker button is not offered (it is already speaker) names the non-contender after which it picks earliest (`SpeakerSale.speakerByPickPosition`). It may also first offer to sell the token. "Furthest behind" applies only to control seats or full priority-track mode.
11. **Diplomacy lock.** AI_PLAYER.md says "first locks the threatened system its plan chose". The code also locks with no threat at all, using the highest lock exposure (`DiplomacyUses.bestLockTarget`).
12. **Prerequisite-skip cost.** AI_PLAYER.md says research is valued "minus 0.7 per resource or influence lost" to a specialty planet. The code uses `PrerequisiteSkips.VALUE_PER_RESOURCE = 0.7 × ValueScale.RESOURCE` = 0.56 per point.
13. **Old PR body, package attribution.** The body attributes "sequences round-1 openings (Warfare, Construction, Gravity Drive)" to the tactical planner. The opening simulation is `ti4.ai.strategy.OpeningOutlook`. The tactical package only reads `OpeningHold`.
14. **Old PR body, size.** The body gives `strategy` as "~7.9k" lines. It is now 9,399 lines, or 8,364 non-blank.

### Trading, deals, promises and promissory notes

1. AI_PLAYER.md "Promissory notes": "it never plays _Trade Convoys_". `ConvoysRules` plays it: `insteadOfTacticalAction`
   and `beforePassing` are called from `StandardBrain`, and `ConvoysRules.next` presses `componentActionRes_pn_<note>`.
2. AI_PLAYER.md "_Alliance_ (`AllianceValue`): … rated per faction (Keleres 3, Jol-Nar 2.5, Hacan, Xxcha, Sol and Nekro
   2, others 0.5 to 1.5), half while locked … Its own costs 0.3". Code: `AllianceValue.ROWS` has (early, late, locked
   penalty) rows, e.g. Keleres (1, 1, −1), Jol-Nar (1, 1, 0), Hacan (1, 0, −1), Xxcha (1, 0, −1), Sol (1, 1, −1), Nekro
   (2, 1, 0); a locked commander subtracts the penalty, not half; public and private buyer bonuses apply. Its own costs
   max(0.25, 0.6 × best other buyer's value − 0.2 for neighbours). "Nothing for Mahact" matches (`hubris`).
3. AI_PLAYER.md "_Political Secret_: 1 per agenda phase still to come, through round 5's". Code
   (`NotesForTrade.politicalSecretValue`): 3.0 normally, 8.0 in the agenda phase when the agenda is important to its
   owner, 0.25 from a Galactic Threat owner, 1.0 from Xxcha, 2.0 for that owner itself while holding a rider. The
   "agenda phases left" count is used only for Political Favor.
4. AI_PLAYER.md "_Political Favor_ … a quarter of a strategy token per agenda phase still to come". Code: give cost =
   5.0 + `CardValueScale.STRATEGY_TOKEN` (1.0) × agenda phases left; a received Political Favor is worth 0.5.
5. AI_PLAYER.md "_Ceasefire_ … Otherwise its own costs 2.5 (or half of what it could take from the receiver)". Code:
   `CeasefireValue.GIVE_COST = 6.0`, so max(6, 0.5 × stake).
6. AI_PLAYER.md "Military Support … charges a buyer half a trade good more per point it leads the table by". The price
   is always 1 TG (`MilitarySupport.PRICE`); the 0.5 per point of tempo lead is part of the internal give cost
   (0.1 + 0.5 × lead), which makes the sale fail the proposal margin, not a higher price.
7. AI_PLAYER.md "Promise deals … At most 2 offers a round (territorial) and 2 an agenda (votes)". With
   `CONDITIONAL_VOTE_DEALS` (enabled by default) the vote cap is `MAX_BLOC_OFFERS = 4` (`PromiseDeals.maxVoteOffers`).
8. AI_PLAYER.md "What it starts. On its own turn … (at most 2 offers a turn)". Debt payment and Hacan facilitation run
   before the cap check and do not count toward it; a technology announcement is a table-talk post, not an offer.
9. AI_PLAYER.md Status table: V3 "Deeper deals: votes for trade goods, multi-party pacts" is listed as future, while
   vote-for-TG promise deals (`VOTE_FOR`, `NOT_AGAINST`, `NOT_FOR`) are implemented; multi-party pacts are not.
10. AI_PLAYER.md "_Support for the Throne_ … Its own given away: the option of a better deal … less the peace it buys
    on a border". This omits two terms in `ThroneValue.givenCost`: the early cost of 6 TG until `ThroneTiming.open`,
    and the `fit` term (1.5 for neighbours, or by fleet size for aggressive factions).
11. AI_PLAYER.md "Trust … −0.3 for not paying for a free Trade follow, −0.2 for not paying a debt it collects" omits
    `EXCLUDED_REPLENISH_UNPAID` (−0.3) and the −0.3 per broken/unpaid promise (`Trust.BROKEN_PROMISE`, with
    `PROMISE_DEALS`), the latter mentioned elsewhere in the document.
12. Old PR body: "`trade` (~6.8k)" lines. `ti4.ai.trade` is now 10,452 lines (64 files), plus 509 in `ti4.ai.deal`,
    573 in `ti4.ai.promise`, 1,017 in `ti4.ai.promissory`.
13. Old PR body: "Trading: through the bot's own transaction buttons, never by reading chat". Deal terms are never read
    from chat, but the AI does read bot-authored text: rejection notices, the debt reminder addressed to it, and AI
    table talk for Technology payments. Human-AI multi-step deals (Carth), promise deals and speaker sales do not use
    the transaction builder; they post their own buttons and execute through `TransactionHelper.acceptTransactionOffer`.

Statements checked and found consistent: margins 0.3/0.5; own commodity 0.6/0.3; debt value trust × reach (0.9) × 0.5
near the end; VP worth 6 → 12, 30 for a win; desperation ≤ 3/4 of the point; Throne sale vetoes (public path, hidden
path unless finishing first, swap, last-resort point, ≥ 10 TG without debt, extortion); extortion 1–5 TG, ≤ 2 a round,
5/15 min, +2 per paid demand, details text; reply timeouts 5 min / 12 h / 24 h; 12 drafts an hour, 3 pending, one
unsolicited offer per human per round; debt "never 3 or more across the table"; Ceasefire duress thresholds 8 and 1.5;
relations personality starts and ±1 per round; trust start 0.8 and its loss on restart/undo.

### Agenda phase

1. AI_PLAYER.md says "It votes everything when a point is at stake, half otherwise, and abstains on agendas it has no
   stake in." That rule exists only in `BallotPlanner.withoutForecast` (no-forecast modes; all votes when the
   preference is ≥ 8 TG). With a forecast, the vote count is chosen by valuing every reachable planet subset total,
   with a 0.5 TG floor, kept-vote prices and fewest-near-best.
2. AI_PLAYER.md gives the forecast softmax "temperature 4 trade goods", twice. `AgendaForecast.TEMPERATURE` = 3.0.
3. AI_PLAYER.md says "A seat that sees nothing between the outcomes (within one trade good) abstains three times in
   ten and otherwise chooses at random." That is only the legacy path. With `AGENDA_VALUE_TABLE`, on by default, an
   indifferent seat always abstains (`AgendaForecast.pick`).
4. AI_PLAYER.md says "Every seat that still has votes casts them all for one outcome." With `AGENDA_HELD_BALLOTS`, on
   by default, simulated voters on a non-final agenda cast only the votes needed (`heldBallot`). Last-voter best
   response and abstain choices exist under `AGENDA_ABSTAIN_CHOICE`.
5. AI_PLAYER.md says "any other agenda is its policy score clamped to two units of 4 trade goods", and the
   limitations list says "`AgendaPolicy` values nearly every For/Against agenda at 0". Every official agenda now has a
   value in `AgendaValues` (expert or legacy tables). The clamped policy score is only the fallback for agendas with
   no table entry.
6. AI_PLAYER.md says "a point election is worth … minus a quarter of a point to everybody else". `rivalPointStake`
   uses 0.75 of the point value unless the rival is far behind (0.25). The expert table prices Shard at 6 TG,
   Crown of Emphidia at 1 and Censure at 3 or 5 to the holder, scaled by threat, unless the point wins the game.
7. AI_PLAYER.md (Shenanigans courtesy) says "It never plays those cards (the bot has no automation for them)", and
   the limitations list says "Deadly Plot and Bribery (the bot has no automation for them)". With `AGENDA_SABOTAGE`,
   `ShenanigansRules.react` → `ShenaniganCards.playIfWorthIt` plays Deadly Plot (via `autoresolve_manual` →
   `resolveWithNoEffect`) and Confounding Legal Text (via `agendaResolution_<faction>`). Only Bribery and Confusing
   Legal Text are still always refused.
8. AI_PLAYER.md gives the shenanigans delay as "5 to 25 seconds". The code computes 5 + floorMod(…, 20) = 5–24 s.
9. AI_PLAYER.md says Veto is queued when "the agenda's expected election costs the seat 5 units or more
   (`AgendaPolicy.worthDiscarding`)". `AgendaCardRules.worthVetoing` = `AgendaPolicy.veryBadFor` (expected loss over
   the forecast with its own ballot ≥ 3.0 TG expert, 3.5 legacy; `worthDiscarding` only without a forecast) or
   `worthDiscardingFor` a pooled bounty.
10. AI_PLAYER.md says Quash is queued when the agenda "is expected to cost it more than that token is worth". With
    `AGENDA_VALUE_TABLE` the threshold is the token value 1.0 plus a 1.0 margin, or a bounty that lifts loss + bounty
    above it.
11. AI_PLAYER.md says Galactic Threat "predicts the outcome most likely to win (the election of the player with the
    most votes)", and "Galactic Threat predicts the outcome with the best chance". With the default flags the
    prediction maximises `votedChance` (wins carried by non-predictor votes) on the forecast that includes the
    prediction. Start votes are used only without a forecast. Queueing is also gated by `suitsThisAgenda` (0.2 / 0.7
    thresholds), which the text does not mention.
12. AI_PLAYER.md (rival Imperial Rider) says the seat "votes all of its votes for the outcome that leaves it best
    off". There is no separate Imperial Rider branch in `AgendaVoting` or `BallotPlanner`. The rider lowers the
    outcome's utility for other seats inside `AgendaForecast.utilities`, and the vote count follows the general
    planner. The test `votesDownARivalsImperialRiderWithAllItsVotes` passes for its scenario, but "all votes" is not
    a rule.
13. AI_PLAYER.md ("Mutiny keeps its own rule") and the old voting bullets: Mutiny is valued by
    `DirectiveValues.mutiny` with an own-vote term and the forecast's `joinShift`. It has no expert-table entry.
14. The old PR description counts `agenda` at "~1.6k" production lines. The package now has about 7.5k lines (43 files).

### Action cards

1. AI_PLAYER.md, "Shenanigans courtesy": "It never plays those cards (the bot has no automation for them)". The code
   plays _Confounding Legal Text_ and _Deadly Plot_ (`ShenaniganCards`, called from `ShenanigansRules.playInstead`)
   when experiment `AGENDA_SABOTAGE` is enabled, which it is by default (`AiExperiments.enabled`).
2. AI_PLAYER.md, Known limitations, "Agenda cards, deferred": Confounding Legal Text "needs a bot-side guard first"
   and "_Deadly Plot_ and _Bribery_ (the bot has no automation for them)". Both Confounding and Deadly Plot have an AI
   play path (`ShenaniganCards.electItself`, `discardTheAgenda`); Bribery is indeed not played.
3. AI_PLAYER.md, Known limitations, "Action cards, combat": "Not yet: _Shields Holding_ and _Maneuvering Jets_".
   Both are played by `HitCancelCards` (tested in `HitCancelCardsTest`, 21 tests). The old PR description's "Still to do:
   ... (_Shields Holding_, _Maneuvering Jets_, _Fire Team_, ...)" is outdated for the first two.
4. AI_PLAYER.md, Known limitations: "Left to humans: _Direct Hit_ (the bot asks for the ship in chat), _Skilled
   Retreat_, ...". `DirectHitRules`/`DirectHitCards` and `SkilledRetreatCard` play both (tests
   `DirectHitCardTest`, `SkilledRetreatCardTest`). The same document's combat section already describes Direct Hit
   as played, so the document is internally inconsistent.
5. AI_PLAYER.md, combat section: "Not yet: the other combat cards ..., _War Machine_, _Rally_, _Parley_, ...". War
   Machine is played (`ProductionCards`, called from `TacticalRules.warMachine`).
6. AI_PLAYER.md, card table: "Every row of this table, and the economy cards, also adds the stall." Forward Supply
   Base and Counterstroke are built with `AiActionCards.reaction` / `reactionWithPlayButton`, which add no stall;
   Flank Speed has no evaluator.
7. AI_PLAYER.md, Strategize row and Known limitations: "never Technology ... or Leadership". Leadership is reachable
   before passing through `StrategizeCard.stalledPlan` (`CardFacade.stalledLeadershipValue`) and
   `StrategyCardRules.startLeadershipSecondary` (tests in `StrategizeLeadershipTest`). Technology remains excluded.
8. AI_PLAYER.md, "Action cards, fog of war": "Cards aimed at a player, planet or system ... are never played in
   fog-of-war games". Several targeted cards (Spy, Insubordination, Forward Supply Base, Uprising, Ghost Ship,
   Exploration Probe) have no Fog of War check in their plans; the statement holds in practice only because AI
   seats are refused in Fog of War games (`AiGameSupport.unsupportedModes`).
9. AI_PLAYER.md, combat cards: "A card is played when its swing reaches its keep prior". Incomplete:
   `CombatCardRules.bestAtRoundStart` also plays the first Morale Boost whenever the loss chance is within
   [0.2, 0.8] regardless of swing, and a Skilled Retreat that denies a secret.
10. AI_PLAYER.md, Sabotage: "5 to 14 seconds when only AIs play, 10 to 39 minutes otherwise". Incomplete: the short
    delay also applies once every human other than the card player has answered (`SabotageRules.everyHumanAnswered`,
    `SabotagePassDelayTest.passesQuicklyOnceEveryHumanHasAnswered`).
11. AI_PLAYER.md, Overrule: the reserve is lifted "for any seat that is about to pass". In `OverruleReserve.active`
    the reserve stays active before passing when no player is a contender; it lifts before passing only when some
    contender exists.
12. AI_PLAYER.md, Pirate Contract row: "the rivals' approaches it blocks ..., else a pure stall". The code also
    takes `NeutralSetups.value` (in turn) and `DustTarget.value` when those are positive.
13. AI_PLAYER.md, "Keeping and discarding": usable cards are worth at least their keep prior. Confounding Legal Text
    and Deadly Plot are played but `keepValue` returns 0 for them (not in `AiActionCards.CARDS`).
14. Old PR body: "Sabotage with identical delays whether or not it holds one". Supported for the press itself
    (`SabotagePolicyTest.waitsTheSameWhateverItWillPress`); see uncertainty (b) for the post-answer hold.
15. Minor numeric wording: "a third of the planet's value" (Mercenary Contract) and "a third of the planets it could
    still take" (Signal Jamming) are 0.3 in code (`PLANET_SHARE`, `JAMMED_PLANET_SHARE`).

### Scoring and secret objectives

1. AI_PLAYER.md, Public objectives: "`Wallet`: planets before trade goods, each planet paying resources or influence,
   never both". With the Xxcha hero unlocked (`Pooling.COMBINED`) one planet pays both kinds; with the Xxcha
   breakthrough each kind counts the larger value (`Pooling.EITHER_AT_BEST`).
2. AI_PLAYER.md: "It prefers the objective worth more points, then one that costs nothing." The second key is fewer trade
   goods + tokens only; exhausted planets are not counted. The third key prefers a spend objective in the status phase.
   So in the status phase a planet-paid spend objective beats a free objective of the same points.
3. AI_PLAYER.md, Reserve: "keeps back what the cheapest affordable spend objective needs". `ScoringReserve.bestAffordableSpend`
   takes the highest-VP affordable spend objective (cheapness only breaks ties, by trade goods + tokens), falls back to a
   projected one funded by expected trade goods and Diplomacy readying, and reserves nothing when a rival is expected to
   win first unless `WinRace.winsWithReserve` holds.
4. AI_PLAYER.md, Action phase list: "_Become a Martyr_ and _Prove Endurance_" are listed among secrets judged when a combat
   ends. `bam` is judged from home planet loss (`watchHomePlanets`), not combat end; `pe` is not handled in
   `ActionSecretRules` at all (it relies on `StandardBrain.queueProveEndurance` pressing `autoProveEndurance_yes`).
5. AI_PLAYER.md, Keeping secrets: "A secret it can score right now is worth a flat 2.0, and a status secret is halved when
   other scorable ones already fill the rounds left." A congested ready secret is not 2.0 halved (1.0): it is
   1.0 x phase value (+ opening tempo) x 0.5 = 0.5 in rounds after the first.
6. AI_PLAYER.md, Drawing and trading secrets: "it draws a secret whenever it can (Imperial, ...), and with a full hand draws
   anyway and discards its weakest, unless every secret it still holds is worth keeping". The Imperial primary draws
   without the `worthDrawing` check whenever it does not hold Mecatol Rex and has not scored all secrets
   (`StrategyCardRules.imperial`); the check applies to the secondary and Strategize.
7. AI_PLAYER.md, Patience: "_Make an Example of Their World_ and _Destroy Their Greatest Ship_ are never saved." True for
   `ActionSecretTiming`, but `TacticalPlanner` additionally prices `dts`, `dtgs`, `btv` at a whole point always, and `btv`,
   `uf`, `dyp`, `fwp` at a whole point against a neutral opponent; the document does not mention this.
8. Old PR body, package sizes: "`scoring` (~2.2k), ... `secrets` (~1.8k)". Line counts (`wc -l`) are now 3,612 for
   `scoring` and 1,881 for `secrets`.
9. Old PR body, "Still to do: ... the mech secrets, Strengthen Bonds." `mtm` and `otf` are modelled
   (`ObjectiveValue.progress`, `GROUND_SECRETS`, `SecretValue.boardFeasibility` via `GroundCampaign`), and `sb` has an
   achievability factor (0.1) here and note-buying elsewhere (`NoteSecrets`, per AI_PLAYER.md), so those items are at least
   partly implemented.
10. AI_PLAYER.md, "When a combat meets two, the rarer one (by how often real players finished it) is scored." Implemented
    as a fixed list order (`ActionSecretRules.COMBAT_SECRETS`) that matches the completion table, not as a runtime lookup;
    the behaviour matches the statement while the table is unchanged.

### Exploration, relics, expeditions and legendary planets

- "a relic fragment 2 (3 for the one that completes a set of three ...)" and "A fragment is worth 2 trade goods, and 3
  when it is the third of a kind, so a set of three is worth 7": `CardValue` uses `FragmentValue.worth` = 3.5 x (sets +
  0.45 x pairs + 0.2 x singles); marginal values 0.7, 0.875, 1.925; a full set 3.5. The Destroy Heretical Works +2 for
  the first two fragments matches.
- "Everything an exploration gives is valued in resources: a trade good 1": the unit is the trade good; a resource is
  0.8 (`ValueScale`).
- Fragments mid-turn "when no plan scores 2.5 (1 for having the relic sooner, 1.5 for waiting ...)": the threshold is
  `RelicValue.draw x 0.3 + Stall.value`, i.e. 0.3 of the average relic plus 1.5 plus `Endurance.stallBonus`; not a
  fixed 2.5.
- Nano-Forge "+2 resources, +2 influence (0.6 each) and a legendary planet (2)": `ValueScale.spendable(2, 2)` is
  0.8 each (3.2), so 5.2 total, x 0.6 when reachable. "the safest ... planet": all unexposed eligible planets tie and the
  first button wins.
- JR-XS455-O "exhausted before passing": it is also a `RelicFlows` candidate mid-turn (net 1.0 + 1.5 against the
  tactical plan score).
- Circlet "a frontier card, 1.5": `ExploreValues.frontierCard` adds the Derelict Vessel share x secret draw value.
- Book of Latvinia "in the 2 minutes after the draw": the 120 s window starts when the AI first sees itself holding the
  book (`latviniaSeen`), not at the draw.
- Maw of Worlds "0.25 per influence": 0.25, or max(0.25, `AgendaPrior.phaseVoteValue`) under `AGENDA_VALUE_TABLE`;
  "the votes the exhausted planets would have cast" is read from ready influence (`getPlayerInfluenceAvailable`).
- Breakthrough value: the doc's formula omits the 8.0 floor for Hacan and Letnev (`PRIORITY_FLOORS`) and the max with
  `YinAscendant.value`; `claimNow` also skips the waiting comparison for those factions and on the last slice.
- "the last slice is rushed when it makes the seat the holder (about +6.9 when tied)": with 6 players and full timing
  the formula gives 8 x (1 - 1/6) = 6.67.
- Industrex "in the system with the best objective gain, then the most ships": tiles are visited in order of the seat's
  ship cost (costliest fleet first) and the strict `>` keeps the first; value = filler x cost + objective gain, not the
  ship's cost.
- "Removing an infantry costs 0.7 (production limits included)": `ExploreSite.infantryCost` is a flat 0.7 plus need and
  risk; no production-limit term.

The old PR description only lists sizes and coverage for this scope (`explore` ~2.6k lines, which matches 2,630); nothing in it
contradicts the code.

### Faction packages

1. **Viscount Unlenn threshold.** AI_PLAYER.md (Barony of Letnev, round start): "when the extra die for the ship with the
   best hit chance gains at least 1.5 ... in a fight the seat has at least a 10% chance in". `ViscountUnlennRules.atRoundStart`
   has no value or win-chance check; it uses the ready agent in every space combat round where its button is shown. The
   test names `usesTheAgentOnAFightItHasAlreadyWon`, `usesTheAgentOnAHopelessFight`,
   `usesTheFreeAgentEvenInASkirmishBecauseItReadiesEveryRound` agree with the code.
2. **Evelyn Delouis threshold.** AI_PLAYER.md (Federation of Sol): "when the extra die on its best ground force moves the
   win chance times the stake by at least 0.2". Code: `CombatForecast.ExtraDie.worthSpending` = win without < 0.95 and
   win with ≥ 0.05; no stake term (SolAgentRulesTest: 8-vs-1 keeps, 1-vs-12 keeps, 1-vs-1 uses).
3. **Nekro hero valuation.** AI_PLAYER.md: "half a point per trade good". Code: 0.5 per (resources + influence) on the
   trade-good scale (a point is 8.0), so 1/16 of a point per trade good, and the hero is played only at a total ≥ 3.0
   (not mentioned). "then ends its turn" is not part of `NekroLeaderRules`; `takeTurn` ends the turn afterwards.
4. **Command token priority list.** AI_PLAYER.md lists "2. tactic up to 2 ... 4. fleet up to 3". The status-phase list in
   `CommandTokenPolicy.priorities` is: fleet to the largest stack, **tactic 1**, the hero fleet target, the open-slot
   objective fleet, then strategy 1 / strategy 2 / tactic 2 (or tactic 2 / strategy 1 / tactic 4 / strategy 2 under a stall
   threat), then **fleet 2** (`SPARE_FLEET`), a build block (tactic 3, build fleet), objective fleet, strategy target,
   Fracture fleet, stacking fleet, tactic 5, strategy 3. The action phase uses a different list (`TokenPlan`-driven).
5. **Quantum Datahub Node.** AI_PLAYER.md lists it among "technologies it does not use". `DatahubRules` uses it (swap at the
   end of the strategy phase), registered in the rule list.
6. **Quash threshold.** AI_PLAYER.md: quashes when the expected cost exceeds `CardFacade.strategyToken()`. Code adds 1.0
   when the seat's `AGENDA_VALUE_TABLE` experiment is on, and also quashes when a discard bounty lifts the loss over the
   threshold (`QuashRules.paidEnough`).
7. **Old PR body, factions.** "Factions: Nekro Virus, Sardakk N'orr, Emirates of Hacan, Barony of Letnev, L1Z1X Mindnet,
   Naalu Collective, Federation of Sol, Universities of Jol-Nar, Xxcha Kingdom, Yin Brotherhood." omits the Winnu, which
   `AiSettings.FACTION_NAMES` includes. Its diagram node "Faction rules: Nekro, Sol, Sardakk, Hacan, Letnev, Xxcha" omits
   the Yin, Naalu and Winnu packages that now exist (WIP).
8. **"the Nekro-only rules ... switch on for Nekro only"** (AI_PLAYER.md, Factions): in scope, every faction rule gates on
   the component, not on the faction; whether Technological Singularity is ability-gated is unverified (see (b)).
9. **Orbital Drop.** AI_PLAYER.md's "plus 1.5 for stalling" omits `Endurance.stallBonus` and the minimum net of 0.6
   (`OrbitalDropRules.MIN_NET`).
10. **Darktalon Treilla.** AI_PLAYER.md describes only the stacking and stall uses; the code also plays it for a whole-fleet
    Mecatol assault (`assaultsMecatol`) and never in Fog of War. Omission rather than contradiction.
11. **WIP factions and `/ai play`.** "never picked by default, `/ai watch` or `/ai play`" holds for AI seats
    (`pickOpponentFactions` uses `DEFAULT_FACTIONS`), but `AiPlay.randomFaction` gives the human player a random faction
    from all `SUPPORTED_FACTIONS`.

Statements checked and consistent with the code: Munitions Reserves (2.5 with a 10% floor, +8 when it breaks a reserve),
Dunlain Reaper (gain ≥ 2), Darktalon stacking gain ≥ 4, Sh'val thresholds (60% / 65% / a point / half a point), G'hom
Sek'kus (30-85% and 8 points), Foresight, Z'eu, Matriarch hit order, Mathis Mathinus (2 points or a win; 1 before passing),
Lazax Gate Folding (1 + stall), Hegemonic Trade Policy (resources + 2), Reclaimer, Indoctrination (2 influence = 1.6),
Devotion (0.5), Brother Milor (1.0), Brother Omar (0.4 per infantry), Dannel (3 infantry, quarter-point hold), Yin
Ascendant, Xxekir Grom (4 planets, 4 TG).

### Engine changes and AI data

1. **"Seven small generic changes don't reference `ti4.ai`"** (`AI_PLAYER.md`, Segregation) and **"about 330 added
   lines across 23 files"** (old PR description). The current diff outside `ti4.ai` is 143 files (113 production Java files, 92 of them
   converting random draws). The listed Nekro hero channel, bombardment Auto-assign, Sabotage double press, War Machine / Flank Speed
   cancel and Veto resume changes are not in the current diff: the Sabotage/War Machine/Flank Speed/Veto work is on
   master (`SabotageService` holds `canceledActionCardWindows`; commit `d19887738a`, "Non-AI fixes from AI v0.1", #5974).
   The list omits most of what is in the diff: `RandomHelper` seeding and 92 converted files,
   `SpaceCannonOffenceWindow`, the SPACE CANNON DEFENSE prompt for all defenders with an owned cancel, the zero-hit
   guards, `AgendaHistory`, `GameStats.actionCardTargets`, the public `AgendaHelper.getVotingOrder`/`getVoteTotal` and
   `TransactionHelper.acceptTransactionOffer`, the `LinkedHashMap` ordering in `CombatRollService`, `Tile.hashCode`,
   the stamp-free `GameSnapshots.copy`, the performance changes, the Frame Shift and Peace Accords fixes, and two TODOs.
   (`ButtonHelper.fleetCheck`, listed in the old PR description, is in the diff.)
2. **"No special engine paths. The AI presses the real buttons through the real handlers"** (old PR description). Mostly true,
   but `HumanAiDealRunner.transferAll` calls `TransactionHelper.acceptTransactionOffer` directly inside a human's press
   of an AI-owned button.
3. **"Not yet: _Shields Holding_ and _Maneuvering Jets_ (they post nothing ... the second cancel button is unowned ...)"**
   (`AI_PLAYER.md`, Known limitations, combat cards). `HitCancelCards` plays Shields Holding against `autoAssignSpaceHits_`
   prompts and Maneuvering Jets against `autoAssignSpaceCannonOffenceHits_` and `autoAssignGroundHits_` prompts, via
   `CombatCardRules`; the SPACE CANNON DEFENSE cancel this branch adds is owned. The old PR description's "Still to do:
   _Shields Holding_, _Maneuvering Jets_" is likewise outdated.
4. **"Every random draw the game makes goes through `RandomHelper.current()`"** (`AI_PLAYER.md`, Testing). The
   `Math.random` in `AuctionFrankenDraftService` and the one-argument `Collections.shuffle` calls left in
   `TheodisiAgendaResolver`, `IxthLegendaryButtonHandler`, `AuctionFrankenDraftService` and `TileEmojis` do not (the
   shenanigans pre-pass draw and the secret-deck shuffles were routed on 2026-11-20).
5. **Relic fragment value "2 trade goods, 3 when it is the third of a kind, so a set of three is worth 7"**
   (`AI_PLAYER.md`, and `docs/ai/RELIC_REPORT.md` "2 per fragment, plus 1 for every relic it could purge"). Code
   (`FragmentValue.worth`): `RelicValue.BASE` (3.5) × (complete sets + 0.45 × pairs + 0.2 × singles), plus 2.0 for each of
   the first two fragments while holding an unscored _Destroy Heretical Works_. A set of three is 3.5, a pair 1.575, a
   single 0.7.
6. **Expert tier strength "A ranked card's strength is the mean of the two sources"** (`AI_PLAYER.md`). True, but the
   mean is then clamped to [0.5, 2.0], so S-tier values above 2.0 are cut; the document does not say so.
7. **Old PR body "The PR currently shows merge conflicts with `master`"** and the checklist items are statements about
   a past state; not verifiable from code.
8. **`docs/ai/AGENDA_PLAN.md`** describes itself in its header as a plan ("Nothing has been changed or built") for
   worktree `vigorous-cerf-c2baec`, yet later sections carry dated "Done" entries (2026-11-12 to 2026-11-14, e.g. the
   voting order fix that uses `AgendaHelper.getVotingOrder`), so the header is stale. It names classes that do not
   exist at `HEAD`: `AgendaPromises`, `CommitteeFormationRules`,
   `RiderEffect`, `SecondAgendaValue`, `VoteValue`, `AgendaDeckReadsTest`. Other names it uses (`ArticlesOfWar`,
   `ClassifiedDocumentLeaks`, `GalacticThreat`, `PayTheAnswerer`, `RivalRiders`, `SpeakerFreePick`,
   `VoteAndDiscardDeals`, `ZeroGainBallots`) exist only inside `AgendaExamTest`/`AgendaVotingTest`, not as classes.
   Its seam remark ("making `AgendaHelper.getVotingOrder` public") matches what was done.
9. **`AI_STRATEGIC_AUDIT.md`** is pinned to baseline `b3766a0a2d`, older than `HEAD`; its claims were not re-verified
   beyond `ScoringPaths` existing. Its architecture description (ordered rules, first applicable wins, no
   minimax/MCTS) is consistent with what this scope shows (no search in the engine changes).
10. **`AI_LOOKAHEAD_PLAN.md`** statements checked and still true: `AiSettings.DECIDE_TIMEOUT` = 20 s,
    `AiRuntime.WORKER_THREADS` = 2, `AiActuator.press` checks only that channel, message and an enabled button with the
    id exist (and refuses ids that open a modal). Its "Done along the way" entry about copies stamping the live game matches the `GameSnapshots` change.

Checked and current: `AGENTS.md` segregation paragraph (AI `@ButtonHandler`s do live in `ti4.ai`), the `GLOSSARY.md`
entries (Slay text from `SlayIntent`, `Relations.MAX` = 2, 2 TG per relation point, the five personalities),
`AI_PLAYER.md`'s seam table (`SlashCommandManager`, `TestBedService.findNonDeveloper`), the kill switch command, the
`7100000` + 9-digit seat id, `AiRuntime` polling every 3 s from a `CronManager` task, `GameStats.actionCardTargets`
filled by the cards' resolve steps, the tier ranges S 2.2–1.8 ... D 0.75–0.45, keep prior 0.9 for Maneuvering Jets to
1.8 for Overrule, the Bunker TODO in `CombatRollService`, `Tile` hashing by tile id and insertion-ordered combat units.

### Testing and self-play harness

- AI_PLAYER.md, Testing: "`-Dai.selfplay.factions=sol,jolnar,...` seats other factions (default: the first six supported ones)". The default is `AiSettings.DEFAULT_FACTIONS` = nekro, sardakk, hacan, letnev, sol, xxcha; the first six of `SUPPORTED_FACTIONS` are nekro, sardakk, hacan, letnev, l1z1x, naalu.
- AI_PLAYER.md, Testing: "The setup is the same as in production: `createNewGame`, the standard 6-player map, `/ai add` for each seat". The harness uses `SelfPlaySetup` with the Thunder Dreaming map from the Thunder's Edge rulebook (`THUNDER_DREAMING_MAP_STRING`, tiles including 98 to 115), not the standard 6-player map; seats are added with `AiSeatService.addSeat`. AI_PLAYER.md elsewhere ("The standard self-play map (tiles 18-48) has no legendary planet") is also outdated for the same reason.
- AI_PLAYER.md, Testing: "A run ends at the victory point goal, when the objectives run out, or when nothing changes for two virtual hours (a stall)". It can also end `LOOPED` (the `🔁` notice), `LIMIT` (time or tick budget) and `LOST_GAME`.
- AI_PLAYER.md, Self-play: "After every seat has been idle for 20 seconds, the speaker AI presses the buttons any player may press". The presser is the seat owning the button's faction, else the speaker seat, else the first seat (`RefereeRules.presser`); the 20-second wait is measured from the lane's last AI action and the prompt's age, not from every seat's idleness; agenda resolution waits 60 s and a tie 10 min.
- AI_PLAYER.md, Testing: "`LegendarySelfPlayTest` ... checks that the seats exhaust them through the bot's real buttons". It hands out five planets but requires only Mallice, Hope's End, Primor and Ordinian (`EXPECTED`); Emelpar is not checked.
- AI_PLAYER.md, Testing: "`RelicSelfPlayTest` hands six seats relics". Letnev's five relics (Maw of Worlds, Prophet's Tears, Emelpar, Silver Flame, Heart of Ixth) are handed out but not checked.
- AI_PLAYER.md, Testing, A/B: "The summary prints the treatment-minus-control points and win share over complete pairs" holds for `AiSelfPlayTest`, but the same claim in the `RoundTwoSelfPlayTest` class comment does not (see section 10: round-two games never count as finished).
- AI_PLAYER.md, Testing: "Self-play turns the time budget off (virtual time)". Correct, but the switch is a harness stub (`AiSettings.thinkingBudget` → `Duration.ZERO`), so `-Dai.thinking.budget.seconds` cannot turn it back on in harness runs.
- Old PR body, size table: "`src/test/java/ti4/ai` | 338 | ~66,000" and "Test utilities (`FakeDiscord`, `SelfPlayEnvironment`) | 8 | ~1,700". Now 516 files and about 99,000 lines under `src/test/java/ti4/ai`; `testUtils/discord` has 8 files (about 1,800 lines), and `SelfPlayEnvironment` lives in `src/test/java/ti4/ai/runtime`, not with the test utilities.
- Old PR body, Known gaps: "Heuristic constants (stall value, thresholds, softmax temperatures)". Nothing in this scope uses a softmax or temperature (outside scope, `AgendaForecast` mentions one; not verified here).
- Old PR body, Testing: "mvn -o test -Dtest=AiSelfPlayTest # first round of a six-seat game" is correct; it does not mention that the first-round test and five scenario self-play tests also run in every default build.
