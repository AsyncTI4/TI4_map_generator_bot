# Thunder's Edge Expansion Rules

Source: Twilight Imperium: Thunder's Edge rulebook (rules pages 8–13 and Clarifications, page 16).
Lore, component lists, board diagrams and the map appendix are left out. Setup changes are in
[setup.md](setup.md). For tirules2 notes on the same topics see the linked reference pages.

Thunder's Edge is the second expansion for TI4. It adds the Thunder's Edge expedition, faction
breakthroughs, The Fracture, six factions (including the Council Keleres, previously Codex-only),
galactic events, and the Twilight's Fall game mode (separate rulebook, not covered here).

## Thunder's Edge expedition

Reference: [expeditions](reference/expedition.md)

Thunder's Edge starts in play, off the board, expedition side (six "slices") faceup.

- At the **end of their turn**, a player may commit to the expedition: choose an unclaimed slice, pay
  its cost, and place one of their control tokens on it.
- Slice costs: spend 5 resources; spend 5 influence; spend 3 trade goods; discard 2 action cards;
  discard 1 unscored secret objective; exhaust 1 technology specialty planet.
- A claimed slice cannot be claimed by another player. A player may claim several slices over the
  game, a different one each time.
- The **first** time a player claims a slice, they gain their faction's breakthrough.
- When the last slice is claimed the expedition is complete. The player who claimed the final slice
  flips Thunder's Edge to its planet side and places it in a system that has no planet, no
  supernova and no printed wormhole, and not in The Fracture. Then the player with the most control
  tokens on the expedition places that many infantry on Thunder's Edge; on a tie, the final-slice
  player chooses which tied player does so.
- If Thunder's Edge is placed in a system with a frontier token, the token stays and can still be
  explored.

## Breakthroughs and synergy

Reference: [breakthroughs](reference/breakthroughs.md), [synergy](reference/synergy.md)

- A breakthrough is a faction-specific card, most often gained through the expedition. It gives a
  unique ability and grants **synergy** between two technology colors.
- **Synergy**: a technology or technology specialty of one linked color may count as the other color
  when researching technology (prerequisites) and for technology-related objectives. Each counts as
  one color or the other at any given time, never both.
- **Breakthrough roll**: whenever a player gains their breakthrough, from any source, and The Fracture
  is not in play, they roll one die. On a 1 or a 10, they put The Fracture into play.

## The Fracture

Reference: [the fracture](reference/fracture.md), [neutral units](reference/neutral-units.md)

- The Fracture is three system tiles not connected to the rest of the galaxy. The player who brings
  it into play places it along an edge of the board (position depends on player count: three-player
  vs four-or-more layouts) and places the neutral units shown on the tile backs.
- **Ingress tokens**:
  - Brought in by a breakthrough roll: for each synergy color on the roller's breakthrough, they
    choose three systems containing planets with that technology specialty, if able, and place an
    ingress token in each. At most one ingress per system and at most six placed this way.
  - Brought in by another ability, or by a breakthrough without synergy (Nekro): the player chooses
    four planets in different systems with different technology specialties and places an ingress in
    each planet's system. If fewer than four specialties are in play, the rest are not placed.
  - Then place an ingress token in the Thunder's Edge system if it is in play, otherwise in the
    Mecatol Rex system if the expedition is not complete.
- **Adjacency**: Fracture systems are not adjacent to the board systems they touch, and each is on
  the edge of the board. A system with an ingress is adjacent to every Fracture system containing an
  egress. Ingress systems are not adjacent to each other; egress systems are not adjacent to each
  other.
