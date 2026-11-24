# AI players and faction components: what each faction uses, and what to improve

A running record of how the AI uses each faction's own components (abilities, leaders, faction techs, units,
promissory notes, breakthroughs). Every batch adds a dated section; the review notes record the developer's
verdicts so later batches can check them.

## How it is measured

`FactionComponentTracker` (test code, `src/test/java/ti4/ai/runtime`) runs in every self-play game. Per seat it
lists the faction's components and counts:

* **offers**: bot buttons whose id or label names the component (heuristic; generic info-panel buttons such as
  `naaluCommander` count too),
* **AI presses** and **AI reasons** naming it,
* **state uses**: changes only a use explains (leader exhausted/purged, tech exhausted, a note in another seat's
  hand or play area, a flagship or mech placed).

A component counts as **used** when the AI pressed a button for it or a state use happened. Passive abilities
(Telepathic, Unrelenting, Armada, Versatile, Arbiters, Blood Ties, Reclamation) and abilities resolved through
another card (Peace Accords, Propagation) show 0 even when they work: check them in the game log instead. For
faction techs a press is the research.

Run a batch over all nine factions (each faction sits in 8 of 12 games):

```
mvn -o test -Dspotless.skip=true -Dtest='AiSelfPlayTest#sixAiSeatsPlayFullGames' -Dai.selfplay.full=true \
  -Dai.selfplay.games=12 -Dai.selfplay.threads=8 \
  -Dai.selfplay.factionpool=nekro,sardakk,hacan,letnev,sol,xxcha,naalu,winnu,yin \
  -Dsurefire.failIfNoSpecifiedTests=false
```

The log has a `FACTION COMPONENTS` block (`FACTIONCOMP` lines) and one `FACTIONUSE <game> ...` line per event.

## Batch 1 (2026-11-16, AI PR head 558a023148)

12 games, all ended on victory points, no stalls. Leader 10.2 VP, table total 44.7, final round 5.8 on average.

| Faction | Avg VP | Wins (ties count) |
| --- | --- | --- |
| Sol | 8.6 | 3 |
| Sardakk | 8.1 | 2 |
| Letnev | 8.0 | 2 |
| Winnu (WIP) | 8.0 | 1 |
| Hacan | 7.8 | 2 |
| Nekro | 7.3 | 1 |
| Naalu (WIP) | 7.3 | 1 |
| Yin (WIP) | 6.5 | 0 |
| Xxcha | 5.5 | 0 |

### Used well

* Agents: Hacan, Letnev, Nekro, Winnu, Xxcha 8/8 and every round; Naalu Z'eu and Sardakk T'ro 8/8.
* Heroes: Hacan, Nekro, Naalu, Winnu 7/8, in rounds 3 to 4.
* Every faction built its mech (Hacan 7/8).
* Sol Military Support given 30 times, Advanced Carrier II and Non-Euclidean Shielding researched 8/8.
* Acquiescence and Greyfire Mutagen given in 7/8; Sardakk commander and N'orr Supremacy 6/8.

### Gaps

| Component | Batch 1 | Note |
| --- | --- | --- |
| Xxcha hero Xxekir Grom (TE) | ready 7, played 0 | |
| Sardakk hero Sh'val | ready 8, played 0 | |
| Sol hero Jace X | 2/8, round 5.5 | |
| Yin hero Dannel | 3/8 | |
| Letnev hero Darktalon Treilla | 4/8, round 4.8 | |
| Yin agent Brother Milor | 3/8, round 5.3 | combat buttons offered often |
| Sol agent Evelyn | 4/8, round 4.3 | |
| Yin Devotion | 1/8 | offered in 6 |
| Sol Orbital Drop | 0 presses | 3 reasons |
| Xxcha Quash / Letnev Munitions Reserves | 1/8 / 2/8 | |
| Faction techs never researched | L4 Disruptors, Neuroglaive, Hybrid Crystal Fighter II, Hegemonic Trade Policy, Lazax Gate Folding, Instinct Training, Nullification Field, Yin Spinner Omega, Quantum Datahub Node | Spec Ops II and Impulse Core 1/8, Exotrireme II and Valkyrie Particle Weave 3/8 |
| Notes never or rarely given | Political Favor 0/8, Antivirus 0/8, War Funding 1/8 | Gift of Prescience 0/8 is intended (1 VP minimum) |
| Breakthroughs | Mindsieve 1/8, Imperator 1/8 | |
| Flagships | Sardakk 1/8, Naalu and Letnev about half | |

