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
The Player who holds the speaker token, picks first in the Strategy phase, reveals each Agenda and decides tied outcomes.
_Avoid_: Host

**Round**:
One full cycle of Strategy, Action and Status phases, plus the Agenda phase once the Custodians token has been removed.
_Avoid_: Turn (a Turn is one Player's action within the Action phase)

**Phase**:
One of the Round's stages: Strategy, Action, Status or Agenda.

**Status phase**:
The Phase in which Players score objectives, reveal a public objective, draw an action card, reset their Command tokens, ready their cards, repair units and return their Strategy cards.

**Agenda phase**:
The Phase, added to every Round once the Custodians token is removed, in which two Agendas are revealed and voted on and then all Planets are readied.

**Vote**:
Votes cast on an Agenda outcome by exhausting Planets, one vote per influence; every vote a Player casts on an Agenda must go to the same outcome, and Trade goods cannot buy votes.
_Avoid_: Ballot

**Turn**:
A single Player's opportunity to take one action during the Action phase, tracked as the active player.

**Pass**:
A Player's declaration that they take no further Turns in the current Action phase, allowed only after they have performed the strategic action of their Strategy card.

**Strategy card**:
A numbered card chosen in the Strategy phase that grants its holder a primary ability and lets others follow with a secondary.
_Avoid_: SC card, initiative card

**Strategic action**:
A Turn action that resolves the primary ability of the Player's Strategy card, after which the card is exhausted and every other Player may Follow.

**Follow**:
Using a Strategy card's secondary ability after another Player plays its primary.
_Avoid_: Secondary-ing

**Tactical action**:
A Turn action that activates a system, moves ships into it, and then resolves space combat, invasion and production there.
_Avoid_: Move

**Component action**:
A Turn action that uses a card or ability marked as an action, rather than a Strategy card or Tactical action.

**Command token**:
A Player's token, kept in the tactic, fleet or strategy pool of their command sheet; tactic and strategy tokens are spent on actions, while the fleet pool caps how many ships a Player may have in a System.
_Avoid_: CC, command counter

**Fleet pool**:
The Command token pool whose size is the maximum number of non-fighter ships a Player may have in any one System.
_Avoid_: Fleet supply

**Trade goods**:
The flexible currency a Player holds, each spendable as one resource or one influence, though never as votes.
_Avoid_: TG coins

**Commodities**:
A Faction-limited stock of goods that a Player cannot spend but that converts to Trade goods when given to another Player. Restocking them to the maximum is to replenish, which players also call refreshing.
_Avoid_: Refill

**Transaction**:
A voluntary exchange between two neighboring Players of Trade goods, Commodities, relic fragments and up to one promissory note each way, agreed before anything changes hands.
_Avoid_: Trade, deal

**Deal**:
An agreement between Players that may include a Transaction and is binding only if its terms can be carried out immediately.
_Avoid_: Contract

### Map and pieces

**Map**:
The arrangement of Tiles for one Game, rendered as an image.
_Avoid_: Board

**Tile**:
A hex on the Map, occupied by one system.
_Avoid_: Hex, system tile

**System**:
A Tile as a place pieces can be activated, moved into and fought over; identified by its map position.

**Active system**:
The System a Player activated with a Command token for the current Tactical action, where movement, combat and production resolve.

**Space**:
The area of a System outside its planets, where ships sit.
_Avoid_: Space area

**Planet**:
A place within a System that can be controlled, exhausted and landed on, with resources and influence.

**Unit holder**:
Anything on a Tile that can hold units: its Space or one of its Planets.
_Avoid_: Location

**Unit**:
A piece of a Player's force, defined by its unit type and owner, and always a ship, a ground force or a structure.

**Ground forces**:
Infantry and Mechs, the Units that land on Planets and fight ground combat.
_Avoid_: Troops, ground units

**Mech**:
A faction-specific heavy ground force, produced for the cost on its Faction's mech card and sometimes placed through Deploy.

**Structure**:
A Unit placed on a Planet that cannot move: a Space dock or a PDS.
_Avoid_: Building

**Space dock**:
A Structure with Production, limited to one per Planet.

**PDS**:
A Structure with Space cannon, limited to two per Planet.
_Avoid_: Planetary defense

**Unit upgrade**:
A Technology that replaces the attributes and abilities of the Faction unit sharing its name, marked by a higher roman numeral.
_Avoid_: Unit tech

**Home system**:
The System a Faction starts in and defends.

**Wormhole**:
A System feature that links it to other Systems bearing the same wormhole type.

**Wormhole nexus**:
The extra System where alpha, beta and gamma wormholes converge, which starts with only its gamma wormhole and becomes active when a unit enters it or Mallice is controlled.

**Anomaly**:
A System with unique rules for movement or combat, marked by a red border: an asteroid field, nebula, supernova or gravity rift.

**Asteroid field**:
An Anomaly that ships cannot move through or into.

**Nebula**:
An Anomaly that ships can enter only as the active System, that cuts the move value of ships starting there to 1, and that gives its defender +1 on combat rolls.

**Supernova**:
An Anomaly that ships cannot move through or into.

**Gravity rift**:
An Anomaly that adds +1 to the move of ships leaving or passing through it, each of which is destroyed on a roll of 1 to 3.

**Border anomaly**:
An anomaly attached to a System's edge rather than its interior.

**Hyperlane**:
A Tile that is not a System, whose drawn lanes make the Systems at their ends adjacent.

**Frontier token**:
A marker placed in each System without planets that can be explored for a reward.

**Attachment**:
A card attached to a Planet's card, marked by a token on the Planet, that modifies it and stays with the Planet when control changes.
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
An Agenda that permanently changes the rules once its For or Elect outcome wins, and is discarded if it loses.

**Promissory note**:
A card of a Player's own color and Faction that another Player holds and can play, passed on through Transactions and usually returned to its owner after use.
_Avoid_: PN

**Relic**:
A powerful card drawn from the relic deck, usually by spending relic fragments found through exploration; relics cannot be traded.

**Exploration**:
Drawing from a planet-trait or Frontier deck to reveal an attachment, relic fragment or other one-off effect, which a Player does on first taking control of a Planet.

**Technology**:
A researched upgrade that grants abilities or unit improvements.
_Avoid_: Tech card

**Leader**:
An agent, commander or hero belonging to a Faction; the agent starts ready, while the commander and hero must first be unlocked.

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

**Space combat**:
The Combat between ships in the active System, which follows Space cannon offense and runs until at most one Player has ships left.

**Ground combat**:
The Combat between ground forces on a Planet during Invasion, which runs until at most one Player has ground forces left.

**Attacker / Defender**:
The active Player is the attacker in a Combat and the other Player is the defender.

**Combat modifier**:
An effect that changes a unit's combat roll or the number of dice rolled.

**Bombardment**:
A unit ability that rolls against ground forces on a Planet at the start of Invasion.

**Planetary shield**:
A unit ability that stops Bombardment against its Planet.

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

**Resources**:
A Planet's material value, spent by exhausting the Planet to pay for Production and other costs.
_Avoid_: Res

**Influence**:
A Planet's political value, spent by exhausting the Planet to gain Command tokens, remove the Custodians token or cast votes.
_Avoid_: Inf

**Control**:
A Player controls a Planet when they hold it, whether or not units are on it.
_Avoid_: Own, claim

**Neighbors**:
Two Players who both have a unit on or control a Planet in the same System or in adjacent Systems, and so can make Transactions.
_Avoid_: Adjacent players

**Invasion**:
The step of a Tactical action in which a Player bombards, lands ground forces on Planets, fights any defenders and establishes control.

**Production**:
A unit ability, chiefly of Space docks, to produce units by spending resources at least equal to their cost, up to the ability's value.
_Avoid_: Build

**Capacity**:
The number of fighters and ground forces a ship can carry, which together cap what a Player may keep in a System's Space.

**Transport**:
Carrying fighters and ground forces along with a moving ship, up to its capacity.
_Avoid_: Ferry

**Deploy**:
A unit ability that places that unit from reinforcements without producing it.

**Reinforcements**:
A Player's supply of units and Command tokens not currently on the Map, from which new ones are drawn.
_Avoid_: Supply, reserves

**Blockaded**:
A Player's unit with Production in a System that holds enemy ships and none of its owner's, so it cannot produce ships but can still produce ground forces.

**Space cannon**:
A unit ability that rolls to hit ships in the active System after movement and ground forces committed to land on its Planet during Invasion.
_Avoid_: PDS fire

**Anti-fighter barrage**:
A unit ability that destroys fighters at the start of the first round of space combat.
_Avoid_: AFB

**Sustain damage**:
A unit ability to absorb one hit and flip to a damaged state instead of being destroyed.

**Capture**:
Taking an opposing unit out of play to the capturing Player's faction sheet instead of destroying it, so its owner cannot use it until it is returned.

**Purge**:
Permanently removing a component from the game.

**Victory points**:
The score a Player earns from objectives and other effects, with the first to the Game's threshold winning.
_Avoid_: Score, points

**Custodians token**:
The token on Mecatol Rex that a Player removes by spending six influence and committing ground forces there, gaining a Victory point and starting the Agenda phase.

**Mecatol Rex**:
The center System, holding the Custodians token; its controller gains a Victory point when resolving the Imperial card.

**Legendary planet**:
A Planet whose controller also gains a legendary planet ability card with a unique ability.

**Initiative order**:
The order in which Players take Turns each Round, lowest Strategy card number first, and the default order for Action phase windows; Space cannon fire and Strategy card secondaries instead go clockwise from the active Player.
_Avoid_: Turn order (also used for the seating order around the table), Clockwise order

**Speaker order**:
The clockwise order beginning with the Speaker, used for abilities and components in phases where Players hold no Strategy cards (Strategy and Agenda phases); Voting instead begins with the Player to the Speaker's left.
_Avoid_: Seating order

**Adjacency**:
The relationship between Systems that touch, share a wormhole type or are joined by hyperlane lines, governing movement.
_Avoid_: Neighbors (that names Players with pieces in or next to each other's Systems)

**Elimination**:
A Player's removal from the Game when they control no Planets, have no ground forces on the Map and own no unit with Production.

### Table slang

**Pop**:
To play a Strategy card's primary ability, as in "popping Trade".
_Avoid_: Cast, fire

**Wash**:
To convert another Player's refreshed Commodities into Trade goods, usually as a favor requested of the Trade card's holder.
_Avoid_: Launder, flip

**X-1 / X-2**:
The outcome of the standard Trade deal: the Trade holder washes a Player's Commodities and keeps one Trade good as payment, leaving that Player one short of their maximum. X-2 is the same deal with a charge of two.
_Avoid_: Wash tax

**Plastic**:
Ships and ground forces, i.e. military strength measured in physical unit pieces; fighters and infantry may be stood in for by tokens, but every such stand-in needs at least one plastic piece, and each color has only so many.
_Avoid_: Fleet size, army size

**Tokens / tokes**:
Command tokens, as in "I'm out of tokens" or "spend a toke".
_Avoid_: Counters

**TGs / money**:
Trade goods.
_Avoid_: Coins, gold

**Comms**:
Commodities.

**Guac**:
Victory points gained outside public and secret objectives, such as Support for the Throne.
_Avoid_: Bonus points, extra VP

**Sabo**:
Shorthand for the Sabotage action card, which cancels another action card.
_Avoid_: Cancel card

**Gum**:
To park a fighter or other small ship in a System to block opponents from moving through it.
_Avoid_: Clog, blocker

**Locked down**:
Describes a System containing a Player's Command token, whose units cannot move out of it. Players saying "that System is locked down" mean either that the units in it cannot be moved out, or that other units can be moved in.
_Avoid_: Frozen, pinned

**Tech skip**:
A Planet with a Technology specialty, also called a tech spec or skip. Also applies to other sources of a specialty, such as the Yin Alliance.

**Point blocked**:
Stopped from scoring, as when a Player is kept from meeting an objective's requirement.
_Avoid_: Score blocked

**Stall**:
To take minor actions so as not to reveal major actions and plans to other Players.

**Out stall**:
To stall long enough that another Player has to Pass first.

**Leadership stall**:
Stalling while holding the Leadership Strategy card until other Players have to Pass before Leadership is used, so they never gain the tactic and strategy tokens needed for an effective Round.

**Winmaking**:
Helping another Player win, sometimes deliberately and sometimes unknowingly. Also called kingmaking, winemaking or stooging.

**Winslaying**:
Stopping a Player from being able to win. Also called kingslaying or slaying.

**Saar ball**:
The Saar faction's style of keeping its forces moving and reinforcing as it travels.

**Space risk**:
Prioritizing Combat and fleet fights over objectives, usually to the detriment of actually winning.
_Avoid_: Warmongering