- Every Fracture planet has a relic icon. Styx is also a legendary planet; its ability card ("A Song
  Like Marrow") gains its holder 1 victory point when gained and loses 1 when lost.

## Relics

Reference: [relics](reference/relics.md)

When a player gains a planet card with a relic icon, or an effect tells them to draw a relic, they
draw the top relic and place it faceup in their play area.

## Legendary planets

Reference: [legendary planets](reference/legendary-planets.md)

When a player takes control of a legendary planet they also take its legendary planet ability card.
An exhausted legendary ability card stays exhausted when control changes.

## Neutral units

Reference: [neutral units](reference/neutral-units.md)

- Non-player forces that fight a player who moves units into their planet or space area.
- Stats come from the neutral unit reference card. Rolls for neutral units are made by any player
  other than the active player.
- Hits are assigned in the order on the reference card, units lower on the card first (destroyers
  take hits before cruisers).
- Neutral units always use their unit abilities when able (sustain damage, space cannon, ...) and no
  other abilities. They do not take turns or perform other game functions unless a rule or ability
  says so.
- They count as another player's units for abilities and game effects, but there is no "neutral
  player". If a decision must be made for them, the speaker makes it.

Neutral unit reference (cost not applicable):

| Unit | Combat | Move | Capacity | Abilities |
| --- | --- | --- | --- | --- |
| Flagship | 7 | 1 | 3 | Sustain Damage |
| War Sun | 3 | 2 | 6 | Sustain Damage, Bombardment 3 (x3) |
| Dreadnought | 5 | 2 | 1 | Sustain Damage, Bombardment 5 |
| Carrier | 9 | 2 | 6 | |
| Cruiser | 6 | 3 | 1 | |
| Destroyer | 8 | 2 | | Anti-Fighter Barrage 6 (x3) |
| Fighter | 8 | 2 | | |
| PDS | | | | Planetary Shield, Space Cannon 6 |
| Mech | 6 | | | Sustain Damage |
| Infantry | 8 | | | |
| Space Dock | | | | |

The table follows the card's order, but the bottom of the card is laid out in two columns
(fighter and PDS on the left; mech, infantry and space dock on the right), so the exact priority
among those units is not clear from the rulebook. Ships: hits go to units lower in the table first.

## Purge

If an ability instructs a player to purge a component, it is removed from the game and returned to
the box.

## Space stations

Reference: [space stations](reference/space-stations.md)

- A player gains control of a space station (from the deck or from another player) when they are the
  only player with ships in its system. They keep control when those ships leave.
- Each space station has a planet card: gained exhausted, and spent and readied like a planet.
- Structures and ground forces cannot be placed on or committed to space stations. They do not count
  as planets for voting, objectives, or controlling a home system.
- Each controlled space station raises its controller's commodity value by 1 (commerce tokens mark
  this).
- Players who each control a space station can transact even if they are not neighbors.
- A player may exhaust a space station at any time to convert their commodities to trade goods.
- With PoK, a system with a space station and no planets gets a frontier token during setup.

## Entropic scar (anomaly)

Reference: [entropic scars](reference/entropic-scars.md)

- Unit abilities (Sustain Damage, Production, Planetary Shield, Space Cannon, Bombardment, Deploy,
  Anti-Fighter Barrage) cannot be used by or against units inside an entropic scar. Text abilities are
  unaffected.
- Wormholes that would be placed in an entropic scar are discarded instead.
- At the start of the status phase, a player with ships in an entropic scar may spend a token from
  their strategy pool to gain one of their faction technologies.

## Dual planet traits and dual specialties

- A planet with two traits has both. When exploring it (PoK), the explorer draws from the deck of
  either trait, not both.
- A planet with two technology specialties can be exhausted when researching to satisfy either or
  both prerequisites at once.

## Coexist

Reference: [coexistence](reference/coexistence.md)

- Coexisting units on a planet do not trigger combat with the other player's units there.
- The player whose units triggered coexistence does not gain or keep control of the planet; if they
  controlled it, the player they now coexist with gains control of it, exhausted.
- Coexisting structures are always blockaded.
- Units the coexisting player later places, produces or moves onto that planet also coexist.
- If a player's coexisting units become the only units on the planet, that player gains control.
- Coexisting units can be bombarded. A player bombarding the planet chooses which bombardment units
  target which other player's units and rolls against each player separately.
- Units committed to the planet may start combat against coexisting units, including units already
  coexisting with them. The active player may choose not to engage them. After winning combat
  against the planet's owner, they may optionally start separate combats against each coexisting
  player.
- A coexisting player can end coexistence by activating the system and committing the coexisting
  units against the planet. Winning that ground combat gains control as normal.
- For scoring objectives only, a coexisting player counts as controlling the planet.
- Several players may coexist on one planet.

## Hyperlanes

Reference: [hyperlanes](reference/hyperlanes.md)

Used with five players, or with four players and PoK. Each contiguous line of hyperlane tiles is a
hyperlane; systems it connects are adjacent for all purposes. Hyperlane tiles are not systems: they
hold no units and cannot be targeted. Hyperlane setups do not give five-player position trade goods.

## Reference cards, galactic events, diplomacy token

- **Faction reference cards** summarize each faction (including PoK content). Used in Twilight's
  Fall setup and by galactic events that pick random factions.
- **Alliance reference cards** are for the PoK "Alliance" promissory note, the Alliance variant, some
  galactic events and the Yin breakthrough. When gained other than through an "Alliance" promissory
  note, the gaining player places it in their play area and can use its ability.
