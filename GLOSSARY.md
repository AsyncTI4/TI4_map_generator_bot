# Async TI4 Game Management Bot

A Discord bot that runs asynchronous games of Twilight Imperium 4th Edition: it holds each game's state, drives play through slash commands and buttons, and renders the map. This glossary fixes the vocabulary shared by players, bot helpers and developers.

## Language

### Games and play structure

**Game**:
One asynchronous play-through of TI4 for a set of players, identified by a name like `pbd1234`.
_Avoid_: Match, session, lobby

**Game mode**:
A named ruleset variant or option layered onto a Game, such as Fog of War, Absol or Franken.
_Avoid_: Variant, flavor

**Player**:
A seat in a Game, holding one Faction and one color, normally controlled by one Discord user.
_Avoid_: User (the Discord account), seat

**Faction**:
The playable race a Player controls, which supplies its home system, starting technology, abilities, leaders and unique units.
_Avoid_: Race, empire

**Speaker**:
The Player who holds the speaker token and so breaks ties and picks first in the Strategy phase.
_Avoid_: Host

**Round**:
One full cycle of Strategy, Action, Status and Agenda phases.
_Avoid_: Turn (a Turn is one Player's action within the Action phase)

**Phase**:
One of the Round's stages: Strategy, Action, Status or Agenda.

**Turn**:
A single Player's opportunity to take one action during the Action phase, tracked as the active player.

**Pass**:
A Player's declaration that they take no further Turns in the current Action phase.

**Strategy card**:
A numbered card chosen in the Strategy phase that grants its holder a primary ability and lets others follow with a secondary.
_Avoid_: SC card, initiative card

**Follow**:
Using a Strategy card's secondary ability after another Player plays its primary.
_Avoid_: Secondary-ing

**Tactical action**:
A Turn action that activates a system, moves ships into it, and resolves landing and combat.
_Avoid_: Move

**Component action**:
A Turn action that uses a card or ability marked as an action, rather than a Strategy card or Tactical action.

**Command token**:
A Player's token pool, divided into tactic, fleet and strategy tokens, spent on actions and fleet capacity.
_Avoid_: CC, command counter

**Trade goods**:
The flexible currency a Player holds and spends.
_Avoid_: TG coins

**Commodities**:
The portion of a Player's economy that converts to Trade goods when given to another Player.

**Transaction**:
A voluntary exchange of Trade goods, Commodities, cards or other items between two Players.
_Avoid_: Trade, deal

### Map and pieces

**Map**:
The arrangement of Tiles for one Game, rendered as an image.
_Avoid_: Board

**Tile**:
A hex on the Map, occupied by one system.
_Avoid_: Hex, system tile

**System**:
A Tile as a place pieces can be activated, moved into and fought over; identified by its map position.

**Space**:
The area of a System outside its planets, where ships sit.
_Avoid_: Space area

**Planet**:
A place within a System that can be controlled, exhausted and landed on, with resources and influence.

**Unit holder**:
Anything on a Tile that can hold units: its Space or one of its Planets.
_Avoid_: Location

**Unit**:
A piece of a Player's force, such as a fighter, dreadnought or infantry, defined by its unit type and owner.

**Home system**:
The System a Faction starts in and defends.

**Wormhole**:
A System feature that links it to other Systems bearing the same wormhole type.

**Anomaly**:
A System feature that alters movement or combat there, such as a nebula or gravity rift.

**Border anomaly**:
An anomaly attached to a System's edge rather than its interior.

**Hyperlane**:
A Tile whose contents are lanes joining adjacent Systems rather than planets.

**Frontier token**:
A marker in an unexplored System that a ship may explore for a reward.

**Attachment**:
A token placed on a Planet that modifies its properties.
_Avoid_: Upgrade

**Map template**:
A predefined layout of positions that a Game's Map is built into.
_Avoid_: Map preset (that names a saved Map, not the empty layout)

**Fracture**:
The Thunder's Edge map event in which the galaxy is divided into separate regions.

### Cards, objectives and content

**Deck**:
An ordered, shuffled collection of cards of one kind that a Game draws from and discards to.

**Action card**:
A card played for a one-off effect at a specified timing window.
_Avoid_: AC

**Secret objective**:
A scorable goal known only to its holder until scored.
_Avoid_: SO

**Public objective**:
A scorable goal revealed to everyone, either Stage I or Stage II.
_Avoid_: PO

**Agenda**:
A card voted on in the Agenda phase, resolving as either a law or a one-time directive.

**Law**:
An Agenda that remains in play after passing.

**Promissory note**:
A card owned by a Faction, handed to another Player, and returned when its condition is met.
_Avoid_: PN

**Relic**:
A powerful card gained from exploration, usually by finding a relic fragment.

**Exploration**:
Drawing from a planet-type or Frontier deck to reveal an attachment, relic fragment or event.

**Technology**:
A researched upgrade that grants abilities or unit improvements.
_Avoid_: Tech card

**Leader**:
An agent, commander or hero belonging to a Faction, each unlocked and used under its own conditions.

**Ability**:
A standing faction or card effect that is not a card in hand.

**Breakthrough**:
A Thunder's Edge Faction card that is unlocked and then used for a powerful effect.

**Expedition**:
A Thunder's Edge shared objective that one Faction completes first for a reward.

**Splice**:
A Twilight's Fall card (ability, genome, paradigm or unit) drawn and distributed to shape a Faction.

**Plot card**:
A hidden Twilight's Fall card tied to a Faction and revealed for an effect.

**Event**:
A card that sets a game-wide condition for a period of rounds, drawn when an Event deck is in use.

**Source**:
The expansion or homebrew set a piece of content originates from.

**Homebrew**:
Community-made content or rule changes used in a Game outside the published rules.

### Draft and setup

**Slice**:
A set of Tiles assigned together to one Player to build the Map around their home system.

**Milty draft**:
A draft in which Players pick a Slice, a Faction and a seat position, producing the Map.
_Avoid_: Slice draft

**Franken**:
A mode in which each Player assembles a custom Faction from drafted components.
_Avoid_: Frankenstein, mix-and-match

**Bag draft**:
A draft where each Player picks from a rotating hand of items; the Franken drafts are Bag drafts.
_Avoid_: Pack draft

**Draft orchestrator**:
The configured flow that sequences the drafting steps of one Game's setup.

### Game modes

**Fog of War**:
A mode in which each Player sees only the parts of the Map and state their units can reveal.
_Avoid_: FoW (acceptable in code and chat), hidden-information mode

**Light fog**:
A milder Fog of War in which Players see more of the Map than under full Fog of War.

**Absol**:
A homebrew content mode that substitutes revised cards, technologies and leaders.

**Discordant Stars**:
A homebrew content mode that adds additional Factions and components.

**Alliance mode**:
A team mode in which Players are paired and share some benefits.

**Community mode**:
A mode where a Game is played by a community role rather than individually assigned Players.

**TIGL**:
The Twilight Imperium Global Ladder, whose competitive Games report results and move Players through ranks.
_Avoid_: Ladder game

### Combat

**Combat**:
A fight between two Players in a System, resolved in rounds of rolling units.

**Combat modifier**:
An effect that changes a unit's combat roll or the number of dice rolled.

**Bombardment**:
A pre-ground-combat attack by ships against planets.

### Discord surface

**Actions channel**:
The Game's main Discord text channel where play is announced and driven.
_Avoid_: Main channel, game channel

**Table talk**:
The Game's channel for conversation between Players, kept apart from Actions.

**Cards info thread**:
A Player's private thread showing their hand, cards in play and status.
_Avoid_: Hand, info channel

**Bot map updates thread**:
A thread where the bot posts map images after each change.

**Launch post**:
The announcement a Game is created with, which Players join through.

**Auto-ping**:
A scheduled reminder that nudges the active Player when their Turn has been idle.
_Avoid_: Nag, bump

**Undo**:
Restoring a Game to an earlier saved state.
_Avoid_: Rollback

**Bothelper**:
A trusted community role that can repair a Game's state with privileged commands.
_Avoid_: Mod, admin

**Testbed**:
A developer-only harness for driving a Game through scripted actions on a development bot.

### Rules concepts

**Exhausted / Readied**:
A card or planet is exhausted once its use is spent and readied when refreshed, typically in the Status phase.
_Avoid_: Tapped / untapped, used / refreshed

**Control**:
A Player controls a Planet when they hold it, whether or not units are on it.
_Avoid_: Own, claim

**Invasion**:
The step of a Tactical action in which ground forces land on Planets and fight any defenders.

**Production**:
Building units at a Space dock or via an ability, paid for with resources up to the unit's cost.
_Avoid_: Build

**Reinforcements**:
A Player's supply of unit pieces not currently on the Map, from which new units are drawn.
_Avoid_: Supply, reserves

**Blockaded**:
A Space dock whose System contains enemy ships and none of its owner's, so it cannot produce ships.

**Space cannon**:
A unit ability that fires at ships, in space or on approach to a Planet, before combat.
_Avoid_: PDS fire

**Anti-fighter barrage**:
A unit ability that fires at fighters at the start of space combat.
_Avoid_: AFB

**Sustain damage**:
A unit ability to absorb one hit and flip to a damaged state instead of being destroyed.

**Capture**:
Removing an opposing unit from the Map to a Player's own holding area rather than destroying it.

**Purge**:
Permanently removing a component from the game.

**Victory points**:
The score a Player earns from objectives and other effects, with the first to the Game's threshold winning.
_Avoid_: Score, points

**Custodians token**:
The token on Mecatol Rex that is removed by spending influence and grants a Victory point.

**Mecatol Rex**:
The center System, holding the Custodians token and the Imperial card's point for controlling it.

**Legendary planet**:
A Planet with a unique ability that its controller may use.

**Initiative order**:
The order in which Players take Turns each Round, set by the numbers on their Strategy cards.
_Avoid_: Turn order (also used for the seating order around the table)

**Adjacency**:
The relationship between Systems that share an edge or are linked by wormholes or other effects, governing movement.
_Avoid_: Neighbors (that names Players with pieces in or next to each other's Systems)

**Elimination**:
A Player's removal from the Game when they control no Planets, have no ground forces on the Map and own no Space docks.

### Table slang

**Pop**:
To play a Strategy card's primary ability, as in "popping Trade".
_Avoid_: Cast, fire

**Wash**:
To convert another Player's refreshed Commodities into Trade goods, usually as a favor requested of the Trade card's holder.
_Avoid_: Launder, flip

**X-1**:
The outcome of the standard Trade deal: the Trade holder washes a Player's Commodities and keeps one Trade good as payment, leaving that Player one short of their maximum.
_Avoid_: Wash tax

**Plastic**:
Military strength measured in physical unit pieces; fighters and infantry may be stood in for by tokens, but every such stand-in needs at least one plastic piece, and each color has only so many.
_Avoid_: Fleet size, army size

**Guac**:
Victory points gained outside public and secret objectives, such as Support for the Throne.
_Avoid_: Bonus points, extra VP

**Sabo**:
Shorthand for the Sabotage action card, which cancels another action card.
_Avoid_: Cancel card

**Gum**:
To park a fighter or other small ship in a System to block opponents from moving through it.
_Avoid_: Clog, blocker

**Saar ball**:
The Saar faction's style of keeping its forces moving and reinforcing as it travels.

**Space risk**:
Prioritizing Combat and fleet fights over objectives, usually to the detriment of actually winning.
_Avoid_: Warmongering
