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