### Review notes (developer, 2026-11-16)

* **Passive abilities**: check in the game logs whether they were used correctly.
* **Xxcha hero**: fix it never being played.
* **Yin hero Dannel**: use it every game. The usual play takes Styx: drop 1 infantry there with 3 infantry ready, and
  indoctrinate one of the neutral infantry into a mech. The other 2 infantry go to planets that score a point, block a
  point, or to neutral Fracture planets that still have a relic. With Imperial or Overrule it can take Mecatol Rex the
  same way (1 infantry, indoctrinate into a mech), and it can do both Styx and Mecatol. With Fleet Logistics it can
  score Imperial right after taking Mecatol, which is better still.
* **Sol hero Jace X**: use it whenever Sol needs it to score a public or status-phase secret (higher desperation).
  Otherwise use it at least to take Styx or Mecatol Rex (move everything in range, pick up the token with the hero,
  activate Styx or Mecatol); Mecatol mostly matters for Imperial. If someone else is about to win, move on that
  player's home system instead (pick up the token, strike), or take Mecatol from the Imperial holder when that sets
  them up to win and Sol can get there in time (hero, then Fleet Logistics).
* **Letnev hero Darktalon Treilla**: if someone looks likely to win this round and the hero is unused, play it as a
  stall.
* **Sardakk hero Sh'val**: it loses the cost of the ships, but play it when desperation is high: take Mecatol from a
  leader with Imperial, take Styx (especially if its holder will win), take planets for publics or secrets it would not
  otherwise score, take the home of a player who might win. The planner should consider a "with my hero" alternative
  for each tactical action that costs more but skips the space combat (only the ground combat must be won).

### Passive and indirect abilities (audit of the batch 1 log, 2026-11-16)

| Ability | Engine | In the games | AI |
| --- | --- | --- | --- |
| Naalu Telepathic | zero token gives initiative 0 | turn order not in the log | `InitiativeRace` ignores initiative for the zero holder |
| Sardakk Unrelenting | +1 on every combat roll | 7 of 7 Sardakk combat rolls list it; none on space cannon (correct) | combat odds include always-on modifiers |
| Letnev Armada | fleet supply +2 | no fleet-supply violations | `FleetLimit` counts it |
| Sol Versatile | 3 command tokens in the status phase | per-seat counts not in the log | `CommandTokenPolicy` counts it |
| Hacan Guild Ships | trades with non-neighbours | 119 of 180 transactions involve Hacan | `TradeLegality` allows it |
| Hacan Masters of Trade | **no engine code**: the Trade "Spend A Strategy Token" button spends a token | the AI uses the free replenish button instead | correct; only humans are affected |
| Hacan Arbiters | action cards tradable | **0 action-card trades in 12 games** | the card-sale proposer ran 571 to 930 times a game and never made a deal |
| Winnu Blood Ties | no custodians payment | Mecatol taken without paying | custodians cost 0 |
| Winnu Reclamation | PDS and space dock placed on Mecatol | message not in the log | `Reclamation.captureBonus` |
| Xxcha Peace Accords | Diplomacy claims a neighbouring planet | 22 claims | `PeaceAccords` |
| Nekro Propagation / Singularity | tokens instead of tech; steals tech on kills | 21 Technology follows with tokens, 36 tech steals | handled |

Open issues: the AI never trades action cards (Arbiters, `ai/trade/CardSales.java`); the engine ignores Masters of
Trade on the "Spend A Strategy Token" button (`ButtonHelperSCs.followTrade`); Peace Accords buttons show the source
planet's owner emoji (`ButtonHelperAbilities` around line 2595). The tracker should count AI reasons as use so these
abilities stop reading 0/8.

### Changes after batch 1 (2026-11-16)

