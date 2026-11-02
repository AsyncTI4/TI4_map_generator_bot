# TI4 Rules Reference for Agents

The official Twilight Imperium 4th Edition rules (base game, Prophecy of Kings, Thunder's Edge),
split into small markdown files so an agent can grep for a term and read one topic at a time.
Use it to check what the rules say before implementing or changing game logic, writing player-facing
text, or judging whether a bug report describes a real rules error.

This is the **published** game. The bot also supports homebrew content and game modes (Franken,
Absol, Fog of War, Discordant Stars, ...) whose rules are not here. For bot vocabulary see
[GLOSSARY.md](../../GLOSSARY.md); for bot-specific component rules see
[COMPONENT_RULES.md](../../COMPONENT_RULES.md). Card text itself is not repeated here; it lives in
the bot's data files under `src/main/resources/data/`.

## Layout

| Path | Contents |
| --- | --- |
| [reference/](reference/) | One file per Living Rules Reference (LRR) topic: the numbered LRR 2.0 rule text, then tirules2.com clarification notes, then related topics. Also the Thunder's Edge topics, which have notes but no LRR section yet. |
| [cards/](cards/) | Rulings on specific generic cards, one file per card type (action cards, agendas, objectives, relics, ...). |
| [factions/](factions/) | Rulings on each faction's abilities, units, leaders, promissory notes and breakthrough. Files are named by the bot's faction alias (`ghost`, `cabal`, `naaz`, ...). |
| [setup.md](setup.md) | Complete game setup from the LRR, with Thunder's Edge changes. |
| [thunders-edge.md](thunders-edge.md) | Rules introduced by the Thunder's Edge rulebook (expedition, breakthroughs, The Fracture, neutral units, space stations, entropic scars, coexist, Alliance variant) and its Clarifications page. |
| [designer-faq.md](designer-faq.md) | The PoK designer FAQ and errata (v3.1.1, January 2023): rulings that are not in the LRR. |

## How to look something up

1. Grep the whole folder for the term, e.g. `rg -i "sustain damage" docs/rules`. Card, leader and
   technology names are in italics in the notes (`*Sarween Tools*`), so a plain name search finds
   them.
2. Read the topic file you land on. Rule numbers (`41.2`, `41.2a`) match the official LRR, so cite
   them as "LRR 41.2" in code reviews and PR text.
3. For a named card or faction component, also check `cards/<type>.md` or
   `factions/<alias>.md`, and search [designer-faq.md](designer-faq.md) for the card name.
4. Follow the "Related Topics" links at the end of each reference file for neighbouring rules.

## Precedence

When sources disagree, apply them in this order (highest first):

1. **Card text** over the rules (LRR 1.2). If both can be followed, follow both.
2. **Thunder's Edge rulebook clarifications** ([thunders-edge.md](thunders-edge.md#clarifications-rulebook-page-16)), the newest official source.
3. **Designer FAQ** ([designer-faq.md](designer-faq.md)) and notes marked **[Official ruling]**.
4. **LRR 2.0 text** (the numbered rules in `reference/`).
5. **tirules2 notes** (Roman-numeral notes): community clarifications of the rules above. Usually
   right, but they are interpretations; where one predates a later ruling it may be stale.

Known places where a later ruling overrides the LRR text (also flagged at the top of each file):

| Topic | Change |
| --- | --- |
| [Gravity rift](reference/gravity-rift.md) | The +1 move bonus applies only once per ship (Thunder's Edge). Overrides 41.3 and note VI. |
| [Nebula](reference/nebula.md) | A nebula need not be the active system to move into; units just cannot move through it (Designer FAQ). |
| [Anomalies](reference/anomalies.md) | Anomaly movement rules apply to all moving units, not only ships (Designer FAQ). |
| [Warfare](reference/warfare.md), [Construction](reference/construction.md) | Thunder's Edge replaces both strategy cards with revised versions; the LRR text describes the old cards. |
| Movement abilities | Units may be transported out of systems containing your command tokens during movement abilities, not only tactical actions (Designer FAQ, General). |
| "Replace" | Treated as "remove and place" when reinforcements are empty (Designer FAQ, General). |

## Sources

- **Living Rules Reference 2.0** (Fantasy Flight Games, 2020-09-22). Rule text in `reference/` and
  the setup steps in [setup.md](setup.md).
- **tirules2.com** (crawled 2026-10-11): the LRR text plus community notes and **[Official ruling]**
  items. Each file links its source page.
- **Thunder's Edge rulebook** (Fantasy Flight Games): rules pages and Clarifications only; lore,
  component lists and the map appendix are left out.
- **The Unofficially Official FAQ and Errata** for PoK, v3.1.1 (2023-01-31).

Twilight Imperium is © Fantasy Flight Games. These files are a snapshot; when the LRR or a new
FAQ changes a rule, update the affected file and add a row to the precedence table above.

## Index

### Rules reference topics

Numbers are LRR section numbers; `TE` marks Thunder's Edge topics without an LRR section.

| # | Topic |
| --- | --- |
| 1 | [Abilities](reference/abilities.md) |
| 2 | [Action Cards](reference/action-cards.md) |
| 3 | [Action Phase](reference/action-phase.md) |
| 4 | [Active Player](reference/active-player.md) |
| 5 | [Active System](reference/active-system.md) |
| 6 | [Adjacency](reference/adjacency.md) |
| 7 | [Agenda Card](reference/agenda-card.md) |
| 8 | [Agenda Phase](reference/agenda-phase.md) |
| 9 | [Anomalies](reference/anomalies.md) |
| 10 | [Anti–Fighter Barrage (Unit Ability)](reference/anti-fighter-barrage.md) |
| 11 | [Asteroid Field](reference/asteroid-field.md) |
| 12 | [Attach](reference/attach.md) |
| 13 | [Attacker](reference/attacker.md) |
| 14 | [Blockaded](reference/blockaded.md) |
| 15 | [Bombardment (Unit Ability)](reference/bombardment.md) |
| 16 | [Capacity (Attribute)](reference/capacity.md) |
| 17 | [Capture](reference/capture.md) |
| 18 | [Combat (Attribute)](reference/combat.md) |
| 19 | [Command Sheet](reference/command-sheet.md) |
| 20 | [Command Tokens](reference/command-tokens.md) |
| 21 | [Commodities](reference/commodities.md) |
| 22 | [Component Action](reference/component-action.md) |
| 23 | [Component Limitations](reference/component-limitations.md) |
| 24 | [Construction (Strategy Card)](reference/construction.md) |
| 25 | [Control](reference/control.md) |
| 26 | [Cost (Attribute)](reference/cost.md) |
| 27 | [Custodians Token](reference/custodians-token.md) |
| 28 | [Deals](reference/deals.md) |
| 29 | [Defender](reference/defender.md) |
| 30 | [Deploy](reference/deploy.md) |
| 31 | [Destroyed](reference/destroyed.md) |
| 32 | [Diplomacy (Strategy Card)](reference/diplomacy.md) |
| 33 | [Elimination](reference/elimination.md) |
| 34 | [Exhausted](reference/exhausted.md) |
| 35 | [Exploration](reference/exploration.md) |
| 36 | [Fighter Tokens](reference/fighter-tokens.md) |
| 37 | [Fleet Pool](reference/fleet-pool.md) |
| 38 | [Frontier Tokens](reference/frontier-tokens.md) |
| 39 | [Game Board](reference/game-board.md) |
| 40 | [Game Round](reference/game-round.md) |
| 41 | [Gravity Rift](reference/gravity-rift.md) |
| 42 | [Ground Combat](reference/ground-combat.md) |
| 43 | [Ground Forces](reference/ground-forces.md) |
| 44 | [Hyperlanes](reference/hyperlanes.md) |
| 45 | [Imperial (Strategy Card)](reference/imperial.md) |
| 46 | [Infantry Tokens](reference/infantry-tokens.md) |
| 47 | [Influence](reference/influence.md) |
| 48 | [Initiative Order](reference/initiative-order.md) |
| 49 | [Invasion](reference/invasion.md) |
| 50 | [Leader Sheet](reference/leader-sheet.md) |
| 51 | [Leaders](reference/leaders.md) |
| 52 | [Leadership (Strategy Card)](reference/leadership.md) |
| 53 | [Legendary Planets](reference/legendary-planets.md) |
| 54 | [Mecatol Rex](reference/mecatol-rex.md) |
| 55 | [Mechs](reference/mechs.md) |
| 56 | [Modifiers](reference/modifiers.md) |
| 57 | [Move (Attribute)](reference/move.md) |
| 58 | [Movement](reference/movement.md) |
| 59 | [Nebula](reference/nebula.md) |
| 60 | [Neighbors](reference/neighbors.md) |
| 61 | [Objective Cards](reference/objective-cards.md) |
| 62 | [Opponent](reference/opponent.md) |
| 63 | [PDS](reference/pds.md) |
| 64 | [Planets](reference/planets.md) |
| 65 | [Planetary Shield (Unit Ability)](reference/planetary-shield.md) |
| 66 | [Politics (Strategy Card)](reference/politics.md) |
| 67 | [Producing Units](reference/producing-units.md) |
| 68 | [Production (Unit Ability)](reference/production.md) |
| 69 | [Promissory Notes](reference/promissory-notes.md) |
| 70 | [Purge](reference/purge.md) |
| 71 | [Readied](reference/readied.md) |
| 72 | [Reinforcements](reference/reinforcements.md) |
| 73 | [Relics](reference/relics.md) |
| 74 | [Rerolls](reference/rerolls.md) |
| 75 | [Resources](reference/resources.md) |
| 76 | [Ships](reference/ships.md) |
| 77 | [Space Cannon (Unit Ability)](reference/space-cannon.md) |
| 78 | [Space Combat](reference/space-combat.md) |
| 79 | [Space Dock](reference/space-dock.md) |
| 80 | [Speaker](reference/speaker.md) |
| 81 | [Status Phase](reference/status-phase.md) |
| 82 | [Strategic Action](reference/strategic-action.md) |
| 83 | [Strategy Card](reference/strategy-card.md) |
| 84 | [Strategy Phase](reference/strategy-phase.md) |
| 85 | [Structures](reference/structures.md) |
| 86 | [Supernova](reference/supernova.md) |
| 87 | [Sustain Damage](reference/sustain-damage.md) |
| 88 | [System Tiles](reference/system-tiles.md) |
| 89 | [Tactical Action](reference/tactical-action.md) |
| 90 | [Technology](reference/technology.md) |
| 91 | [Technology (Strategy Card)](reference/technology-sc.md) |
| 92 | [Trade (Strategy Card)](reference/trade.md) |
| 93 | [Trade Goods](reference/trade-goods.md) |
| 94 | [Transactions](reference/transactions.md) |
| 95 | [Transport](reference/transport.md) |
| 96 | [Units](reference/units.md) |
| 97 | [Unit Upgrades](reference/unit-upgrades.md) |
| 98 | [Victory Points](reference/victory-points.md) |
| 99 | [Warfare (Strategy Card)](reference/warfare.md) |
| 100 | [Wormhole Nexus](reference/wormhole-nexus.md) |
| 101 | [Wormholes](reference/wormholes.md) |
| TE | [Breakthroughs](reference/breakthroughs.md) |
| TE | [Coexistence](reference/coexistence.md) |
| TE | [Entropic Scars](reference/entropic-scars.md) |
| TE | [Expeditions](reference/expedition.md) |
| TE | [Neutral Units](reference/neutral-units.md) |
| TE | [Space Stations](reference/space-stations.md) |
| TE | [Synergy](reference/synergy.md) |
| TE | [The Fracture](reference/fracture.md) |


### Card rulings

[Action Cards](cards/action-cards.md), [Agendas](cards/agendas.md), [Events](cards/events.md), [Exploration Cards](cards/exploration-cards.md), [Legendary Planets](cards/legendary-planets.md), [Objectives](cards/objectives.md), [Promissory Notes](cards/promissory-notes.md), [Relics](cards/relics.md), [Technology](cards/technology.md), [Units](cards/units.md)

### Faction rulings

`keleres.md` covers all three Keleres variants (`keleresm`, `keleresx`, `keleresa`); `firmament.md` also covers `obsidian`.

| Bot alias | Faction |
| --- | --- |
| `arborec` | [The Arborec](factions/arborec.md) |
| `argent` | [The Argent Flight](factions/argent.md) |
| `letnev` | [The Barony of Letnev](factions/letnev.md) |
| `saar` | [The Clan of Saar](factions/saar.md) |
| `keleres` | [Council Keleres](factions/keleres.md) |
| `crimson` | [The Crimson Rebellion](factions/crimson.md) |
| `deepwrought` | [The Deepwrought Scholarate](factions/deepwrought.md) |
| `muaat` | [The Embers of Muaat](factions/muaat.md) |
| `hacan` | [The Emirates of Hacan](factions/hacan.md) |
| `empyrean` | [The Empyrean](factions/empyrean.md) |
| `sol` | [The Federation of Sol](factions/sol.md) |
| `firmament` | [The Firmament / The Obsidian](factions/firmament.md) |
| `ghost` | [The Ghosts of Creuss](factions/ghost.md) |
| `l1z1x` | [The L1Z1X Mindnet](factions/l1z1x.md) |
| `bastion` | [Last Bastion](factions/bastion.md) |
| `mahact` | [The Mahact Gene–Sorcerers](factions/mahact.md) |
| `mentak` | [The Mentak Coalition](factions/mentak.md) |
| `naalu` | [The Naalu Collective](factions/naalu.md) |
| `naaz` | [The Naaz–Rokha Alliance](factions/naaz.md) |
| `nekro` | [The Nekro Virus](factions/nekro.md) |
| `nomad` | [The Nomad](factions/nomad.md) |
| `ralnel` | [The Ral Nel Consortium](factions/ralnel.md) |
| `sardakk` | [Sardakk N’orr](factions/sardakk.md) |
| `titans` | [The Titans of Ul](factions/titans.md) |
| `jolnar` | [The Universities of Jol–Nar](factions/jolnar.md) |
| `cabal` | [The Vuil’raith Cabal](factions/cabal.md) |
| `winnu` | [The Winnu](factions/winnu.md) |
| `xxcha` | [The Xxcha Kingdom](factions/xxcha.md) |
| `yin` | [The Yin Brotherhood](factions/yin.md) |
| `yssaril` | [The Yssaril Tribes](factions/yssaril.md) |
