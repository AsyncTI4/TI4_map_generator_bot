# AI players and relics: how each relic is used and what it does to the game

This report covers the AI PR ([#5846](https://github.com/AsyncTI4/TI4_map_generator_bot/pull/5846)) after the relic
work: relic fragments (values and trading) and every relic in the Prophecy of Kings and Thunder's Edge relic deck.
Values are in trade goods (TG), the AI's currency (a victory point is 8 in the planner and 6 to 12 in trades).
Numbers in the usage tables come from the AI's own self-play (`AiSelfPlayTest`, real button handlers, six AI seats,
32 whole games) with `-Dai.selfplay.relics=2`: every seat is dealt two relics through the bot's own relic draw when
the first action phase begins, so every relic is drawn 17 to 25 times. All 32 games ended on victory points, with no
bot warnings or errors.

## What changed

| Area | Before | Now |
| --- | --- | --- |
| Fragment value | 1 TG, 2 for the third of a kind, 3 each for the first two with _Destroy Heretical Works_ | **2 TG, 3 for the third of a kind** (a set of three is worth 7), and 2 more each for the first two with _Destroy Heretical Works_; exploring uses the same numbers |
| Giving a fragment | priced at infinity (it never gave one) | priced at what it loses: 2 for a spare fragment, 3 for one of a set, so it never sells a set it is about to purge |
| Fragment deals | accepted fragments it was offered, never asked for or offered any | proposes and answers fragment deals (below) |
| Purging fragments for a relic | purges 3 of one kind as a component action | unchanged (already there); it still keeps 2 back for _Destroy Heretical Works_ |
| Relics | the Enigmatic Device, and the Crown of Emphidia's exploration | a `RelicFlow` per relic action, plus rules for the reactive ones |

### Fragment trading

* **Value.** `FragmentValue` prices a holding as 2 per fragment, plus 1 for every relic it could purge (unknown
  fragments fill in), plus 2 for each of the first two fragments while it holds an unscored _Destroy Heretical
  Works_. Receiving or giving a fragment is the change in that number. In a trade a fragment that completes a set is
  worth 1 more, because the relic comes a turn sooner.
* **Why 3 TG.** The last fragment of a set is worth 3 + 1 to its buyer and a lone fragment costs 2 to its seller, so
  3 TG leaves both a trade good ahead. 2 TG is not enough for the seller, 4 is too much for the buyer. The AI accepts
  a human's offer when it is at least a trade good ahead (0.5 against a human, 0.3 against an AI) and counters
  otherwise.
* **What it starts** (`FragmentDeals`, on its own turn, at most 2 a round, one per partner): a fragment sold for 1 to
  4 TG, one bought for 1 to 4 TG, or a one-for-one swap of two different kinds with or without a TG.
* **How it builds them.** The transaction builder picks fragments by kind and amount (`Frags_<kind><n>`); counter
  offers keep the fragments they can cover.
* **Seen in 32 games:** 26 accepted fragment deals, 20 of them a fragment for 3 TG, 5 swaps.

## Relic by relic

"Drawn" is draws across the 32 games, "used" is a purge or an exhaust of an exhaust relic seen between ticks, and
"holders" is the average final victory points of the seats that held it (the table averaged 7.05; with 17 to 25 holders
per relic the spread of a few tenths of a point is noise). Passive relics have no use to count.

| Relic | Rules | How the AI uses it | Drawn / used | Holders |
| --- | --- | --- | --- | --- |
| **Relic fragments** | 3 of one kind (unknown counts as any) are purged by a component action for a relic | purges 3 instead of a weak tactical action or before passing; trades them (above) | n/a | n/a |
| **Dominus Orb** | purge before a tactical move: move from systems that hold your command tokens | **not used.** The planner never plans from a system with its own token, and the extra plans are not modelled | 17 / 0 | 6.59 |
| **Maw of Worlds** | start of the agenda phase: purge, exhaust all planets, gain any technology | purges in the status phase when the best technology it could research is worth at least 2 more than the votes it gives up (0.25 per influence); researches through the free-technology buttons | 17 / 17 (first in round 2.2) | 6.18 |
| **Scepter of Emelpar** | exhaust to spend a token from reinforcements instead of the strategy pool | follows a strategy card it wants to follow with the Scepter first: the bot then marks the card followed without charging a token. A ready Scepter also counts as a spare token when it decides whether it can follow. It readies every status phase | 22 / 109 (about one follow a round) | 7.00 |
| **Shard of the Throne** | +1 point for its holder; passes to whoever takes a legendary planet the holder controls or a planet in its home system | a planet that would hand it over is worth 8 more to the holder and 8 more to a seat that attacks it (and counts in the denial of the holder); the bot scores and unscores the point | 25 / n/a; changed hands 10 times | 7.12 |
| **Stellar Converter** | action: destroy a non-home, non-legendary, non-Mecatol planet next to a bombardment unit | destroys a rival's planet worth at least 4 (value + structures + ground forces, times 0.6, 0.9 against the leader), never with its own units on it | 18 / 10 (first in round 4.0) | 7.11 |
| **The Codex** | action: purge, take up to 3 action cards from the discard | retrieves up to 3 of the best discards by keep value; the net is what they add over the cards it would discard, and it stops when the next card is not 0.3 better than its worst | 20 / 20 (round 1.5) | 6.60 |
| **Crown of Emphidia** | exhaust after a tactical action to explore a planet you control; purge for a point in the status phase if you control the Tomb of Emphidia | exhausts it for the best exploration; purges for the point whenever the bot offers the button | 24 / 118 | 7.71 |
| **Crown of Thalnos** | reroll any dice in combat with +1; units that reroll and miss die | **not used.** The reroll is a multi-step unit selection with no model of which dice missed | 22 / 0 | 6.91 |
| **The Obsidian** | draw a secret objective; hold one extra secret | passive; the extra secret is handled by the bot | 23 / n/a | 7.22 |
| **The Prophet's Tears** | exhaust when researching to ignore a prerequisite or draw an action card | replaces the cheapest planet it would exhaust to skip a prerequisite, otherwise draws an action card when the hand has room | 23 / 50 | 6.96 |
| **Dynamis Core** | +2 commodity value; action: gain trade goods equal to it, then purge | purges for the trade goods less what +2 commodities would be worth over the next 3 rounds | 20 / 20 (round 1.2) | 7.65 |
| **JR-XS455-O** | action: exhaust, a player places a structure for 3 resources or gains a trade good | chooses itself and takes the trade good, before passing, every round | 17 / 88 (about one a round) | 6.76 |
| **Nano-Forge** | action: attach to a non-legendary, non-home planet: +2 resources, +2 influence, becomes legendary | attaches to its safest eligible planet (+2, +1.2 for the influence, +2 for the legendary, 0.6 of that if an enemy can reach it) | 20 / 20 (round 1.6) | 7.60 |
| **Neuraloop** | purge a relic when an objective is revealed to replace it | **not used.** Needs a view of the revealed objective and a relic to burn | 21 / 0 | 7.00 |
| **Circlet of the Void** | ignore anomalies when moving; action: exhaust to explore a frontier token in a system without others' ships | exhausts it for a frontier card (1.5) whenever such a token exists | 23 / 113 (about 5 per holder) | 7.74 |
| **Book of Latvinia** | on gain: research 2 technologies without prerequisites; action: purge, a point if you hold all four technology specialties, otherwise the speaker token | researches the two best technologies when it is drawn; purges for the point (8) only once it controls all four specialties, otherwise keeps it | 17 / 0 | 7.65 |
| **Heart of Ixth** | exhaust to add or subtract 1 from a die (the Ixthian Artifact roll, the Silver Flame) | **not used.** Its prompts are declined | 21 / 0 | 6.43 |
| **Lightrail Ordnance** | docks get Space Cannon 5 (x2), also against adjacent systems | passive; the bot applies it, the combat estimates do not know it | 20 / n/a | 7.85 |
| **Metali Void Armaments** | Anti-Fighter Barrage 6 (x3) in space combat | passive, as above | 22 / n/a | 8.05 |
| **Metali Void Shielding** | one non-fighter ship may sustain damage each time hits are produced | passive, as above | 25 / n/a | 6.88 |
| **The Quantumcore** | gain your breakthrough; synergy for all technology types | passive; the bot unlocks the breakthrough | 18 / n/a | 7.11 |
| **The Silver Flame** | action: roll a die, a point on a 10, otherwise purge your home system and all units in it and lose public objective scoring | **never played**: a 10% chance of a point against a 90% chance of ruin; it may be exchanged in a transaction but the AI declines relics | 23 / 0 | 7.26 |
| **The Triad** | counts as a planet for spending: 3 resources and 3 influence plus 1 per kind of fragment held | passive; the AI's payments see it as a planet (checked with `Wallet`) | 19 / n/a | 7.32 |
| **Enigmatic Device** | action: spend 6 resources, purge, research a technology | unchanged (already there) | 10 / 8 (first in round 3.1) | 7.90 |

### Reading the table

* The relics the AI uses every round are the exhaust relics: the **Circlet**, the **Crown of Emphidia**, **JR-XS455-O**,
  the **Scepter** and the **Prophet's Tears**. The **Scepter** is the most valuable of them: each follow it makes with
  it saves a strategy token (2 TG in the AI's currency), about one a round, which is why it is the one relic whose
  absence the strategy-token budget would notice.
* The one-shot action relics are all spent at the first good turn: the **Codex**, **Dynamis Core**, **Nano-Forge** and
  the **Maw of Worlds** every time they were held, the **Stellar Converter** in 10 of 18 games (it needs a rival
  planet worth destroying next to its bombardment), the **Book of Latvinia** not once in these 32 games (no seat held all four specialties; it scored its point twice in
  an earlier 16-game batch). These relics were dealt in round 1, which is why the first uses fall in rounds 1 to 2; drawn from the
  deck later they are used the same way.
* Holders' final points sit within about one point of the table average (7.05) for every relic, in both directions, so
  none of them decides a game on its own in the AI's hands. The ones with the largest gains are the ones whose use is
  indirect (Metali Void Armaments +0.99, Enigmatic Device +0.85, Lightrail +0.80); with 17 to 25 holders each, those are
  within noise.
* The **Shard** changed hands 10 times in 32 games, so the AI does attack the planets that carry it.

## Effect on the whole game

Two batches of 32 whole games each, same code, the second with two relics dealt to every seat:

| | No relics dealt | Two relics per seat |
| --- | --- | --- |
| Relics gained per game | 2.7 (found by exploration and fragments) | 15.5 |
| Average final round | 6.1 | 5.6 |
| Leader's points at the end | 10.2 | 10.2 |
| Table total at the end | 44.1 (7.35 each) | 42.3 (7.05 each) |
| Games that ended on points | 32 of 32 | 32 of 32 |
| Accepted fragment deals | 26 (20 at 3 TG, 4 swaps, 2 at 1 TG) | 26 (20 at 3 TG, 5 swaps, 1 at 1 TG) |

Twelve extra relics per game bring the end about half a round earlier and leave the leader on the same points, so the
table as a whole scores less. That fits what the relics do in the AI's hands: most of the extra value is economic
(trade goods, command tokens, action cards, a technology, a legendary planet) and arrives in rounds 1 to 3, which
speeds the leader up without adding points for the others. In ordinary games the table gets about 3 relics a game from
exploration and fragments (2.7 across six seats), and the effect of any one relic is small next to the objectives.

## What is not done, and why

* **Dominus Orb and Crown of Thalnos** need planner support (plans that start in a system with its own token; a model
  of which dice missed and which units die on a reroll). They are the two relics with the most to gain from it.
* **Heart of Ixth and Neuraloop** need a view of the roll or the objective they would change; the AI declines them.
* **Lightrail Ordnance, Metali Void Armaments and Metali Void Shielding** work in the engine for whoever holds them but
  the AI's combat estimates ignore them, so a holder under-rates its fleet and docks.
* **The Silver Flame** is a gamble the AI never takes. It could be sold or given away (relics are not tradable by the
  AI), or purged for **Neuraloop**, which needs a relic to burn and is the one relic the AI would never miss.

## Where to look

* Code: `ti4.ai.explore` (`RelicFlows`, one `*Flow` per relic action, `FragmentRules`, `MawOfWorldsRules`,
  `CrownOfEmphidiaRules`), `ti4.ai.trade` (`FragmentValue`, `FragmentDeals`), `ti4.ai.strategy` (`ScepterFollow`,
  `PrerequisiteSkips`), `ti4.ai.eval.ShardPrize`.
* Tests: `RelicFlowsTest`, `FragmentDealsTest`, `OfferResponderTest`, `StrategyCardRulesTest`, `ShardPrizeTest`, and
  `RelicSelfPlayTest` (every flow through the bot's real buttons).
* Re-running the study: `mvn -o test -Dtest=AiSelfPlayTest -Dai.selfplay.full=true -Dai.selfplay.games=32
  -Dai.selfplay.relics=2`.