* **Xxcha Xxekir Grom (TE)** had no rule. `XxekirGromRules` plays it before passing, or earlier when the 4 planets it
  readies are worth 4 TG or more; it places mechs (PDS first when a structure objective is near) on the exhausted
  planets worth most to ready.
* **Yin Dannel**: `DannelPlan` now values Styx's point and an unclaimed relic on every Fracture planet, counts neutral
  defenders, plans an Indoctrination conversion (mech when one is in reinforcements) on its point landings, and makes
  Mecatol Rex worth a point with Imperial or Overrule (more with Fleet Logistics). Keeping the hero costs a quarter
  point, and nothing when a landing takes a point.
* **Sol Jace X** plays whenever the freed tokens bring a point (objectives, custodians or Imperial on Mecatol, Styx,
  or denying a winning rival), not only in the final round.
* **Letnev Darktalon Treilla** is played before passing as a stall while a rival can win this round.
* **Sardakk Sh'val**: before each tactical action the planner ranks the plans again with the space combat skipped
  (`SkippedSpaceCombat`: only the ground combat counts, every ship in the system is lost). The hero plan is taken
  when it is worth a point after those ships and beats the plan without the hero by half a point.

## Batch 2 (2026-11-17, hero changes, not yet committed)

8 games with Sardakk, Xxcha, Yin, Sol, Letnev and Naalu at every table. All ended on victory points, no stalls. Leader
10.0 VP, table total 45.4, final round 6.1 on average. Wins: Yin 4, Letnev, Naalu, Sardakk and Sol 1 each, Xxcha 0.

| Hero | Batch 1 | Batch 2 | Note |
| --- | --- | --- | --- |
| Xxcha Xxekir Grom (TE) | 0 of 7 ready | 7/8, round 4.7 | mostly before passing in round 5 |
| Yin Dannel | 3/8 | 6/8, round 4.5 | landed on Styx in 2 games; Indoctrination on a hero landing rarely seen |
| Sol Jace X | 2/8, round 5.5 | 3/8, round 5.0 | offered in only 4 games |
| Letnev Darktalon Treilla | 4/8, round 4.8 | 5/8, round 5.4 | 1 played as the new stall |
| Sardakk Sh'val | 0/8 | 3/8, round 6.0 | all 3 through the skip-the-space-combat plan |
| Naalu The Oracle | 7/8 | 8/8, round 3.8 | unchanged, control |

Still open: Yin is not yet "every game" (2 games without it); Styx was the target in only 2 of 6 plays. Sol is
offered the hero in only half its games. Sardakk plays it late (rounds 5 to 7). Xxcha is still the weakest faction.

### Review notes on batch 2 (developer, 2026-11-17)

* **Xxcha Xxekir Grom**: treat it like Diplomacy. It readies planets to spend, so playing it just before passing is
  wasted unless those planets pay for a public objective. Changed: before passing only when the readied planets unlock
  a spend objective; mid-round when they unlock one, or when spending is still ahead and they are worth 4 TG or more.
* **Peace Accords** buttons showed the emoji of the Xxcha planet they started from; they now show the target planet's
  owner (neutral when unowned).

## Batch 3 (2026-11-20, AI PR head ef0b816624, 4 games)

Same six factions. All ended on victory points, no stalls. Leader 10.0 VP, table total 44.8, final round 5.5. Wins:
Naalu, Sol, Xxcha and Yin 1 each.