- **Galactic events** are optionally chosen at setup and change the parameters of play; each card
  carries its own rules. Advent of the War Sun: at the end of setup every player except Muaat gains
  the War Sun unit upgrade; Muaat purges their faction promissory note and places 1 extra War Sun in
  their home system.
- **Diplomacy token**: a player aid for simple uses of the Diplomacy primary. It stands for one
  command token from each player, cannot push a player over the command token component limit, and
  should not be used with abilities that remove command tokens.

## Alliance game variant

Teams of two, with four, six or eight players.

- **Setup**: each player is assigned an ally and takes their ally's Alliance reference card. The
  Mahact player's ally places a command token from reinforcements in Mahact's fleet pool. With PoK,
  each player purges their "Alliance" promissory note and flips their commander to its unlocked side.
- **Transactions**: commodities exchanged with your ally do not become trade goods. You may receive
  your ally's promissory notes but cannot resolve them.
- **Movement and control**: your ships may move through and into systems with your ally's ships
  without space combat. Your ground forces may land on your ally's planets without ground combat and
  without gaining control. When an effect lets a player redistribute command tokens, allies may also
  swap planet cards if the receiver has a ground force or structure there; the swap keeps
  ready/exhausted state and does not trigger "gain control" abilities. When your ally activates a
  system, you may take a tactical action in the same system at the same time, spending a tactic token
  as normal. With your ally's permission you may transport, support and commit their fighters and
  ground forces.
- **Combat and unit abilities**: allies with units present roll together; hits against an alliance
  are assigned jointly, and if the allies disagree the active player assigns them. Hits an opponent
  assigns against an allied pair can go to either ally's units in any mix.
- **Abilities**: your ally's units count as neither yours nor another player's. Abilities that
  trigger on activating a system with another player's units, planets or command tokens do not
  trigger between allies. Your unit abilities do not affect your ally. Agent abilities can target your
  ally. Your ally's planets count as yours for resolving abilities, but you cannot exhaust them and
  they do not count for objectives or the Imperial point.
- **Elimination**: a player cannot be eliminated while their ally controls a planet.
- **Winning**: use the 14-point track. An alliance wins when one player has 14 points and their ally
  has at least 10.

## Clarifications (rulebook page 16)

| Topic | Ruling |
| --- | --- |
| Space stations (tiles) | A system with a space station and no planets gets a frontier token during setup when playing with PoK. |
| Gravity rift | A gravity rift contributes its move bonus to a ship only once per ship. **Change from earlier rulings** (overrides LRR 41.3 and tirules2 note VI). |
| Thunder's Edge (planet) | Placed in a system with a frontier token: keep the token; it can still be explored. |
| Warfare (strategy card) | If the tactical action from the Warfare primary is canceled, or the player's turn is ended, the rest of the card still resolves. |
| Abilities (general) | An ability that is unresolved cannot be triggered again within a timing window it generated itself. |
| Executive Order (Keleres tech) | Agendas that refer to "this agenda phase" stay in play and resolve in that round's agenda phase, or the next agenda phase if that round has none. |
| Plot cards (Firmament) | Hidden information once the game starts; players may look at them before the game begins. |
| Cronos and Tallin (Firmament home) | On flipping, units on Cronos go to Cronos Hollow, units on Tallin go to Tallin Hollow, space units stay in space. |
| Firmament/Obsidian technologies | Cannot be researched once the Firmament has become the Obsidian, but can be gained by effects that say "gain". |
| Breaches (Crimson Rebellion) | At the component limit, inactive breaches may be moved from elsewhere; active breaches must be removed or flipped before being placed again. |
| Aello (Deepwrought commander) | Once unlocked, any player may use it; the Deepwrought player cannot refuse. |
| Coexist (Deepwrought) | Several players may coexist on one planet. |
| Riftwalker Meian (Creuss hero) | Cannot swap the Ahk Creuxx system or Fracture systems. |
| Galactic events without PoK | Wild, Wild Galaxy and Age of Exploration work without PoK; ignore text about PoK components. |
| Minor Factions (event) | If Creuss or Crimson Rebellion is a minor faction, the Creuss Gate or the Sorrow goes in the minor faction spot and the home system goes near the board. |
| Age of Exploration (event) | New tiles cannot be placed off the Creuss home system, the Crimson Rebellion home system, the Wormhole Nexus, or Fracture tiles. |
| Wild, Wild Galaxy (event) | A home system replaced by a supernova is still that player's home system. With no planets in their home system, a player still controls every planet there and can score public objectives. |