Xxekir Grom with the Diplomacy-style timing: played in 1 of 4 games (round 5, mid-round, "ready planets worth 4 for
its next actions"), down from 7/8. It was never played before passing, so no game had 4 exhausted planets that
unlocked a spend objective. Other heroes: Naalu 4/4, Yin 3/4, Letnev 3/4, Sol 2/4, Sardakk 1/4.

### Review notes on batch 3 (developer, 2026-11-20) and changes

Xxcha should plan around its spending: Archon's Gift spends the higher of a planet's resources or influence, and the
agent and hero ready planets. Spend everything on Leadership (keeping what a Technology follow needs), then ready the
planets to build; spend on the Technology primary so there is time to ready for a later build or objective; follow
Construction for docks (two at home, forward docks) so there are bigger builds and more spends to ready.

Changed:
* `ExpectedReadying` counts Ggrocuto Rinn (1 planet) and Xxekir Grom (4) next to Diplomacy (2) while the seat has not
  passed. Reserves for spend objectives, custodians and builds are kept only from what will be ready again, so the
  Leadership purchase and every reserved payment spend planets the seat will ready (Technology follow kept unspent).
* A second Technology research paid with planets that come back counts as cheap, like idle trade goods.
* Space docks: dock sites, the second home dock and "rich" planets use the pooled value (Archon's Gift counts
  influence), and the round's spending counts the readied planets again, so Xxcha wants a second home dock sooner.

Not changed: forward docks still go one per system planet; whether Xxcha follows Construction more is not yet
measured (the self-play log has no per-seat dock count).

## Batch 4 (2026-11-20, ef0b816624 + uncommitted spending changes, 4 games)

Same six factions as batch 3 (Sardakk, Xxcha, Yin, Sol, Letnev, Naalu), after the Xxcha spending changes.

* Leader 10.0 VP, table total 44.8, average final round 6.0 (batch 3: 10.0 / 44.8 / 5.5). All 4 games ended on points.
* Wins: Yin 2, Xxcha 1, Naalu 1 (batch 3: Naalu, Sol, Xxcha, Yin 1 each).
* Xxekir Grom: played in 2 of 4 (batch 3: 1), both in round 5 "to ready the planets it spends on a public objective";
  it was offered in 3 games.
* Ggrocuto Rinn: 22 uses over 4 games, every round 1 to 4 in all games (batch 3: 19 uses).
* Other heroes: Letnev 4/4, Naalu 4/4, Yin 3/4, Sol 2/4 (offered in 2), Sardakk 1/4 (round 6).
* Not measured: Leadership tokens bought and Construction follows per seat; the log has no per-seat count for either.
  Four games is too few to tell a win-rate change from noise.

### Batch 4 correction (found 2026-11-20)

Ggrocuto Rinn never readied a planet in batches 1 to 4: the engine posts its planet buttons with Xxcha's owner prefix
and `XxchaAgentRules` only matched unowned buttons, so each "use" exhausted the agent and readied nothing. The tracker
counted the exhaust as a use. Peace Accords, Orbital Drop and Mindsieve were in fact used; the tracker missed their
camelCase button ids. Both are fixed, and the tracker now prints a follow-through count for the agent.

## Batch 5 (2026-11-21, 4b2988f8f6 + agent, tracker, Quash, Munitions Reserves fixes, 4 games)

Same six factions. First batch with the new SPENDING counts (averages per game).

* Leader 10.5 VP, table total 44.8, average final round 6.5. All 4 games ended on points.
* Wins: Sol 2, Letnev 2. Average VP: Sol 8.75, Letnev 8.5, Naalu 8.25, Yin 7.75, Sardakk 6.75, Xxcha 4.75
  (Xxcha 6.75 in batches 3 and 4, 7.0 in batch 2). Xxcha finished last or second to last in every game.
* Ggrocuto Rinn: 24 of 24 uses readied a planet (6 per game); 5 per game of those planets were spent again the same
  round.
* Xxcha Leadership: 5 secondaries per game buying 11.5 tokens for 28 influence (35 pooled), against 2.5 to 3.5
  secondaries and 6.5 to 8 tokens for every other faction.
* Xxcha Construction: 2.3 follows per game (others 0.5 to 1.5); docks 0.3 home and 1.5 forward per game.
* Xxekir Grom: never played (unlocked in 3 games, offered in 2).
* Now visible: Peace Accords 11 presses in 4/4 games, Orbital Drop 4/4, Munitions Reserves 2/4 (0/4 before the fix),
  Quash 1/4.

## A/B: XXCHA_READYING_SPEND (2026-11-22, 48 games, 24 pairs, base seed 20261121)

Same six factions, paired seating. Both arms have the hero rework (XxekirGromPlan), the partial build reserve, the
approved strategy values (Instinct Training 2.0, Nullification Field 2.5, Mindsieve priority, Sh'val keep
max(0, 4 - round)) and the Political Secret / Quash fixes. The flag gates only the readying spend planning
(leader readies in ExpectedReadying, the readied branch in ScoringReserve, the Technology-follow keep in
TokenPurchase, readiedAgainCovers, and pooled dock values in StructurePolicy).

* Leader 10.1 VP, table total 45.4, average final round 6.2. All 48 games ended on points.
* Table: treatment minus control -0.35 +- 0.18 VP (t -1.93); treatment win share 0.46.
* Xxcha: +0.38 +- 0.52 VP (t 0.72), win share 0.21 as treatment vs 0.29 as control. Not significant.
* Other factions as treatment: Letnev -0.88 (t -2.18), Sardakk -0.88 (t -1.63), Sol -0.63, Yin -0.42, Naalu +0.29.
  The parts that reach every faction (Technology-follow keep, Diplomacy readied branch) look harmful.
* Wins over both arms: Sol 12, Xxcha 12, Yin 7, Letnev 6, Naalu 6, Sardakk 4. Average VP: Letnev 7.90, Xxcha 7.85,
  Sol 7.77, Yin 7.50, Naalu 7.48, Sardakk 6.94 (Xxcha 4.75 in batch 5, before the hero rework).
* Xxekir Grom played in 36/48 games (first use round 4.6); Sh'val 23/48 (round 4.7); Mindsieve used in 48/48;
  Instinct Training researched once, Nullification Field never.
* The flag was then split into `TECH_FOLLOW_KEEP`, `DIPLOMACY_READIED_PLANNING` and `XXCHA_LEADER_READIES` for one-at-a-time A/B tests.

## A/B: TECH_FOLLOW_KEEP (2026-11-23, 48 games, 24 pairs, base seed 20261121)

Leadership purchases keep the 4-resource Technology follow payable (every faction). Other two split flags on.

* Leader 10.1 VP, table total 45.4, average final round 6.2. All 48 games ended on points.
* Table: treatment minus control -0.12 +- 0.17 VP (t -0.70); treatment win share 0.44. Neutral.
* Per faction (treatment minus control): Naalu +0.46, Xxcha +0.25, Letnev +0.21, Sol -0.38, Yin -0.50,
  Sardakk -0.75 (t -1.96). None clear.
* Wins over both arms: Letnev 11, Yin 11, Sol 8, Naalu 6, Sardakk 6, Xxcha 5.

## A/B: DIPLOMACY_READIED_PLANNING (2026-11-23, 48 games, 24 pairs, base seed 20261121)

The Diplomacy holder plans spending (scoring reserve, Technology follow, structures) on the planets its own
Diplomacy will ready. Other two split flags on.

* Leader 10.2 VP, table total 46.2, average final round 6.3. All 48 games ended on points.
* Table: treatment minus control -0.10 +- 0.17 VP (t -0.57); treatment win share 0.46. Neutral.
* Per faction (treatment minus control): Sardakk +0.54, Naalu +0.38, Xxcha +0.25, Letnev -0.21, Yin -0.58,
  Sol -0.96 (t -1.94). None clear.
* Wins over both arms: Letnev 11, Yin 11, Xxcha 10, Naalu 7, Sol 7, Sardakk 2.

## A/B: XXCHA_LEADER_READIES (2026-11-24, 48 games, 24 pairs, base seed 20261121)

Xxcha plans spending on the planets its agent and Xxekir Grom will ready, and pools resources and influence by
its unlocks. Only Xxcha seats are affected. Other two split flags on.

* Leader 10.1 VP, table total 45.8, average final round 6.3. All 48 games ended on points.
* Table: treatment minus control +0.18 +- 0.20 VP (t 0.93); treatment win share 0.52. Neutral.
* Per faction (treatment minus control): Naalu +0.67, Letnev +0.58, Yin +0.13, Xxcha +0.08, Sardakk 0.00,
  Sol -0.38. None clear.
* Wins over both arms: Letnev 14, Naalu 10, Yin 10, Sol 6, Xxcha 5, Sardakk 3.

### Split summary

None of the three flags moves the table on its own (-0.12, -0.10, +0.18; all |t| < 1). The combined run's
-0.35 (t -1.93) reads as noise at this sample size, or as a small interaction. All three are kept on.
