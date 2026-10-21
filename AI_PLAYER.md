# AI players

Bot-controlled seats that play standard games (Base, Prophecy of Kings, Thunder's Edge; no fog
of war, homebrew, galactic events, scenarios, Twilight's Fall or Franken). They make their
decisions with rule-based heuristics, not an LLM. Any number of seats can be AI, up to every
seat at the table, so the bot can play a test game against itself. AI players ship in
releasable versions; this document describes what exists today.

[Using it](#using-it) · [Self-play](#self-play) · [How it works](#how-it-works) ·
[Segregation](#segregation) · [Testing](#testing) · [Status](#status)

## Using it

1. A developer turns the feature on once per bot:
   ```
   /developer setting setting_name:ai_players_enabled setting_value:true setting_type:bool
   ```
2. Before the secret objectives are dealt (and not while a draft is running), a player of the game, its
   owner or a bot developer runs
   `/ai add hs_tile_position:<pos>` once per AI seat. The `faction`, `color` and `speaker`
   options are optional; without `faction`, the seat takes the first free faction in the list
   below.

| Command | What it does |
| --- | --- |
| `/ai add` | Adds an AI seat and sets it up at the given home position. |
| `/ai status` | Shows whether each AI seat is running, paused, or disabled on the bot. Never shows aggression. |
| `/ai pause` / `/ai resume` | While paused, an AI acts only through the table: blocking choices are posted for any player to make. |
| `/ai fallbacks` | Lists the last 10 times an AI seat was unsure, made no progress or timed out, with what it did about it. Players, the owner and developers only; the prompt, button and decision details are left out for private prompts and in Fog of War games unless you are a developer. |
| `/ai delegate` | If an AI is holding up the game and doesn't know what to do, post its options for the table now. Without `faction_or_color` it only applies to seats that are waiting on something. |
| `/ai watch` | Developers only: creates a new game with its channels in this channel's category, sets up the standard map, seats an AI in every chair (3 to 6, default 6, random supported factions) and starts it, so you can watch it play itself. Options: `seats`, `game_name` (default `aiwatch<N>`) and `pace` (`fast` by default: shorter pauses while no human plays). You are added to each AI's private thread. |
| `/ai remove` | Removes an AI seat before the game starts and restores what adding it changed (map tile; speaker and game flags once the last AI seat leaves). Afterwards, use `/game replace` to hand a seat to a human. |

Every command except `add` takes an optional `faction_or_color` to target one AI seat; without
it, it applies to all of them (`remove` needs it when there are several).

**Factions.** Each AI seat plays a different faction: the bot cannot seat two players with the same
faction (setup refuses it, and home planets, button ownership and many game values are keyed by
faction). AI seats can play the factions whose setup needs no choices and whose abilities the
shared rules handle: Nekro Virus, Sardakk N'orr, Emirates of Hacan, Barony of Letnev,
L1Z1X Mindnet, Naalu Collective, Federation of Sol, Universities of Jol-Nar, Xxcha Kingdom and
Yin Brotherhood. They all use the same rules; the Nekro-only rules (Technological Singularity,
the tech-steal bonus when choosing attacks) switch on for Nekro only.

**Federation of Sol.** The Sol rules key on the ability, leader, unit or breakthrough rather than the faction, so
Nekro and Franken copies work too (`ti4.ai.sol`):
- _Orbital Drop_ (`OrbitalDropRules`, `GroundDrops`) is a component action worth, per planet, the better of what 2
  infantry cut from the expected loss of a landing there (the same defence model production uses), the carriers in
  that system waiting for infantry (1.0 each when the best tactical plan launches from it, 0.5 otherwise), and
  finishing _Occupy the Fringe_ (80% of a point), plus a floor of 0.3 per infantry and a quarter of the rest. It
  costs a strategy token (2 trade goods, times 0.4 with 3 spare tokens, 0.6 with 2, 0.8 with 1, 1.0 with none, and 1.5
  more when it would cost a follow it plans) and the action. It is played when that, plus 1.5 for stalling while
  anyone else is still in the round, beats the best tactical plan, and in place of passing. The mech deploy pays 3
  resources when the mech's gain beats the premium over a mech's cost (a mech that turns a landing 2 infantry cannot,
  or advances _Mechanize the Military_).
- Evelyn Delouis (`SolAgentRules`) is used at the start of a round of a ground combat the seat fights in, when the
  extra die on its best ground force moves the win chance times the stake by at least 0.2; never for other
  players' combats. Claire Gibson (`SolCommanderRules`) places her free infantry before the seat's first roll.
- Jace X, 4th Air Legion (`SolHeroRules`) is played from round 3 on when the seat has a tactical token and freeing
  every command token on the board makes its best tactical plan worth at least three quarters of a point and at least
  3 more than its two best plans now.
- _Bellum Gloriosum_ (`BellumGloriosumRules`): after its normal build is paid, the seat takes the free build for each
  ship with capacity it produced: defending infantry, spare infantry up to four, then fighters up to its room, paid
  from what the reserve leaves.
- Production wants one Advanced Carrier II (capacity 8, and it sustains damage) instead of two carriers.
- Genesis' infantry in space is landed like any other ground force in the system, or carried with the fleet.

**When an AI is confused**, it posts the choice in the main game channel and **any player**
may pick for it. Choices involving hidden information (its hand, its secret objectives) are
never shown: the AI picks a safe option and says so. A safe option is one that declines or passes; it is never a
button that cancels a hit (`cancelSpaceHits_`, `cancelPdsOffenseHits_`, `cancelGroundHits_`, `cancelAFBHits_`), which
would throw a hit away for free.

**Trading:** they trade through the bot's own transaction buttons and never read chat. They start deals on their
own turn, in the agenda phase, as Trade holder with the players who followed, and ask for debt payments at any time;
they answer offers whenever they arrive. They trade trade goods, commodities, debt, relic fragments, spare action cards and at most one of their own
promissory notes per deal, never relics or secret objectives. They keep the terms they accept (an AI
that followed another AI's Trade pays the announced fee) and remember who pays: human players they can't trust get
less (no free Trade follow, no loans).

## Self-play

When every real player is an AI seat, the game runs itself. `/ai watch` sets one up in a single command;
to build one by hand:

1. Create a game as usual. The owner stays in the game as an unseated spectator; that keeps
   channel access, and lets them run `/ai` commands and resolve anything the AIs post.
2. Add the map, set `/game setup player_count_for_map` to the number of seats, then run `/ai add`
   once per seat (one of them with `speaker:True`).
3. The AIs take it from there. On top of their own choices:
   - **Referee.** After every seat has been idle for 20 seconds, the speaker AI presses the
     buttons any player may press to move the game on: dealing the starting secret objectives
     (once every seat is filled), revealing objectives, starting the strategy phase, flipping and
     resolving agendas, breaking ties and moving on to the next round. It never ends the game. It confirms an agenda's
     winner (the "Resolve with Current Winner" button, never a manual resolution) only once the announcement is a
     minute old, so the AI seats have answered the shenanigans prompts first.
   - **No one to delegate to.** Instead of posting a choice for the table, an unsure AI picks a
     declining option (or else the first option that doesn't open a form) and says so.
   - **Game end.** Once a player reaches the victory point goal, or every objective has been revealed,
     the AIs stop and post a notice; finish the game with `/game end`. Ending it still reports the game like any other (staff
     channel, stats for the winner); deleting the channels by hand avoids that for a throwaway `/ai watch` game.
   - **No one to hide from.** With no human at the table, the AIs answer Sabotage windows within seconds instead
     of after a 10-40 minute delay, and at `fast` pace the referee waits 10 seconds instead of 20.
   - Pausing any AI seat stops the referee too, so the owner can take over moving the game on.

## How it works

- **Seat.** A normal player whose user id is synthetic: `7100000` followed by 9 digits, below any
  real Discord snowflake. Its name is "<faction> AI". The bot gives it a private cards-info thread,
  which is its inbox. Its hidden profile (brain, aggression base and mode, paused state, seed) lives
  in player stored values, so `/game undo` rolls it back with the game.
- **Change detection.** `AiRuntime` polls every 3 seconds from a `CronManager` task named
  `AiRuntime`. It compares each AI game's `ManagedGame.lastModifiedDate`, which every save,
  undo and reload refreshes, and queues a debounced tick on its own worker threads.
- **One lane per game.** A tick reads the main channel, the AI seats' private threads and
  their ongoing combat threads once, then lets each AI seat decide in turn, starting after the
  seat that acted last. The first seat that wants to press gets the game's single press for that
  tick, so presses never race for the game lock and every decision sees fresh state. Each seat
  keeps its own memory, pressed buttons, attempt counts, stall timer and caps.
- **Perception.** A seat sees buttons it owns (`FFCC_<faction>_…`), everything in its own
  thread, every message in its combat threads, and the public windows its rules care about. It
  never reads other players' private threads.
- **Decision.** The rules return `Press`, `Unsure`, `Wait` or `Idle`. They handle:
  - strategy card picks, valued by what the card is worth to the seat right now (below);
  - **tactical actions**: choosing a plan, activating the system, moving, landing, exploring, building, paying and ending the turn (below);
  - **combat**: rolling, anti-fighter barrage, space cannon offense (also its own PDS and PDS II on its own tactical
    action) and defense, Assault Cannon (it fires it with 3 or more non-fighter ships before the first roll, and
    when an opponent fires it, destroys its own cheapest non-fighter ship and closes the prompt), bombardment before
    landing, hit assignment with the bot's auto-assign buttons (which use Sustain Damage first), a single hit an
    opponent's ability assigns to one of its units (it sustains if it can, otherwise takes the loss), Graviton
    Laser System before its space cannon fires at ships screened by fighters, Magen Defense Grid's hit at the start
    of a ground combat (a damaged mech first, then infantry, then a mech that would only sustain), ground combat
    automation. Combat technologies (`CombatTechRules`): Dimensional Splicer at the start of a space combat;
    Impulse Core when the cheapest enemy ship is worth at least the destroyer or cruiser it gives up and no enemy
    ship can sustain the hit; Supercharge once per combat; Exotrireme II after a finished round when the two best
    enemy ships cost at least twice a dreadnought; the enemy ship to hit when one of these lets it choose (the most
    expensive, a sustain counted as 1); taking an opponent's Impulse Core hit like any single hit. **Retreats**
    (`RetreatRules`): before rolling a space combat round it announces a retreat when its chance to win falls below
    a threshold that slides from 35% for a system it does not need down to 20% for a critical one (its planets
    there, unspent ones counting more, its structures, and the objective progress its ships hold there; a stake of
    a victory point or more is critical). It still fights that round, then retreats before the next roll to the
    legal system it values most (home, its planets and ships, away from enemy reach; Dark Energy Tap widens the
    choice). It never retreats from its home system. Ground forces on planets there leave with it, within the
    retreating ships' spare capacity and mechs first, when the enemy's ground forces in orbit would likely take the
    planet: it evacuates only while the garrison's cost beats the chance it holds times the garrison plus the
    planet's stake;
  - **technologies used outside combat** (`TechRules` unless noted): Infantry II revival at the start of its turn;
    Yin Spinner's 2 infantry after producing (on its dock planet first); Magen Defense Grid's mandatory infantry;
    Bio-Stims at the end of its turn to ready its best spent planet with a technology specialty; Psychoarchaeology
    on the turn it passes, trading ready planets with a technology specialty for trade goods (those worth 1 or less
    always, the rest only with nothing to save for scoring or Leadership); Predictive Intelligence's 3 votes on top
    of its planets (`AgendaVoting`); AI Development Algorithm as a production discount when it has 2 or more unit
    upgrades (or cannot research), and exhausted when a unit upgrade it researches needed its prerequisite skip
    (`StrategyCardRules`); Sling Relay (`SlingRelayRules`) as an action of its own whenever it beats the best
    tactical plan: the ship's value less what its resources could buy elsewhere, plus 1.5 for waiting to see what
    the others do and 1.0 more for an unlocked ship an objective wants (a destroyer at least); before passing it
    also adds a ship, since unspent planets are wasted, weighing any trade goods against the ship and the stall;
    Fleet Logistics plays slowly: a second tactical action only for an attack or a plan worth most of a point, a
    second strategic action only for Imperial when it scores right away; with Thunder's Edge Warfare it attacks with
    Warfare first (no command token), leaving the same ships free for the follow-up; each action keeps its own plan;
    Dark Energy Tap: a spare ship is worth sending to a system with a frontier token (1.5 for the card, 1.0 for the
    position, the stall and later edge-of-map objectives) and it widens retreats; Production Biomes' 4 trade goods
    instead of passing while it has a strategy token to
    spare (the other 2 to the player furthest behind); Nullification Field when the player activating a system
    with its ships could bring in a bigger fleet; Neural Parasite against the leader, on a planet rather than in space
    and on a planet's last infantry first; Salvage Operations' trade good after a decided space combat (it declines the paid
    rebuild); Self-Assembly Routines' free mech after producing (on the dock planet); Integrated Economy after
    taking a planet (`IntegratedEconomyRules`): units an objective wants (a mech for _Mechanize the Military_, ships
    for fleet objectives), otherwise infantry and fighters (for spare capacity) only when an enemy fleet can reach
    the system or the money is spare beyond its best dock's production and a research; Mirror Computing counts each
    trade good as 2 in every payment plan;
  - **Entropic Scars** (`EntropicScarRules`): while it has ships in a scar (an anomaly, Thunder's Edge) at the start of the
    status phase it presses the faction technology it values most (`ResearchPolicy.value`, which includes the secret
    objectives below) for a strategy token, prerequisites ignored, as long as the technology is worth more than the
    token at the tech tempo, otherwise it presses "No Thanks". Each offer message is answered once, and only while
    the ships are still there. While it holds _Adapt New Strategies_ and has no ships in a scar, a position move into a
    reachable scar is worth 0.6 of a point divided by the faction technologies still missing. While it holds the secret
    with ships in or beside a scar it keeps one strategy token for the purchase (`ScarToken`), and while a reachable
    research route still needs work it takes a third strategy token at the status phase so it can follow Technology;
  - technologies it does not use: Transit Diodes, Chaos Mapping, Instinct Training, Quantum Datahub Node, Wormhole
    Generator and the remaining faction technologies with an action or a choice it has no rule for. They are worth
    the least when it researches or copies a technology;
  - **Technological Singularity** (Nekro): copying a technology after the first kill in a combat. A technology
    that changes the rest of that fight (a unit upgrade for its units there, Duranium Armor, X-89 in a ground
    combat) is worth its swing in the win chance times the resources at stake, and attack odds already count the
    copy it would make;
  - **strategy card primaries and secondaries** (below), ending its turn and passing;
  - declining whens/afters; **agenda votes**, Nekro's **Galactic Threat** and agenda ties as speaker (below);
  - **scoring** (below), status-phase command tokens, the opening secret objective discard, discarding down to the
    action card limit, and queueing _Prove Endurance_ when it holds it and everyone else has passed;
  - Sabotage or "No Sabotage" after the same seeded delay in every window, so the timing never reveals its hand
    (below);
  - Nekro: Propagation's command tokens from any source, the commander's card draw, Dacxive Animators' infantry,
    and its agent and hero (below);
  - paying for a Nekro agent that names it (any AI seat);
  - Sardakk's commander and hero (`ti4.ai.sardakk`, keyed on the leaders being unlocked, so alliances count):
    - _G'hom Sek'kus_: the tactical planner counts 1 ground force from each planet in an adjacent system without the
      seat's own command token as landable in the active system (unless an enemy fleet sits over it, the planet is
      contested, or the home/guarded garrison rules need the force). That gives expansions and invasions with no
      transport, and favours systems beside its infantry. At the Commit Ground Forces step it pulls what the plan
      lands beyond the ground forces in orbit, and adds a force to a contested planet when that lifts the win chance
      by 8 points or more (from between 30% and 85%). Never twice from one planet, never onto a planet with the
      custodians token, and not while its ships still have a space combat to fight;
    - _Sh'val, Harbinger_: after movement, instead of fighting the space combat, when the ships are not favoured
      (60% or less to win) but the ground forces in orbit (plus the commander's) win the landing at 65% or more on a
      planet worth a victory point or more (an objective or secret it would score, a custodians/Mecatol hold, an
      enemy home planet, denying a rival's score), and not fighting is worth more than the fleet's replacement cost
      beyond the expected fight losses plus half a point for the card. It then lands everything on that planet.
  - playing component action cards and _Forward Supply Base_, valued by what they score and by human play statistics,
    and answering cards that target it (below);
  - playing the promissory notes other players gave it, and respecting its own notes played against it (below);
  - trading (below).
- **Scoring comes first.** Victory points drive most choices:
  - **Public objectives**, including "spend" objectives. The bot takes trade goods and tokens off by itself;
    for resources and influence it only posts exhaust buttons and trusts the player, so the AI plans the cheapest
    payment (`Wallet`: planets before trade goods, each planet paying resources or influence, never both) and
    exhausts exactly that. It prefers the objective worth more points, then one that costs nothing.
  - **Reserve.** During the action phase it keeps back what the cheapest affordable spend objective needs at the
    status phase (unexhausted planets, trade goods, tokens), unless it already qualifies for enough free
    objectives. Production, research and following respect the reserve. Production pays with the planet that wastes
    least: the resources it overpays plus its influence at 2 resources to 3 influence, so a 2/3 planet such as Lirta IV
    is kept for Leadership or the custodians when a resource planet covers the same bill. Production also leaves the custodians' 6
    influence while its ships can reach Mecatol Rex, and the influence-leaning planets a Leadership secondary (or its
    own primary) would spend on command tokens while that card is still to come this round.
  - **Mecatol Rex.** It lands on Mecatol Rex when it can pay the custodians' 6 influence (on top of the reserve),
    pays it honestly, and scores the Imperial point whenever it plays Imperial while holding Mecatol Rex. Before then
    it races for the custodians (below). The offense outlook's Imperial term values gaining it (an expansion or an attack)
    at 0.8 of a victory point for each point it could then cash: one for its own unplayed Imperial and one for an
    _Overrule_ it may play while Imperial is still to be played, from the late game on (two with both). Landing in
    place counts the same points at three quarters of what they are worth. That makes the swipe, taking Mecatol Rex before Imperial, worth planning for, and it pushes
    those plans over the score at which a second tactical action is taken (Fleet Logistics, _Master Plan_) and above
    the component actions they are compared with.
  - **Secret objectives** (`ti4.ai.secrets`), within the scoring limits:
    - **Status phase:** one per status phase, choosing among those it qualifies for the one it could lose first
      (board presence) and the ones with a cost last. It pays those costs honestly: 5 action cards for
      _Form a Spy Network_, 2 relic fragments for _Destroy Heretical Works_. A cost (like a spend public objective's
      planets) stays owed until it is paid, even when the bot queues the score behind slower players.
      _Adapt New Strategies_ counts only the seat's own faction technologies, so Nekro's copies never score it.
      Research walks the route to _Adapt New Strategies_ and _Master the Laws of Physics_ (`TechPath`): the researches
      still needed are the seat's cheapest two faction technologies (each one research, plus one per prerequisite it
      lacks after its technology specialty planets and breakthrough synergy, where a synergy colour counts as either
      of its two) or the colour closest to four (synergy colours pooled, as the bot's own progress check does). If
      that fits in the researches left (one per round the leader still needs) a technology that shortens the route
      carries 1.1 times its even share of the point and the one that finishes it the whole point; otherwise the
      route is worth nothing, and the secret's keep value falls to what it has already completed. Nekro (no
      research, Valefar Assimilators never count) never has a route.
    - **Action phase:** at most one per combat (a space combat and each ground combat are separate). Before anything
      else on every tick it records each combat it is in (opponent, whether that opponent leads on points, its
      flagship, the system), and it also picks up a combat it only saw after the fact from the bot's combat round
      trackers. It judges each combat when it ends, and counts a win only if both sides rolled a round: _Spark a
      Rebellion_, _Brave the Void_, _Darken the Skies_, _Unveil Flagship_, _Betray a Friend_, _Demonstrate Your
      Power_, _Destroy Their Greatest Ship_ (the enemy's flagship or war sun gone from the board, not just
      retreated), _Fight with Precision_ (anti-fighter barrage clears the fighters before the first round, counted even when the
      fighters were all the opponent had, which ends the combat before another look; the seat waits for the barrage
      hits to be assigned before rolling the first round), _Turn
      Their Fleets to Dust_ (its space cannon, and no other player's, destroys an invader's last ships), _Become a
      Martyr_ and _Prove Endurance_. Earned secrets wait in a queue, so two combats in one turn can score two.
      _Make an Example of Their World_ scores when its bombardment destroys the last ground forces on a planet
      before it lands; it builds ships with bombardment for it and keeps the secret while it has some.
      When a combat meets two, the rarer one (by how often real players finished it) is scored.
    - **Prove Endurance** (`ti4.ai.endurance`). It is scored by passing last, so a seat holding it counts the actions each
      player still has (`StallCount`) and stalls only while it can outlast them. A count is the tactic tokens (its own
      less the ones a scoring reserve keeps), unplayed strategy cards, ready technologies with an ACTION (its own: Sling
      Relay with a space dock and Production Biomes), 1 more when Bio-Stims is ready and could refill one, relics with
      an ACTION and unlocked leaders with an ACTION (another player's are read from their public cards; its own are the
      _Enigmatic Device_ and relic fragments it can purge), and the component action cards in the hand: its own count
      0.8 each, another player's hand counts its size times the share of component action cards in the deck times 0.8.
      The race compares its count with the best of the players who have not passed. The chance to win is
      `1 / (1 + e^(-lead / 1.5))`, and above 0.3 the race is contested: every stall (`Stall.value`: the 1.5 for a stall plus
      the chance times a point (`ScoringStakes.scoreValue`) over the rival's count plus one) is worth more, so cards,
      relics, _Enigmatic Device_ and Sling Relay are used before passing, Bio-Stims readies an exhausted Sling Relay
      rather than a planet, and with no other action left it spends a tactic token on a tactical action that moves
      nothing (`StallPlans`: its home system first, then a system it holds, never one with enemy units or its own
      token). Below 0.3 it passes as before. While the race cannot be won the component action cards get no stall
      value at all (`Stall.cardActionValue`), so they are saved for a round where it can, unless the card is worth
      playing for what it does. Holding it adds 0.75 to researching Sling Relay and Bio-Stims and 0.5 per token to
      picking Leadership (`CardUse.LEADERSHIP_ENDURANCE`).
    - **Patience.** Saving an action secret means not going out of its way to meet the condition early; a combat
      that has already met it is always scored. The tactical planner counts a qualifying secret as a full point when it
      should pursue it now and as a quarter of a point when it can be patient. Human tables usually finish by round 5
      of a ten point game, so it pursues every secret once the last round looks near: the round is at least half the
      goal, or any player is within 4 of the goal (6 of 10). Before that it is patient only while it expects to meet
      the condition in a later round anyway: the chance of meeting it in the rounds before the likely last round and
      in that last round (its ordinary per-round rate, never counting on an enemy staying within reach, times 0.85 for
      each other combat secret in hand as they compete for the same combats) must be at least 0.4. So rare secrets
      are pursued at once. _Make an Example of Their World_ and _Destroy Their Greatest Ship_ are never saved: the
      seat pursues them from round 1. `-Dai.secrets.hold=false` turns patience off.
    - **Agenda phase:** no limit: _Dictate Policy_ (3 laws in play) and _Drive the Debate_ (it or its planet is
      elected by the agenda just resolved, never one from an earlier round), for which it also votes for itself.
    - **Seize an Icon** (`IconStake`). While it holds the secret and controls no legendary planet it chases one.
      The planner already credits a landing or an attack that takes a legendary planet with the secret's full point;
      on top of that it claims the legendary planet of a system first when the landing forces cannot cover every
      planet, takes a rival's legendary planet through the structures on it (outside a home system structures
      otherwise forbid the landing), stages for one a rival holds, and `marchPlans` moves a transport with up to two
      ground forces one system closer to the nearest free legendary planet it cannot reach this action (worth 0.6 of a
      point times the share of the distance closed, less the counterstrike, the cost of leaving and the distance). A
      free legendary planet is also a round goal for the advance plans, in any territory zone. For a legendary Mecatol
      Rex (Thunder's Edge) the secret's point joins the custodians race prize, and the custodians are paid out of the
      scoring reserve, since the point of the custodians plus the icon outweighs a spend objective. A held legendary
      planet needs no extra rule to be kept until the status phase: losing it loses the secret, which already makes its
      system guarded. The standard self-play map (tiles 18-48) has no legendary planet, so only a map with one (Primor,
      Hope's End, Mallice, a Thunder's Edge Mecatol Rex) exercises this.
    - **Keeping secrets.** When it holds too many it discards the one with the lowest keep value: the chance the
      secret gets scored times what a point from its phase is worth (action 1.2). The chance blends how often real
      players finished the secret (`SecretStatistics`) with the board: progress for a status secret, and for an action
      secret the chance per round of meeting its condition (`ActionSecretOdds`), higher with a ship placed for it and
      0.6 when it can make the condition happen next turn. A secret it can score right now is worth a flat 2.0, and a
      status secret is halved when other scorable ones already fill the rounds left. It uses Imperial to replace the
      rest. It pursues _Mechanize the Military_ and _Occupy the Fringe_ like the other board secrets: the tactical
      planner counts the planets holding one of its mechs and the most ground forces on a planet without its space dock,
      lands a mech first on a planet that has none while it holds _Mechanize the Military_, and with _Occupy the Fringe_
      in hand plans garrison moves that carry ground forces onto a planet it holds without a dock. With _Mechanize the
      Military_ in hand, home keeps infantry back before mechs, expansions load mechs first and land one on each
      planet, a pickup that would complete the secret is worth half a point, production adds a carrier at home when two or
      more spare mechs sit there with none to take them, a forward dock goes to a planet that shares its system with
      another held planet, building at a dock first lifts a spare mech onto a held planet in that system without one
      (using a ship already there, or one from next door), and garrison moves carry spare mechs (never a planet's last one) to held planets that have none,
      one each, and with _Occupy the Fringe_ in hand production buys the infantry its biggest dockless stack still lacks,
      up to what its carriers in or next to the system can move (plus 4 with Transit Diodes ready). A carrier pickup at a
      system holding that cargo is worth more while either secret is held. With Transit Diodes ready
      (`TransitDiodesRules`) it moves up to 4 ground forces at the start of its turn: spare mechs to bare planets, or
      infantry onto the dockless stack. Research values Transit Diodes for these secrets only when its prerequisites are
      already met. With _Strengthen Bonds_ (another player's note in its play area) or
      _Betray a Friend_ (beat a player whose note is in its play area; only a neighbour's note counts, one per owner) it
      buys the notes that land in a play area (`NoteSecrets`): such a note is worth 0.9 of a point more for _Strengthen
      Bonds_ and 0.4 for _Betray a Friend_, while the secret is unmet. Its first ask is the owner's fair price for the
      note; each ask that did not land raises the next by 1 trade good in rounds 1 and 2, 2 in the middle rounds and 3
      once the end is in sight, up to 9.
      With an unscored _Destroy Heretical Works_ it never purges fragments for a relic unless 2 are left, and fragment
      trades value its first two fragments 2 trade goods higher (`FragmentValue`, `FragmentDeals`).
    - **Drawing and trading secrets** (`SecretDraws`). Like human players it draws a secret whenever it can (Imperial,
      an exploration, _Impersonation_, _Strategize_), and with a full hand draws anyway and discards its weakest, unless
      every secret it still holds is worth keeping (a keep value of 0.75 or more: one it can score, one close to done or
      a good action secret); it never draws once all its secrets are scored. A full hand values the draw at what the new
      secret could add over the weakest one. When its secrets are weak it plays its outs: at the end of its turn it
      exhausts Mecatol Rex (Thunder's Edge) to trade the weakest for a new one, holding Mecatol Rex is worth that trade
      on top of the Imperial points, and with Dark Energy Tap a frontier token is worth the chance of a _Derelict Vessel_
      still in the frontier deck. When a draw leaves it over the limit with no discard buttons showing, it opens them.
- **Command tokens** (`CommandTokenPolicy`). Every gain (status phase, Leadership, Propagation) goes to the first
  unmet level of one priority list, and status-phase redistribution moves a token out of any pool above its share
  (fleet first, then strategy, then tactic) and gains it back where the list needs it, so each move brings the pools
  closer and it always settles:
  1. fleet up to its largest stack, so no ships are lost;
  2. tactic up to 2 (tactics are what turn into actions);
  3. fleet up to what a revealed _Raise a Fleet_ or _Command an Armada_ needs, once a stack can grow into it this
     round;
  4. fleet up to 3;
  5. fleet up to 4 while a dock is within two moves of an ingress, one ingress is in the seat's own slice, Styx is
     not the seat's and a stack has at least 2 ships (`FractureCampaign`: three dreadnoughts and a carrier for Styx);
  6. strategy up to 2;
  7. fleet up to its largest stack plus one (at most 5), only when that stack has at least 3 ships and other ships
     could join it;
  8. tactic up to 5;
  9. strategy up to 3, never more;
  10. the rest to tactic.

  Every fleet level stays within _Fleet Regulations_' 4, and _Raise a Fleet_ is ignored under it. A normal round
  ends near 2 tactic, 2 strategy and 3 fleet. When it must lose a token (for example for a Nekro agent), it takes it
  from the pool furthest above its share, a fleet token at its largest stack last. Redistribution during Thunder's
  Edge Warfare moves tokens only between tactic and strategy, against the split that keeps the fleet pool as it is
  (`idealKeepingFleet`); it refills whatever it took out before finishing, so it never ends a token short.
- **Strategy cards.** Every card is picked with a plan for its use (`StrategyCardPlanner`): a list of concrete
  benefits its primary would bring this seat on the current board, valued on one scale (a victory point is 8, a trade
  good 1, the same scale as tactical plans). Nothing the seat would get anyway counts, so Imperial with nothing to
  score and no Mecatol Rex is worth only the secret it draws (no more than the strategy token a follow costs).
  | Card | Uses it plans for |
  | --- | --- |
  | Leadership | the tactical actions its 3 tokens unlock (the plans it could not otherwise afford), acting first in a race for the custodians, and claiming a contested expedition slice before a rival (see below) |
  | Diplomacy | locking a system with its planets that an enemy fleet can reach (home most, by what an attack on it before the seat's next turn would cost it; more when close to winning), readying its best 2 planets (more with a spend objective revealed), and the planets an expedition exhausts or would need (see below). For the Xxcha's _Peace Accords_ it is also worth the best empty planet next to one it controls (`PeaceAccords`: the planet's value, the expansion bonus a landing there would get, the objective progress and, for a planet a rival holds, half its value again), and the card is played at once when that beats its best tactical action. Base-game Diplomacy has no buttons to resolve, so it is worth only its trade goods |
  | Politics | becoming speaker (more when it will want Imperial next round or is racing a rival for the custodians next round; a loss when it is already speaker, since the card hands the token away), 2 action cards (a point when they complete _Form a Spy Network_), looking at the agenda deck once the custodians are taken, and the expedition the 2 cards make payable (the action card slice) |
  | Construction | a space dock early, PDS (more under threat), a structure objective it would complete; for the Thunder's Edge card also production at a dock without placing a token, so the units can move out this round |
  | Trade | 3 trade goods, its commodities, a spend objective they make payable, leverage for deals, and the expedition the 3 trade goods make payable (the trade goods slice) |
  | Warfare | PoK: a command token plus one reactivation; Thunder's Edge: a free tactical action. Reactivations are a second build at home, building at home and then moving out, moving twice to reach a far system, or activating a system that holds its token |
  | Technology | the best technology it can research (and a second worth 6 resources; Gravity Drive is worth more while the custodians are still to be taken and it saves the seat a round); for Nekro, Propagation's tokens |
  | Imperial | an extra public objective this round, the Mecatol Rex point (less while an enemy fleet can reach it, half when it can take Mecatol this round), or a secret (only the strategy token a follow would cost, since following Imperial draws the same secret, except for the last seat to pick: nobody else is left to play Imperial, so the whole secret counts); plus denying the card to a Mecatol Rex holder still to pick |

  **Expedition uses** (`ExpeditionUnlock`, Thunder's Edge games only, while the seat's breakthrough is locked or the Thunder's Edge race still pays, and while a slice is open). Each is `ExpeditionOutlook.value` for the holdings after the card minus the same without it. The value is the best slice to claim now or the best to claim at the pass (see Cost below), and at pick time the seat's turn is still to come, so a slice is worth its net times the chance that no rival that could pay for it (0.25 each) takes it first. Only a slice the seat could not otherwise pay for, or would pay for with something dearer or riskier, counts:
  | Card | Expedition use |
  | --- | --- |
  | Trade | the 3 trade goods it gives (not the commodities: they arrive in later turns), unless a spend objective they make payable already claims them |
  | Politics | the 2 cards it draws, for the action card slice |
  | Diplomacy | at pick time, the cost of the planets the first claim will exhaust, for the 2 it can ready; in the action phase, the best 2 exhausted planets whose readying makes a slice payable. Either way only what exceeds the card's ordinary readying value counts (the two overlap), and the primary readies the pair the expedition needs when that beats its ordinary choice. The Xxcha agent and Bio-Stims value a planet with the same expedition gain (the readying action cards do not: valuing a card would recurse through the action card slice cost) |
  | Leadership | acting first: the value of the expedition when the claim is certain minus the value when rivals may take the slice first, halved (0.5, kept modest); never when the best slice is the secret one unless its worst secret costs at most 1 (a keep value of 0.25), since that slice wins the least |

  A Trade, Politics or Diplomacy plan with an expedition use of at least 0.5 plays the card at once, so the end-of-turn claim happens with what the card gave before a rival's turn (Leadership needs no play: the claim is the seat's first turn's end whatever it does).

  The pick adds the trade goods on a card and a small nudge (at most an eighth of a point) from how often human Nekro
  players take that card in that round (`PickPrior`), and remembers the plan. During the action phase it re-plans
  every held card from the live board each turn (`StrategyCardReplanner`), so a plan that other players spoil gives
  way to the card's next best use. A card is played before tactical actions when its plan says now (`CardTiming`):
  Leadership or Propagation with no tactic token left, Diplomacy to lock a threatened system or once planets are
  worth readying, Construction to produce or to place PDS under threat or a dock in the first 3 rounds, PoK Warfare
  once taking back a token unlocks a much better action, Thunder's Edge Warfare to reactivate a system or with no
  tactic token, and Imperial while it can score two objectives or before an enemy fleet takes Mecatol Rex, except that
  Imperial waits while the best tactical action takes Mecatol Rex (the point is then scored with the card; unless
  playing it now wins the game) and while the seat's own _Overrule_ is due first. Otherwise
  it plays the card once no tactical action is left. While it holds Warfare for a second build or to build and then
  move out, the tactical planner favours producing at home first (`SetupHint`). After playing a card it resolves the
  primary before ending its turn, paying every cost the bot leaves to the player:
  | Card | Primary | Follows when |
  | --- | --- | --- |
  | Imperial | scores the best public objective it qualifies for, then the Imperial point (Mecatol Rex) or a secret | it has a spare strategy token, and room in hand for a secret or a weakest secret worth replacing (see Drawing and trading secrets) |
  | Technology | one free technology, a second for 6 resources if one is worth it (`ResearchPolicy`: generic value, informed by the technologies Nekro players most often end games with, plus progress on tech objectives it can still finish in the rounds left, about 1.5 researches a round until the leader is 2 points a round from winning, and Space Dock II for _Produce en Masse_; minus 0.7 per resource or influence lost exhausting a technology specialty planet for a prerequisite, which it then exhausts when paying, free with Psychoarchaeology, or AI Development Algorithm when that costs less); Nekro takes Propagation's 3 command tokens instead | a technology worth at least 3 (most technologies other than the weakest) is affordable after the scoring reserve; as Nekro, Propagation's 3 command tokens for the strategy token and 4 resources |
  | Leadership | 3 command tokens, plus up to 3 more bought with spare influence, within its reinforcements | spare influence buys up to 3 command tokens (3 influence each, no strategy token) |
  | Politics | makes itself speaker (or the player furthest behind), draws 2 action cards, and once the custodians are taken looks at the top 2 agendas, burying _Public Execution_ while it leads on points, agendas that punish the planet owner while it holds the most planets, and elections it would lose on influence (a work in progress that needs a redesign: votes are unknown in the action phase) | with a spare strategy token when _Form a Spy Network_ needs cards (right after Imperial when 2 cards complete it), or with 2 or more spare strategy tokens and room for 2 cards; never with no strategy token, a full hand or _Hold No Action Cards_ |
  | Construction | the Thunder's Edge card produces at the dock its plan chose (no token placed) and then places one structure; otherwise two structures: a space dock while it has a site, otherwise PDS on planets without structures. A dock site is another planet in a home system with more than one planet, or a planet worth 2 or more resources elsewhere, preferring planets worth 3 or more and those in its own slice (next to home or on the way to Mecatol Rex; Styx counts too); while Styx is open, a planet in an ingress system of its own slice is a dock site whatever it is worth and comes first (the human Ral Nel docked on its ingress in round 1); any planet for _Fuel the War Machine_ | a revealed or held structure objective is 1 or 2 structures short |
  | Diplomacy | first locks the threatened system its plan chose (PoK card only), then readies the 2 exhausted planets it can use best (the same test as a follow), otherwise its 2 most valuable | with _Peace Accords_, a planet worth more than the strategy token can be claimed (the ready button is pressed even with nothing exhausted, and `PeaceAccordsRules` then claims the best planet the prompt offers, or declines); otherwise its best 2 exhausted planets make a spend objective payable at the status phase (even after passing), or, while it still has actions, pay at least 3 towards production it would actually build or the custodians (both need a tactic token), research (a Technology follow it still has a strategy token for, or the 6-resource second technology of its own primary; never Nekro's free Propagation) or Leadership tokens (within its reinforcements). It readies those planets. |
  | Warfare | Thunder's Edge: a tactical action without a command token, which may reactivate a system that already holds its token (for example a second production at home), at the target its plan chose while that is still close to the best; PoK: takes back the token whose removal unlocks the best tactical action | producing at home is worth as much as a tactical action's production would need to be (carriers, infantry for free planets, ships for objectives, or enough filler) |
  | Trade | the bot resolves the primary; an AI holder then announces X−k terms per player (k = 2 for 4 or more commodities or a player ahead of it, otherwise 1; none for a player close to winning, the leader, a bad payer or one the commodities would let score) and sends each follower the deal: their commodities for that many minus k trade goods (k debt when they are not neighbours, k trade goods after Replenish and Wash), an even wash for Hacan and for players who spent a strategy token | for free when an AI holder's terms are worth it (it holds its own _Trade Agreement_, gains commodities, and the deal or the debt is worth more than what it gives up), always as Hacan, and with a strategy token for a human holder only for 4 or more new commodities and somewhere to wash them |

  Buying tokens with influence uses only planets worth more as influence than as resources, and keeps what a spend
  objective and (when its ships can reach Mecatol Rex) the custodians need. Bought tokens are only gained once the
  influence is actually paid. While another payment or token gain is still open, it waits up to 90 seconds before
  deciding on a Leadership, Technology, Warfare or Diplomacy follow, instead of declining it.

  **Secondaries in order** (`SecondaryQueue`): a played card resolves its primary first, then each other player
  follows or declines in turn, starting after the card's holder and going clockwise (the same order the bot prints for
  the card). An AI waits for its turn: it does not follow or decline while the holder's primary (an AI holder's, until
  its last step) is open or an earlier player has not answered yet, or an earlier AI seat is still choosing its
  technology, paying for it, placing its structure, readying planets or building. An AI holder does not end its turn,
  play a second card or act again until every other player has followed or declined and the AI followers have
  finished their steps. It waits for a human as long as it takes; an open step of an AI seat is given up after 10
  minutes. A human holder's primary cannot be seen, so AI followers only wait for the players ahead of them.

  Follows that cost a strategy token are ranked Imperial, Technology, Diplomacy, Warfare, Construction, Politics,
  Trade; while 2 cards would complete its _Form a Spy Network_, Politics moves up to just after Imperial. When several
  cards wait for an answer it takes the best first, and it keeps one strategy token for each better card another
  player still holds unplayed, or has played while the seat waits on a payment, that it would want to follow, so a
  token is never spent on Diplomacy while Imperial is still to come. The one exception is a Politics follow that
  completes _Form a Spy Network_: a victory point is at stake, so it follows with any strategy token its scoring
  reserve does not need.
- **Agendas** (`ti4.ai.agenda`). Planets ready again at the end of the agenda phase, so votes cost nothing:
  - **Voting** (every faction except Nekro): it votes for the outcome that is best for it: itself for elections that
    give a point or a benefit (and any election while it holds _Drive the Debate_), the leader for _Public
    Execution_, its own planets for beneficial planet agendas and others' for harmful ones, the _Seed of an Empire_
    side that scores for itself, "For" on _Mutiny_ when it would carry. Planet agendas that need a trait (_Holy
    Planet of Ixth_, the _Research Teams_, _Core Mining_, ...) only go on planets with that trait. It votes
    everything when a point is at stake, half otherwise, and abstains on agendas it has no stake in. It only
    answers the outcome prompts its own vote opened.
  - **A rival's _Imperial Rider_.** When another seat's _Imperial Rider_ sits on an outcome of the agenda, the seat
    votes all of its votes for the outcome that leaves it best off in the forecast with those votes counted (the rider
    costs the seat what a point is worth to the rival, in full when it wins them the game), whatever it thought of the
    agenda, and as speaker breaking a tie it picks the tied outcome that does the same. _Mutiny_ keeps its own rule,
    and agendas without such a rider are voted as above.
  - **Nekro** cannot vote (Galactic Threat): it never plans a ballot unless the Xxcha commander lets it. Once per
    agenda phase it queues Galactic Threat as an "after" when
    another player has a technology it could gain, predicts the outcome most likely to win (the election of the
    player with the most votes), and on a correct prediction takes the best technology (`CopiedTechPolicy`, which
    also caps copied faction technologies at its two Valefar Assimilators). It never researches: every research is
    replaced by Propagation's command tokens, all of them, within its reinforcements.
  - **Xxcha commander (Elder Qanoj)**: the bot's vote count adds one vote per planet, so the AI's ballot, the planets it
    exhausts for it (influence-less planets included, highest first until the ballot is covered) and the forecast
    all use it; a rider owner or a Nekro with the commander keeps voting (`XxchaCommanderVotingTest`).
  - **Forecast** (`AgendaForecast`). For an agenda that elects a player, a planet or For/Against, the chance of each
    outcome winning, from public information only: the votes each seat had at the reveal less the votes already cast,
    which players have voted (those before the active voter), and the riders placed (a rider's owner casts nothing
    without the Xxcha commander alliance, nor does a seat in the _Assassinate Representative_ list, nor Nekro). Every
    seat that still has votes casts them all for one outcome drawn from a softmax over what the outcome is worth to it
    (temperature 4 trade goods): a point election is worth the point to the elected seat and minus a quarter of a point
    to everybody else (the whole game when it wins the elected player the game), any other agenda is its policy score
    clamped to two units of 4 trade goods (never reading Drive the Debate for another seat), a seat's own placed rider
    adds its payoff and a rival's _Imperial Rider_ takes away the rival's point (`RiderPayoff`). A seat that sees
    nothing between the outcomes (within one trade good) abstains three times in ten and otherwise chooses at random.
    256 draws, one per seat and sample that every hypothetical (`withRider`, `withVotes`, `without`) shares, decide
    the winner by the most votes, a tie going to the speaker's own choice. The draws are seeded by the game, agenda and
    viewer, so the same table gives the same numbers. It is empty in fog of war, Hidden Agenda and Omega modes, outside
    the agenda phase and for agendas it cannot list the outcomes of, and the readers below then use the votes at the
    reveal as before. Galactic Threat predicts the outcome with the best chance; the Political Secret, Political Favor
    and "elected to a point" tests take expectations over it, which makes them a little less sure than the single
    expected winner they used to assume: a 9 vote leader now wins about three times in four, so the leader has to be
    7 points ahead, not 6, before Political Favor is worth discarding the agenda.
  - **Shenanigans courtesy** (`ShenanigansRules`). When the winner is announced the bot asks every player to confirm they
    play no _Deadly Plot_, _Bribery_, _Confounding Legal Text_ or _Confusing Legal Text_ (generic buttons that add a
    reaction). Each AI seat presses each of those buttons once, 5 to 25 seconds after the message, a delay seeded by the
    message and never by the hand: the bot pre-reacts for seats holding no action cards, so whether a seat has to answer
    is public. It never plays those cards (the bot has no automation for them), so confirming is its true answer.
  - **Queued entries.** A seat whose "when" or "after" slot holds an entry (a Political Secret or Political Favor
    note, Galactic Threat) never presses the "Pass On" button the bot posts for it, which would unqueue it, and never
    holds its turn while the entry waits (`AgendaQueue`). It used to wait quietly, which meant it never answered the
    Sabotage window of another player's _Veto_ and the agenda stalled until the 24 hour ceiling.
  - As speaker breaking a tie it applies the same preferences. It only breaks real ties while it is the active
    speaker, never an agenda a player is resolving by hand.
- **Nekro's leaders** (`NekroLeaderRules`).
  - **Agent (Nekro Malleon).** At the start of its turn, once per round, it exhausts the agent, names itself, and pays
    for the 2 trade goods with an action card it can spare (it never plays action cards, and keeps 5 while it holds
    _Form a Spy Network_). The bot gives the trade goods first and trusts the player to pay, so the payment rule runs
    for every AI seat, including when a human's Nekro agent names it: it discards a card, or else gives up a
    command token from the pool furthest above its share, then closes the prompt. If the discard prompt has not
    arrived yet it waits for it rather than closing the prompt unpaid.
  - **Hero (UNIT.DSGN.FLAYESH).** Once the hero is unlocked (3 scored objectives) it plays it as a component action,
    which costs no tactic token, when a planet with a technology specialty in a system holding its units is worth it:
    half a point per trade good (the planet's resources and influence), the best technology of that colour it does
    not own (`CopiedTechPolicy`), and the other players' units the hero destroys there. It walks the component
    action menu, the planet and the technology, then ends its turn. The hero is purged as soon as it is played, so
    it never starts it without a worthwhile planet.
- **Action cards** (`ti4.ai.actioncards`).
  - **Scoring decides.** Victory points are the first reason to play, keep or cancel a card, valued on one scale
    (`ScoringStakes`): a point is worth 6 trade goods, rising to 12 near the goal, and a score that reaches the goal is
    worth 30. A *contender* is a player within 2 points of the goal, or whose points in reach by the next status phase
    would get there. The points in reach are the public objectives the player qualifies for (limited by its scoring
    chances), Imperial's extra public and the Mecatol Rex point while the player holds Imperial, and the chance that
    one of its secrets is ready. `SecretOdds` works that chance out by card counting, as a human would: the unseen
    secrets are the deck less every scored secret and the viewing seat's own hand; each one is judged against the
    player's public board (certain when the player meets it, half when one step short, nothing otherwise, 0.15 for
    secrets the objective model does not cover); the player's chance is the chance that at least one of the secrets it
    holds is ready, and only the number of secrets in its hand is read. Nothing in this section reads another player's
    hand.
  - **What a card is worth** (`ActionCardPrior`, `ActionCardValue`). Human players' statistics for the Thunder's
    Edge action card deck (`data/ai/action_card_stats.json`, made from a pasted statistics report by
    `scripts/ai-action-card-stats.mjs`) give every card an Impact Score, win, play and cancel rate, and for single
    copy cards how often it is played again after the first time (almost always after a card like _The Codex_ took it
    out of the discard pile). The file is a prior, not a truth: players who play more cards win more, and a few
    factions play far more cards than the rest. From it each card gets (one action card is worth 1.2, a trade good 1):
    | Figure | Formula | Use |
    | --- | --- | --- |
    | strength | the mean of the Impact Score over the average of 30 (a card with few recorded plays is pulled halfway to 1) and, for the 62 cards the experts ranked, the expert strength; kept between 0.5 and 2 | how good the card is |
    | keep prior | 1.2 × (0.5 + 0.5 × strength), so 0.9 for _Maneuvering Jets_ up to 1.8 for _Overrule_ | what holding the card is worth before looking at the board |
    | cancel appetite | (human statistics only) the card's cancel rate, shrunk towards the average with 50 plays, over the average cancel rate of 4.4%, at most 4; 0 for a card that cannot be cancelled | how much human players want the card cancelled |
    | first-play rate | single copy cards: first plays over the 1072 games a copy is drawn in; four-of cards: the play rate | what the self-play report compares each card's rate with (never a decision input) |
    | retrieval | single copy cards: replays per first play (0.065 on average) | choosing a card from the discard pile, later |
  - **A fresh secret** (`SecretDrawValue`). The experts rank drawing a secret among the most valuable things a player can do,
    so a secret is worth what it will probably score: the mean readiness of the secrets nobody has scored (the unseen
    pool of `SecretOdds`, judged against the seat's own public board) raised towards 1 by a quarter for each round the
    game is expected to last (the leader's points to go over 2), times what a point is worth to the seat. Only one
    secret scores in each status phase, so secrets already in the hand take rounds away, and a full hand makes a draw
    worth what the new secret could add over the weakest one (at least the swap, 0.3), or 0.3 at most when every
    held secret is worth keeping. Following _Imperial_, an exploration's secret, _Impersonation_ and _Strategize_ use it. Picking _Imperial_ only for its secret still counts just the strategy token it saves over following, unless the seat is the last to pick and nobody else could play Imperial for it to follow.
  - **Expert tiers** (`ExpertTiers`, `data/ai/action_card_expert_tiers.json`). A podcast tier list by three experienced players
    ranks every card from _Ancient Burial Sites_ to _Overrule_ (S, A, B, C, D, best first within a tier). The Impact
    Score misjudges some cards (_Impersonation_ and _Leadership Rider_ rate lower than the experts put them, _Morale
    Boost_ higher), so each tier is a range of strengths, spread evenly from its best card to its worst: S 2.2 to 1.8,
    A 1.7 to 1.4, B 1.35 to 1.05, C 1.0 to 0.8, D 0.75 to 0.45. A ranked card's strength is the mean of the two
    sources; a card the experts have not reached keeps the statistics alone. Cancel appetite stays with the human
    statistics.
  - **Keeping and discarding** (`AiActionCards`). A card is *usable* when a rule of the AI plays it: the economy
    cards, _Summit_, _Sabotage_ and the cards in the table below. A card no rule plays is worth 0 and goes first;
    between such cards the lower keep prior goes first. A usable card is worth the larger of its keep prior and what
    playing it is worth now (at most 3); a second _Sabotage_ in the hand is worth 0.6 of the first. Discards (hand
    limit, _Form a Spy Network_, Nekro's agent) follow this order. Nekro's agent only spends a card worth less than
    its 2 trade goods.
  - **When it plays.** Only on its own turn, in the action phase, and a component action card is the turn's action:
    the turn ends once it is resolved. A component action also stalls: it takes the turn without committing
    anything while the other players keep acting, which is worth 1.5 trade goods (`Stall.ACTION_VALUE`, the same
    figure relics, _Enigmatic Device_ and the _Sling Relay_ use, more while it holds _Prove Endurance_ and can outlast
    the table) as long as at least one other player has not passed.
    That is added to what every component action card does now, even a card whose effect gives nothing (_Industrial
    Initiative_ with no industrial planet is a pure stall). A card that cannot legally be played (no legal target, _War
    Effort_ with no room for the cruiser) has no value. The
    economy cards are _Mining Initiative_ (trade goods equal to its best planet's resources), _Industrial Initiative_
    (its industrial planets), _Economic Initiative_ (readying its spent cultural planets, valued as Diplomacy's ready
    is: the resources and influence that would be spent this round on production, research, tokens, the custodians
    or a spend objective, plus the points a spend objective would score), _Rise of a Messiah_ (3 or more
    planets), _Frontline Deployment_ (on a planet a dock or carrier can pick the infantry up from, or next to enemy
    ships) and _War Effort_ (a cruiser where the fleet pool and cruiser limit allow it). A card that unlocks a spend
    objective is worth the points it scores (30 if they win the game). Economy cards and the cards below are played
    when what they do now, with the stall, is worth at least their keep prior (half of it near the hand limit), either
    instead of a tactical action that scores less, or when it is about to pass. Among the cards it may play it spends the
    one with the largest margin (what it does now less its keep prior), so a card that is worth little in the hand goes as a
    pure stall before a good one is wasted as one. Seats on the earlier rules (the A/B
    flag) play the economy cards only when about to pass and worth 2 (1 near the limit) without the stall.
    _Summit_ is pre-played as soon as it is drawn.
    | Card | Worth | Target |
    | --- | --- | --- |
    | Fighter Conscription | 0.5 per fighter that fits the capacity of a system with its dock or capacity units and no other player's ships | none |
    | Uprising | the resources it gains (plus the score they unlock), plus a quarter of the planet's resources and influence while its owner has not passed, times the owner's standing, plus the points exhausting the planet would take from the owner's spend objective | a readied planet outside a home system, the best by that value |
    | Insubordination | a tactic token (0.75), plus 0.3 per unspent resource (3 to 6) when it takes the last token of a player who has a dock and has not passed, times the owner's standing | a contender first, else the strongest player with a tactic token who has not passed |
    | Spy | an action card (1.2) times the owner's standing | a contender first, else the player with the most action cards |
    | Ghost Ship | a destroyer (1), 1 more next to its own ships, half of what an objective gains from the extra ship (wormhole, anomaly, edge and presence objectives), and 1 for a way into Mallice from an alpha or beta system when it has no ship in a wormhole system yet | a wormhole system outside the home systems without other players' ships |
    | Exploration Probe | a frontier exploration (1.5) | a frontier token in or next to a system with its ships |
    | Archaeological Expedition | three draws from the deck of a planet trait it holds, times the value of the relic fragments in it | the best deck |
    | Refit Troops | 1.3 per infantry replaced by a mech (1 or 2), while it has mechs in reinforcements | its planets with infantry, spread out |
    | Forward Supply Base | 3 trade goods; played after another player activates a system holding its units | the player with the fewest points, not the active player when it can avoid it |
    | Focused Research | the best technology it could research at the tech tempo (and the objective it completes) less the 4 trade goods, when the scoring reserve can spare them | the technology the Technology card would pick |
    | Divert Funding | the tech tempo times what the technology it gains is worth more than the one it returns (never a unit upgrade or faction technology), plus the objective it completes | the least valuable technology it can return; it never researches that one again |
    | Plagiarize | the tech tempo times a non-faction technology a neighbour owns (and the objective it completes) less the 5 influence, paid with the planets it can best spare | the best such technology |
    | Impersonation | the value of a fresh secret less the 3 influence, paid with the planets it can best spare | none: the secret is drawn at once |
    | Counterstroke (2 copies) | the tactic token it gets back (at least 0.75) plus what its ships in that system could do once the token no longer holds them; played after another player activates a system holding its token | none: it takes the token back in the system |
    | Overrule | on Imperial, the points its primary scores beyond what the status phase would (one more public objective than the seat has scoring chances for, 2 for a stage II objective, and 1 for Mecatol Rex), valued as points, or 30 when they reach the goal, plus a fresh secret when it holds no Mecatol Rex; on Technology, what the strategy card plan values the primary at; plus a little for how often human players overrule that card (Imperial 0.5, Technology 0.16). It is held for a *points play* (Imperial with at least a point to score) or a use worth the reserve, see below | an unplayed Imperial or Technology, among the uses the reserve lets through; the card stays unplayed for its holder |
    | Strategize (4 copies) | the best secondary it could perform (Imperial: a fresh secret; Diplomacy: its ready; Construction: a structure when it nears a structure objective; Warfare: a build at home; Politics: two cards; Trade: its missing commodities), less the strategy token the bot takes at once, when no better follow needs that token | the card of that secondary among the unchosen ones and its own unplayed cards (a card another player will play this round can simply be followed), never Technology (the bot's button grants the technology without collecting its 4 resources) or Leadership |
    | Unexpected Action | the best a freed system buys (the Warfare "take back a token" test), else a pure stall; the token goes back to reinforcements | the token whose system unlocks the best tactical action |
    | Flank Speed (4 copies) | played only for a move into the Fracture its plan needs one more move for, worth that plan less 1.5 for the card | none: played right after it activates the system |
    | Pirate Contract (4 copies) | the rivals' approaches it blocks (half a point of standing per rival nearby, at most 3), else a pure stall | a legal system that is not next to its own planets or units |
    | Pirate Fleet | 2 × a contender's standing for blocking its approach, less the 3 resources, paid honestly | a legal system next to a contender and away from its own ships |
    | Mercenary Contract | the points a contender loses with the planet (and a third of the planet's value) less the 2 trade goods | an empty non-home planet a contender controls, among the first 22 the bot lists |
    | Signal Jamming | what the player would have wanted from the system: a third of the planets it could still take, a point for Mecatol Rex, times its standing | the player and the system next to or with its ships; a pure stall elsewhere |
    | Cripple Defenses | a PDS (1) per PDS on the planet times the owner's standing | a rival planet without its own PDS on it; a pure stall when there is no PDS |
    | Plague | half the infantry on the planet × 0.5 × the owner's standing | the rival planet with the most infantry, never one it has ground forces on |
    | Unstable Planet | half the planet's value if readied, plus up to 3 infantry (0.5 each), times the owner's standing | a rival's hazardous planet, never one it has ground forces on |
    | Reactor Meltdown | a space dock (4 early, 2 later) times the owner's standing | a rival's dock outside the home systems |
    | Tactical Bombardment | half the value of each readied rival planet in a system with its BOMBARDMENT units | played only when a readied rival planet is there |
    | Lucky Shot | the cost of the ship times the owner's standing | a rival dreadnought, cruiser or destroyer in a system with a planet it holds |
    | Scuttle | a fifth of the ship's cost plus the points the trade goods unlock | one ship in a system without enemies, with no planet of its own and no objective riding on it; it then finishes |
    | Seize Artifact | a fragment, a half more against a contender | a neighbour's fragment of the kind it wants most |
    | Repeal Law | the harm of a law that hurts it (an election that punishes the elected, one that punishes its planet) or the point a rival gets from a point law | a law that hurts the seat; never one that helps it |
    | Brilliance | the readying value of a spent planet with a technology specialty, else 2 for its own breakthrough | the best ready, else a breakthrough for the weakest player who is not a contender |

    Every row of this table, and the economy cards, also adds the stall. The *tech tempo* converts a technology's value to
    trade goods: technology comes from few sources, so the Technology follow pays 4 resources and a strategy token
    for a technology worth 3 (`ResearchPolicy.WORTH_FOLLOWING_FOR`), and the research cards value a technology at
    (4 + 1) / 3 = 1.67 trade goods per point (`ResearchPolicy.TECH_TEMPO`) so the two rules agree. A technology worth 3
    makes _Focused Research_ worth 3 × 1.67 − 4 + 1.5 = 2.5 and it is played; one worth 2 makes it worth 0.83 and it is
    held; once every other player has passed the stall is gone and a technology worth 3 gives 1.

    _Standing_ is 3 for a contender, 1.3 for a player alone in the lead and ahead of the seat, 0.6 for a player two
    points or more behind the seat, otherwise 1. Nekro cannot research, so it never plays _Focused Research_ or
    _Divert Funding_; _Plagiarize_ gains a technology without researching.
  - **How it plays.** It presses the card's `ac_play_from_hand` button in its private thread; when the thread has
    scrolled past it, it asks the bot to post its cards again once and waits. Where the card names a target, it picks
    it before playing and announces it in the main channel straight after, as the bot asks humans to. The bot posts the
    resolve button at once, so the AI holds its turn until every other player has answered the Sabotage window (at most
    a day), waits a few seconds more in case a Sabotage is landing, and never resolves a sabotaged card. With no window
    it still waits a while if someone could cancel with Instinct Training or the Watcher mech. It then presses the
    resolve button and the target buttons. Most target buttons are not tied to a player, so it presses one only in a
    message that addresses it and was posted after the play. If the target it picked is no longer on offer it takes the
    best one that is. It remembers the resolve button in case the channel moves on, and after an undo or a restart it
    picks a play up again from the board: the resolve button, or a target message addressed to it, but only for a card
    it played and that was not cancelled, and a target button must match the card's own id (a Technology strategy card
    button is not a research card's). Plays outside its
    own turn (_Forward Supply Base_) are tracked by the activation that triggered them, one play per activation.
  - **Master Plan** ("after you perform an action, perform an additional action"). It is played from the hand once the
    first action has concluded, when the bot offers another action ("Use Ability To Do Another Action" after a tactical
    action, "Do Another Action" after a strategy card) and a second action is worth it: the same test Fleet Logistics
    uses, a tactical action that attacks or is worth most of a point, or an Imperial that scores right away. With Fleet
    Logistics too it plays Master Plan first. Once the Sabotage window has closed on it, the seat presses the bot's
    button for the extra action and takes it the way Fleet Logistics does; a Sabotaged Master Plan gives nothing.
  - **Overrule is held for points.** Human players keep it for a late play that scores: taking Mecatol Rex and then
    overruling Imperial for its point, an extra public objective through Imperial, or both. Before the late game (no
    player within 4 points of the goal, where a point starts to be worth more) it is held for anything short of a win,
    even an extra public objective and a secret, and its own Imperial is played as usual. From then on the seat keeps it
    at a reserve of 10.8 (0.9 of a late victory point, worth 12) and plays it only on a points play, which the reserve
    never holds back, or on a use worth that much. A fresh secret and a free technology are not, so the card stays in
    the hand, above the rest of it (above a Sabotage kept against a contender) at the hand limit, until a points play
    appears. A points play is not compared with a tactical action, since Imperial may be played by its holder first.
    The reserve is lifted in the final round (someone within 2 points of the goal) for a seat that cannot win with the
    card (not a contender, and its points, those in reach and the card's own point fall short of the goal), and for any seat that is
    about to pass, which then plays the card on its best use rather than pass with it. Two waits come before a play:
    while the seat does not hold Mecatol Rex and its best tactical action takes it, the card waits for that action
    (unless the play wins the game), and a seat with its own unplayed Imperial and a points play for Overrule on it (a
    third public objective, or Mecatol Rex) overrules Imperial first and plays Imperial afterwards, two primaries in a
    round. The seat's own Imperial is held back for the card in that case, in the turn, the second strategic action
    and the final fallback. Once the card is played, the choice of card is made again from the same rules, but a played
    card is never wasted: if the reserve would hold every use back it takes the best.
  - **Overrule, playing.** The bot plays the primary of the chosen card as if it were played, but nothing is marked played, nobody can
    follow, and its message names the card as overruled with the seat's own buttons. The AI therefore starts the primary
    itself (it remembers the card as played this turn, finds the message by the word overruled and the card's emojis, which is
    all the bot's wording names it by), never
    waits for secondaries, and never touches the card's follow queue. Warfare, Leadership, Politics and the rest are left
    out until their steps can run from that message; Trade would announce a replenishment the bot refuses.
  - **Honest play.** It never plays while censured, over the hand limit, or passed against Transparasteel, and keeps
    5 cards while it holds _Form a Spy Network_.
  - **Sabotage** (`SabotagePolicy`, `SabotageRules`). Every AI seat answers each window after the same seeded delay
    (5 to 14 seconds when only AIs play, 10 to 39 minutes otherwise), whichever button it presses, including windows
    that have scrolled out of the messages it reads (it remembers each window's Sabotage button when it first sees
    it). It presses "Cancel Action Card With Sabotage" instead of "No Sabotage" when it holds a Sabotage and may play
    action cards (not censured, not over the limit, not passed against Transparasteel), the card is not its own, can
    be cancelled and is not protected by Shameix's Bane, the game is not fog of war, the window is still open right
    before the press, and the cancel value reaches the value of the Sabotage it would spend:
    ```
    cancel value = baseline + rival stake + denial stake
    baseline     = 1.2 × cancel appetite × involvement × standing
    rival stake  = what the points the card can give its player are worth to them
                   (in full when they reach the goal, otherwise a quarter)
    denial stake = what the points a card aimed at the seat would stop it scoring are worth to it
                   (30 when that is the win)
    ```
    Involvement is 1 for a card aimed at the seat (_Coup d'Etat_, _Extreme Duress_ and _Crisis_ on its turn, _Public
    Disgrace_ while it picks, a combat card played by its opponent in the system it fights in), 0.6 in the agenda
    phase and 0.35 otherwise; standing is as above (3 for a contender). The points a card can give its player
    (`CardScoring`) are: for _Overrule_, what its seat's Imperial primary would score (`ScoringStakes.overrulePlay`:
    one more public objective, 2 for a stage II, and the Mecatol Rex point) while Imperial is still to be played, and
    the whole game when that reaches the goal; 0.6 for an _Imperial Rider_ before it is placed (an _Imperial Rider_ is worth its chance of winning, 0.6 until it sits on an outcome and then the forecast's chance of that outcome, times the whole of the owner's point, which is the game for a rival at match point, instead of a quarter of 0.6 of a point); 0.5 for _Political Stability_, _Puppets on a String_ and _Master Plan_ while
    the player holds Imperial; the spend objective an economy card or _Uprising_ unlocks; and a technology objective
    that is one technology short for a research card. The points a card aimed at the seat would stop it scoring are
    what its planned strategy card scores (_Public Disgrace_, _Coup d'Etat_, _Crisis_), its reserved trade goods
    (_Extreme Duress_), the victory points riding on the system it fights in (combat cards) and the point an
    election would give it (agenda cards). The Sabotage it would spend is worth its keep prior, a second one 0.6 of
    that, and the last one at least 4 while any opponent is a contender. "Save your Sabotage for Overrule": while
    _Overrule_ has not been seen (not played this game, not in the discard pile, not purged by the game setting and not
    in the seat's own hand) the last Sabotage is also worth at least 3, except against _Overrule_ itself. With a quiet
    table that makes an uninvolved seat spend its only Sabotage on _Overrule_ alone while it is unseen, and afterwards
    on _Overrule_, _Lie in Wait_, _Rout_, _Confounding Legal Text_, _Political Stability_, _Parley_ and _Imperial
    Rider_; a card aimed at the seat, or anything that could win the game for a contender, clears the bar either way.
    A window aimed at the seat is answered before it continues a combat or its turn, and it keeps its turn while such
    a window is open for the others. When _Crisis_ is cancelled it presses the bot's "Delete These (If Crisis Was
    Sabo'd)" button, and when _Coup d'Etat_ is cancelled it plays its strategy card again.
  - **When targeted** (once the card's Sabotage window has closed). It hands a card to a _Spy_ (if it has one), gives the least harmful promissory note to _Diplomatic Pressure_
    (keeping Support for the Throne), and under _Extreme Duress_ plays its strategy card rather than lose its hand.
  - **Riders** (`AgendaCardRules`): _Imperial_, _Trade_, _Politics_, _Technology_ and _Leadership_ Rider, played as "after"s with the
    bot's queue, and worth their keep prior in the hand like any card the AI plays. In the after window, when its after
    slot is empty and it may play cards, it asks the forecast what each rider would bring on each outcome:
    ```
    gain(rider, outcome) = the seat's expected utility with the rider placed on the outcome
                           less its expected utility voting its current ballot
    ```
    The rider's own payoff (a point, 5 trade goods and what they unlock, 3 action cards and the speaker token, the best
    research it could do for free, 3 command tokens as the Leadership plan values them), the
    votes it gives up (none for Nekro or under the Xxcha commander) and the rival stake other voters see in an
    _Imperial Rider_ are all in the two forecasts. It queues the best rider whose gain reaches the card's keep prior
    (`queueAnAfter`, `queueAfter_ac_<card>`, `lockAftersIn`), one per agenda, never over a queued entry such as Galactic
    Threat and never for an agenda the forecast cannot enumerate. When the queue plays the card the bot posts the
    outcome buttons; the AI re-runs the choice on the current forecast and places the rider at once, before the Sabotage
    window closes. It does not pass on "after"s while a played rider is unplaced (and gives up after 5 minutes); it does
    when the rider was cancelled, placed, or refused by the bot (a seat over the hand limit), and when voting has begun it
    drops a still queued rider and still places a played one. The AI never re-decides once it has locked the rider in,
    and every seat decides on the same table, so several seats may ride one agenda. Winning riders pay out through the
    bot's own buttons: Imperial, Trade and Politics need nothing from the AI; for a _Technology Rider_ it presses "Get a
    Technology" on the message that names it and picks the technology at no cost (Nekro's turns into 3 command tokens
    through Propagation); for a _Leadership Rider_ it takes 3 command tokens within its reinforcements
    (`RiderPayouts`).
  - **Veto**: a "when" card, worth its keep prior in the hand. It is queued (`queueAWhen`, `queueWhen_ac_veto`) when the
    seat's "when" slot is empty (a Political Secret or Political Favor note takes priority and keeps the card) and the
    agenda's expected election costs the seat 5 units or more, the rule Political Favor uses
    (`AgendaPolicy.worthDiscarding`, the forecast-weighted policy score). When the queue plays it the bot halts the
    queue and posts the card's buttons; the seat recovers the play in the agenda phase like it recovers an unfinished
    action phase play, waits out the Sabotage window, and presses "Reveal next Agenda" (`resolveVeto`) once it is at
    least 7 seconds after the previous reveal (the bot refuses within 6 and has already counted the Veto by then). While
    the Veto is played and not resolved the seat does not pass on "when"s, which would set the halted queue going (any
    pass does, so every seat that had a Veto queued plays it in turn; with one Veto in the deck that is only a test
    artefact of dealing everyone one). It does not wait: it keeps answering Sabotage windows, since seats that each
    played a Veto would otherwise wait on one another, and the stall detector does not call it stuck. It gives up after
    five minutes if the bot never posted the buttons. A cancelled Veto resumes the queue through the bot (see
    Segregation).
  - **Quash** (Xxcha, `QuashRules`): queued (`queueAWhen`, `queueWhen_ability_quash`) after the Veto check when the seat
    has a strategy token and the revealed agenda is expected to cost it more than that token is worth
    (`AgendaPolicy.expectedLoss` over the forecast with the seat's own planned ballot, against
    `CardFacade.strategyToken()`); the next agenda is judged afresh. Without a forecast (fog) it uses the Political
    Favor rule.
  - **Combat cards** (`CombatCardRules`, `CombatCards`): _Morale Boost_ (every copy), _Fighter Prototype_ and
    _Emergency Repairs_. The bot has no card window in a combat: the start of a round is the gap before someone presses
    the unowned combat roll, and a one-round modifier is moved in by the seat's next roll of any kind (anti-fighter
    barrage, space cannon and bombardment included) and dropped by it. So the card is played at the one moment the
    seat would roll its combat round, through a hook `CombatRules` calls just before it presses that roll, after its
    own space cannon, anti-fighter barrage, assault cannon and bombardment have been dealt with. The hook is only called
    when both sides have rolled the same number of rounds (once the opponent has rolled, its dice are known), no hits
    are pending, the combat is in the active system and, for a ground combat, no one has asked to automate it (see
    below). The seat is idle at that moment whatever it holds, so the timing says nothing about its hand, and no other
    player's hand is read. A card is played when its swing reaches its keep prior:
    ```
    swing = (chance to win with the card - chance to win without it) x stake
    stake = unit cost of both sides + score value of the objective points riding on the system
    ```
    _Morale Boost_ and _Fighter Prototype_ are valued on a first round that carries the bonus (+1 to all dice, or +2 to
    fighters, in round 1 of a space combat only) before the ordinary chain (`CombatOdds.resolveFirstRound`);
    _Emergency Repairs_ on the fight with every damaged unit of the combat repaired, and only with damaged units. Both
    are read through the `CombatForecast` facade. A play is remembered per round (`acPlay|combat:<position>@<holder>#<round>:start`)
    and goes through the usual Sabotage window; while it is in play a hold rule ahead of `CombatRules` keeps the seat
    from rolling or assigning, and after a modifier card resolves the roll also waits (20 seconds at most) until the
    modifier shows in the seat's pending modifiers. A play whose combat is over is dropped, and one lost with the memory
    is rebuilt from its still visible resolve button. `-Dai.combatcards=false` switches the cards off (they are not
    played in fog of war either). The cost: a Sabotage window left open holds the combat. In human games the other
    AI seats pass after 10 to 40 minutes, a human can hold the window to the 24 hour ceiling, and the opponent AI waits
    too since the card is aimed at it, so a combat card can freeze a combat for that long. The combat cards used to be
    worth nothing in the hand and the first to be discarded at the hand limit; they are now kept at their prior.
  - **Direct Hit** (`DirectHitCards`, `DirectHitRules`, every copy `dh1..dh4`). The bot's resolve button
    (`resolveDirectHit`, then `directHitOn_<position>_<unit>_<color>`) lists the damaged ships of other players in the
    active system that `UnitModel.getCanBeDirectHit` allows (never _Dreadnought II_ or a mech) and destroys the chosen
    one through `DestroyUnitService`. The seat looks for a fresh sustain at the same round-start hook as the other
    combat cards: it remembers the damaged ships it saw at the previous round start (`directHitSeen|<position>`) and a
    ship with more damage now has just sustained. After its own space cannon fire it takes the same snapshot
    (`directHitCannon|<position>`) and compares once the opponent's hit assignment is gone. It plays on a flagship or
    war sun always, and on another ship that costs a _Dreadnought_ or more unless the fight is already won at 95%, or
    whose loss lifts its win chance by 10 points (`CombatForecast.enemyShipLoss`). The card's keep floor is 2 TG, and 3
    while it holds _Destroy Their Greatest Ship_; the target value adds that secret's point to a heavy ship. Not in fog.
  - **Ready for dice** (`ReadyForDice`, `CombatRules`). Human players press "Declare Ready To Throw Dice" in the combat
    thread when they have no more cards or abilities to play, so the AI does the same. At the start of every round
    (both sides level, no hits pending, the combat in the active system, the button still in the thread's history) each
    AI seat in the combat makes one round-start decision after a seeded pause (3 to 10 seconds against AI seats only,
    15 to 45 otherwise; the seed is the game, combat, round, seat and the seat's profile, never its hand): it plays a
    combat card if one is worth it, and when that card has resolved (or at once if none is) it presses
    `announceReadyForDice_`, once per round (`ready|<position>@<holder>#<round>`). The pause, and the press when
    nothing is played, are the same whatever the seat holds. The seat then rolls first only once the opponent has said
    it is ready (a ready message naming the seat that is newer than the round's start, which is the newest bot message
    in the thread that is not a declaration) or has rolled. Against a human who never presses it the usual 120 seconds
    after the thread's newest message still apply; against an AI seat that never declares the seat gives up after 3
    minutes. A non-active AI defender now makes its decision before the attacker rolls (it used to wait for the
    attacker's roll, so it never had a start-of-round window); it still does not roll first. Without the button (fog
    of war, or it scrolled out of the thread's history) the earlier behaviour applies: a pause after the newest
    message, then the hook, then the roll.
  - **Neutral units** (`NeutralCombat`, `CombatRules`). The neutral units of the Fracture have no seat, so nobody presses their
    dice for them unless a seat does: the rules say any player other than the active player rolls for them. In a
    combat where the active AI seat fights only neutral units, the next AI seat after it (in seating order) is
    shown the combat thread and presses the buttons the bot spoofs for the neutral player: its anti-fighter barrage
    (when it has the dice and the attacker has fighters), its round roll once the attacker has rolled the round (a
    round is rolled once, by comparing the round counters), and its auto-assigned hits (after the neutral roll of the
    round, in the order of the reference card with sustain damage first, which is the bot's own order). The attacker rolls
    at once (no pause, no combat card window) except for one pause when a barrage is due, and falls back on the
    neutral buttons itself only when no other AI seat exists after 10 seconds, or the other seat has not pressed them
    after 120 seconds.
    That seat keeps watching the thread for the rest of the round while neutral units are in the system, so the last
    hits of a combat the attacker lost are still assigned. Neutral ground forces and structures count as enemy ground
    forces and structures (`BoardView`), so the tactical action waits for a ground combat on a Fracture planet to end
    before it draws the relic and ends the turn.
  - **Barony of Letnev** (`ti4.ai.letnev`; every rule keys on the ability, technology, leader or unit, so Nekro,
    Franken and alliances get it too):
    - _Odds._ `CombatForces.force` adds the space-side Letnev edges to every force the planner and the defence
      outlook build, for either side. Non-Euclidean Shielding (each sustain absorbs two hits), the Arc Secundus
      repairing every round (`Force.repairs`, the Duranium Armor model), Munitions Reserves (`Force.rerollRounds`: the
      misses of the first N rounds are rolled again, so a die hits with `1 - (1 - p)^2`; N is the seat's trade goods
      divided by 2, at most 3 rounds) and Gravleash Maneuvers (+X on the rolls of the ship that rolls most dice, X being
      the number of ship types; the movement half is not modelled).
    - _Fleet supply._ `FleetLimit.ships(seat)` is the per-system ship limit everywhere the AI plans with it (tactical
      moves and followers, production room, opening carriers, Warfare double builds, the enemy fleet that can reach a
      system, the systems an action card may put a ship in): the fleet pool plus Mahact tokens plus 2 for Armada, unlimited while
      Darktalon Treilla is active. `CommandTokenPolicy` subtracts the same bonus from every fleet token target, so a
      Letnev seat holds two fewer fleet tokens for the same stack (also under _Fleet Regulations_, where the cap is 4
      tokens plus the bonus).
    - _Round start_ (`LetnevCombatRules`, called through the same hook as the combat cards, cards first). Viscount
      Unlenn: when the extra die for the ship with the best hit chance gains at least 1.5 (in the units of a trade good)
      in a fight the seat has at least a 10% chance in, it presses "Use Letnev Agent", then the button that names its
      own faction, waiting up to 30 seconds for the buttons so that it never rolls with the agent half-resolved. It
      never uses the agent for another player's combat. Munitions Reserves: 2 trade goods when the first-round reroll is
      worth at least 2.5 (the 2 trade goods and a quarter on top), with the same 10% floor, plus a victory point when
      the 2 trade goods are reserved for a spend objective. A fight already won loses little to a miss and a hopeless
      one is not worth the trade goods. The swing is `(win chance with - win chance without) x stake + the drop in
      expected losses` (`CombatForecast.rerollGain`, `extraDieGain`). Dunlain Reaper: in a ground combat, 2 resources
      (planned through the scoring reserve and paid through `PaymentRules`) to replace one infantry with a mech when the
      whole-fight gain, with the planet's stake, reaches those 2 resources (`CombatForecast.mechSwapGain`).
    - _Darktalon Treilla_ (`DarktalonTreillaRules`). On its own turn, before a tactical action, the seat asks the planner
      for its best plan with the fleet limit lifted. It plays the hero (component action, then the hero button) when
      that plan is an attack that stacks more non-fighter ships than the real limit and beats the best real plan by 4 or
      more. The hero is not played because it is unlocked, and the lifted limit then lets the next tactical action
      field the stack.
    - _Rear Admiral Farran_ (`RearAdmiralFarranRules`): the commander's trade good prompt is taken when it names the
      seat. _L4 Disruptors_ (`L4Disruptors`): the planets' space cannon is left out of the landing in invasion planning
      (`StrikeEvaluator`) and of the invasion threat against the seat's own planets (`DefenseOutlook`); the cannon still
      fires at the arriving ships.
    - Not handled: _War Funding_ (the bot only prints the holder's rerolled dice and leaves the rerolled hits and the
      opponent's reroll to its players, so there is nothing an AI can press safely), the movement half of Gravleash
      Maneuvers, and playing the hero for a production overflow.
  - **Ground combat automation.** Pressing "Automate Combat" asks the opponent to confirm; only then do the bot's
    automated rolls run, and they move a pending one-round modifier in on each side's first automated roll
    (`CombatRollService` takes the same path as a manual roll), so a _Morale Boost_ played first applies to round 1
    of an automated combat. An offer nobody has accepted therefore no longer blocks ground combat cards. When the
    opponent asks the AI to confirm, the AI first makes its round-0 decision (cards, after the same seeded pause) and
    confirms afterwards, whatever it played. Once the confirmation is on the table no ground card is played, and the
    AI never starts an automation itself.
  - Not yet: the other combat cards (see the known limitations), _War Machine_, _Rally_, _Parley_,
    _Exchange Program_ (its agreement needs a negotiation, and the other way costs both players a fleet token) and the
    remaining component action cards. Every other card of the deck's Action window is played, so none is discarded as
    unusable while it could stall; a test over the deck keeps it that way.
- **Promissory notes** (`ti4.ai.promissory`). It plays the notes other players gave it, only through the bot's own
  buttons and only in the note's timing window:
  - _Trade Agreement_ when its owner replenishes (if they have commodities), _Research Agreement_ for a technology
    worth having, _Military Support_ at the start of Sol's turn before Sol acts (the infantry go where a dock or
    carrier can pick them up), and _Gift of Prescience_ pre-played as soon as the bot offers it.
  - _Political Secret_, queued as a "when", when silencing its owner would change who an election picks for the better,
    and _Political Favor_ when the predicted election is clearly bad for it and Xxcha has a strategy token.
  - At the start of a combat, before either side has rolled: _Antivirus_ against Nekro when Nekro could copy one of its
    technologies, _Tekklar Legion_ in a ground combat it does not already dominate, and _Greyfire Mutagen_ against 2 or
    more non-Yin ground forces.
  - _Ceasefire_ when its owner activates a system holding the AI's units and has ships that could move in.
  - Never _War Funding_ (the extra hits need manual assignment), and it never plays _Trade Convoys_; _Support for the
    Throne_ and _Alliance_ need no play. When a note's play button has scrolled out of its thread, it presses one it
    remembers or asks the bot to post its cards again.
  - Planning charges a victory point for handing back _Support for the Throne_ (and a little for other notes that go
    back when it activates their owner's units), and _Betray a Friend_ counts the notes held when its action began.
  - Against it, without looking at other players' hands: while its own _Ceasefire_ is out of its hand, planning
    discounts moves into a system by the chance that whoever holds it has units there, and it waits 2 minutes before
    moving into a system where a human has units; if its _Ceasefire_ comes back during that action, it moves nothing
    into the system. Under _Political Secret_ Nekro does not use Galactic Threat.
- **Trading** (`ti4.ai.trade`). The game state is the source of truth: a deal is read from the transaction items the
  offerer holds, never from message text.
  - **Valuation**, in trade goods. A trade good or a received commodity is worth 1; its own commodity 0.6 when it could
    wash it with someone else, otherwise 0.3; debt it is owed is worth the debtor's trust × how soon they can pay (0.9
    while they can trade with it, less otherwise) × 0.5 near the end; promissory notes as below. A victory point is
    worth 6 trade goods, rising to 12 near the goal and 30 for a winning score, so a deal that completes a spend
    objective is worth paying extra for (**desperation**, never more than 3/4 of the point).
  - **Promissory note values**, the same for the note given and the note received, from public information only:
    - _Support for the Throne_ (`ThroneValue`, its weights in one record). Received: the point (6 to 12, or 30 if it
      wins) × the chance of keeping it for the rounds left (a small loss per round, more beside its owner and per
      system the two share), less the systems it fences off for every round left (beside the owner, each shared system,
      Mecatol Rex while the owner sits on it), so an early swap with a neighbour rarely pays. Its own given away: the
      option of a better deal once the threats are clearer (1/8 of a point per round left, at most 3/4), less the
      peace it buys on a border (the receiver now loses the point for
      activating its units; more against a stronger, more aggressive neighbour). The point handed over is charged by
      the stinginess term like any other gain of the partner's, so it swaps with a peer and never feeds a leader or a
      player one point from winning. It is never the "least harmful" note a TBD request takes.
    - _Alliance_ (`AllianceValue`): its owner's commander, rated per faction (Keleres 3, Jol-Nar 2.5, Hacan, Xxcha, Sol
      and Nekro 2, others 0.5 to 1.5), half while locked; nothing for Mahact. Its own costs 0.3, so it sells one for 1
      or 2 trade goods and swaps them freely.
    - _Trade Agreement_: its owner's commodity value less 1.
    - _Political Secret_: 1 per agenda phase still to come, through round 5's (the current round counts once the
      custodians are taken).
    - _Political Favor_ given by its owner (the Xxcha): a quarter of a strategy token per agenda phase still to come,
      since a holder who plays it costs a token but the card comes back.
    - _Ceasefire_ (`CeasefireValue`): traded under duress. A seat is under duress from another whose fleet could take
      its home system or the Mecatol Rex it holds (ships worth at least 8 resources can reach it, and at least 1.5 times
      what defends it): it values that seat's
      Ceasefire at 3/4 of the stake (a point's worth + 2 for home, half a point for Mecatol) and its own at 0.5 to them.
      Otherwise its own costs 2.5 (or half of what it could take from the receiver), and one received is worth 0.5 from
      a neighbour, 0.2 from afar.
    - Anything the engine cannot enforce (votes, attacks, territory) is not part of a deal and is worth nothing: every
      deal it values is delivered in full when it is accepted.
  - **Stinginess** (public information only). It subtracts what the deal gives the partner, weighted by how far the
    partner leads (fully for a player close to winning), and refuses to give a point to a player who could win with it
    or to a clear leader. It accepts deals worth at least 0.3 to it (from an AI) or 0.5 (from a human) after that, and
    prices its own offers to another AI by working out what that AI would accept.
  - **Trust.** Human players start at 0.8 and AI seats are always 1: +0.1 for accepting its offer or paying debt, −0.3
    for not paying for a free Trade follow, −0.2 for not paying a debt it collects. It lives in the AI's memory and
    resets on undo or restart.
  - **What it starts.** On its own turn, before its action (at most 2 offers a turn): paying its own debt, buying the
    trade goods it is short for a spend objective (the cheapest package of commodities, debt or a note the lender would
    accept, at most twice a round), a 1:1 commodity wash, a promissory note deal, a relic fragment deal, selling commodities when nobody has
    any to wash, and collecting debt from human players. A **note deal** (`NoteDeals`) is the best of: one of its own
    colour notes sold for 1 to 6 trade goods, the partner's _Support for the Throne_ or _Alliance_ (while in their hand,
    which their play areas show) or, under duress, _Ceasefire_ bought for 1 to 6, or a one-for-one note swap with or
    without a trade good; it must clear the usual margins for both sides (at most 2 a round, one per partner). The
    partner's note is picked in the builder by its name, never by its hand number. A **fragment deal**
    (`FragmentDeals`) is the best of: one fragment sold for 1 to 4 trade goods, one bought for 1 to 4, or a one-for-one
    swap of two different kinds with or without a trade good, again within the usual margins for both sides (at most 2
    a round, one per partner). A fragment that completes a set is worth 1 more in a trade (the relic comes sooner), so
    the last fragment of a set sells for 3 trade goods (the seller's lone fragment costs 2, the buyer gets 3 + 1), a
    lone fragment that completes nothing is not worth 2 to anyone else, and the swap of two lone fragments that each
    complete the other side's set clears best. The builder picks fragments by kind and amount (`Frags_<kind><n>`). In the agenda phase: paying debt (with the "Send" buttons of the bot's debt
    reminder, never "Erase"), collecting debt and washing (with another AI only when its faction sorts first). At
    most 12 new offers an hour, 3 waiting for an answer, and one unsolicited offer per human player per round.
  - **Trade card.** As holder it bills each follower once (its pending offers wait up to 30 minutes for trade goods to
    pay with), and tells a strategy-token follow from a free one by the follower's strategy tokens. As follower it
    holds its commodities back for the holder's bill for 10 minutes (it neither offers them nor accepts another
    player's deal for them meanwhile) and honours the announced shape of the deal.
  - **Builder.** An offer is built with the bot's transaction buttons, one press per tick: `transaction`, the
    partner, what it asks for, what it gives (so an old Accept pressed during the build can only make the partner
    pay), then "Send the Offer". A counter-offer starts from "Reject and CounterOffer" on the offer it received.
  - **Rejections** (`OfferRejections`). It reads the bot's "your offer ... has been rejected" notice in its private
    thread, rescinds the rejected offer and makes no new unsolicited offer to that player for the rest of the round.
    When the rejected offer asked a human for a promissory note (or a relic fragment) it asks in table talk what they would want for it;
    their next offer is the answer, taken if it clears the usual margin and otherwise rejected (no counter-offer), and the
    note deal with that player then waits until the next round. A rejection from an AI seat drops the note deal the
    same way.
  - **Recovery.** An offer counts as accepted once its items with the partner are gone. It rescinds an offer that
    gets no answer in time (5 minutes for an AI; for a human, until the phase ends, at most 12 hours or 24 for a Trade
    bill or a debt collection), whose window has closed, that it can no longer cover, or that crossed the partner's
    own offer. After an undo or restart it adopts an offer whose Rescind button is still showing, or clears the items
    it left behind.
  - `-Dai.trading=false` switches trading off: it rejects every offer, follows Trade only for free from another AI,
    and as Trade holder lets everyone follow for free.
  - **Self-play statistics.** Every accepted offer is logged (round, parties, items). The report lists per seat its
    commodities, debts, offers sent, accepted, rescinded and countered, the trade goods and commodities that moved, and
    Trade settlements accepted out of sent, followed by `TRADE_INVARIANT` lines. A batch prints the trades per game,
    the acceptance rate and the trade goods gained by trading per seat. Promissory notes are tracked on their own
    (`PromissoryTracker`): every change of holder between ticks, every play (from the engine's play event), the notes
    still unplayed in another seat's hand and those that never left their owner; a batch prints these per note.
- **Checks and Balances.** When it must give its picked card away, it plans the card and the recipient together,
  among the players who can still receive a card: if the strongest of them is ahead of it (or leads the game), it
  gives them the card least useful to them; otherwise it gives the player with the fewest points the card most
  useful to them. The trade goods on a card, which the picker keeps, make that card a little more attractive.
- **Tactical actions.** At the start of its turn, `TacticalPlanner` scores every system it can
  activate and remembers the best plan for the turn:
  - **Expand**: a carrier (or other transport) takes ground forces (infantry first, mechs last, except
    that mechs go to hazardous planets first) to free planets, keeping at least one ground force (a mech when there is one) on each home planet, more when the defense model below asks for them, and one on Mecatol Rex, and avoiding systems
    covered by enemy space cannon. In rounds 1-4 every planet taken is worth an extra 0.8, fading to nothing by round
    8, so small planets are worth a token early. It plans to take every free planet in the system: when one transport
    cannot carry a ground force for each planet (and a second for each hazardous one), more transports from the same
    system go along (0.3 per extra ship). Capacity is used: the transports fill up with every spare ground force,
    except what the transports staying behind need for the best system they can reach this round, and then with
    fighters that escort them (0.15 each; the fighters the defense model wants at home stay). Each ground force
    beyond the planets taken is worth 0.5, up to the free planets within one move of the target, since it moves on
    next round. Every ground force it brings lands: a mech on each hazardous planet it can, then one force on every
    other planet, then a second infantry on each hazardous planet (the cards that remove one become nearly free),
    then the rest spread over the planets.
  - **Territory** (`Territory`). Every system belongs to somebody, by hex distance alone (anomalies, ships and hyperlanes
    ignored): the seat's home, the _lanes_ (the shortest paths from its home to Mecatol Rex, up to the point where a
    rival is closer), its _slice_ (closer to its home than to any other), the _border_ (as close to a rival as to
    its home), a rival's area, and the rivals' homes and Mecatol Rex themselves. Every planet an expansion takes is worth
    1.5 more on a lane, 1.0 more in the slice and 0.3 more on the border, and costs 1.0 in a rival's area while the game
    is young (fading to nothing after round 4). An attack that lands on a planet of the lane, slice or border gets the
    same bonus, but no penalty elsewhere: attacking into a rival's area is the offensive planner's job.
  - **Holding Mecatol Rex.** What holding Mecatol Rex is worth beyond its planet is the Imperial point of the rounds ahead
    (`MecatolStake`): a quarter of a victory point for each of at most two later rounds, nothing in the last one. It
    counts for the seat in an attack or expansion that takes Mecatol Rex, as denial against the holder it is taken
    from, in what a Mecatol Rex the seat holds is worth defending, and in what a counterstrike on it would cost. Taking
    Mecatol Rex angers its holder half as much as raiding a home system.
  - **The race for the custodians** (`CustodiansRace`, public information only). For every seat it works out when a
    loaded transport (a ship with capacity and a ground force in its system) could land on Mecatol Rex. A transport hops
    once a round, as far as its move allows (Gravity Drive adds one; a seat with a propulsion technology is assumed to
    research it), and not at all from a system holding its own command token or once the seat has passed. The arrival is
    pushed back a round when the seat could not pay the 6 influence, or, for a later arrival, when its planets, trade
    goods and free planets in reach would not make 6, and is empty under another player's space cannon. A seat that arrives
    in the same round as a rival wins it by the order of play (the Naalu 0 token first, otherwise the initiative when it is
    known, a coin flip when it is not), and a rival that arrives sooner usually wins. Mecatol Rex is worth the
    custodians' point, its hold value and its planet. This drives: **approach moves** (a loaded transport that cannot
    land this action moves one system closer, worth half of the race it wins, and an expansion that brings a spare
    ground force gets the same bonus); Leadership (acting first in a same-round race); Politics (the speaker token, when
    the seat and a rival both arrive next round); the research of Gravity Drive; and the influence it keeps for the
    custodians while a loaded transport could land.
  - **Attack** (the model is described under **Offense** below): a strike moves ships, and the ground forces they carry,
    from one to three systems into a system holding a single other player's units or planets, and lands on the planets
    there that are worth it, free ones included. Its exact odds (`CombatOdds`, with standing combat modifiers such as
    Fragile or Unrelenting) count its own space cannon (PDS in the system, PDS II and other deep space cannon next to
    it) firing first; the enemy's space cannon offense on the arriving ships (a system covered strongly enough to
    destroy the fleet is simply a strike that cannot win) and a PDS on the planet shooting at the landing (it sends one
    ground force more than the hits it expects when nothing defends the planet); Assault Cannon on both sides (the
    cheapest non-fighter ship lost); anti-fighter barrage on both sides; Non-Euclidean Shielding (a sustained hit
    cancels two); Duranium Armor repairs; X-89 ΩΩ doubling ground combat and bombardment hits; the expected
    bombardment hits on the planet the bot will bombard; and Naalu's Foresight (a stack that would lose and has a
    strategy token and an empty system beside it leaves before the combat, so nothing is killed). Damaged ships join
    attacks but can no longer sustain damage, and damaged defenders cannot either. It also weighs the attack with the
    fighters at the origin riding along in the fleet's capacity (the infantry and mechs to land share what is left)
    and takes whichever plan scores better; the ground odds use the infantry and mechs actually landed, and ground
    forces that rode in a destroyed ship never land. It always clears enemy ships out of its own home system.
  - **Produce** (`ProductionPlanner`): build at a space dock, scoring first. Activating a system to build is only
    worth it for at least 4 units, unless the build scores or advances an objective. In order:
    1. **Scoring.** A flagship or war sun (the cheaper) while _Engineer a Marvel_ is unscored, one that could reach
       Mecatol Rex or another home from that dock for _Achieve Supremacy_, and the flagship itself for _Unveil
       Flagship_. Destroyers to stack five (or eight) ships for _Raise a Fleet_ (_Command an Armada_) unless a big
       enough stack already exists; the fleet pool grows to match what it can field, never past 4 under _Fleet
       Regulations_. Dreadnoughts up to five for _Gather a Mighty Fleet_. Two ships with bombardment (dreadnoughts
       first) for _Make an Example of Their World_ and ships with anti-fighter barrage (destroyers) for _Fight with
       Precision_: two anywhere on the board, but three at the dock itself when a destroyer trip from it reaches a system
       holding enemy fighters. A few destroyers to move around for presence objectives.
    2. **Core.** Carriers up to two, the infantry the dock is short of a carrier load, and two mechs (four for
       _Mechanize the Military_).
    3. **Fracture fleet.** At the dock nearest an ingress (within two moves) while Styx is not the seat's, a carrier,
       three dreadnoughts and six infantry counted in that system, the smallest fleet that beats the guard of Styx,
       at 0.75 a resource. After the surplus ships, that dock also fills the free capacity with fighters at the same
       value.
    4. **Surplus.** Its flagship, a war sun when it has the technology, dreadnoughts up to three, upgraded cruisers
       and destroyers up to four, then plain cruisers up to two. Each only while the resources left could still fill
       the rest of the production with cheap units. Every flagship is worth building with money to spare, ahead of
       dreadnoughts, and its tier in `FlagshipRating` sets how much more it needs on top: nothing for S (Nekro,
       Ghosts, Nomad, L1Z1X, Deepwrought, Crimson Rebellion, Xxcha), 1.5 resources for A (Yin, Sol, Naalu, Yssaril,
       Empyrean, Keleres, Naaz-Rokha, Mahact), 3 for B (Jol-Nar, Winnu, Letnev, Mentak, Cabal), 4.5 for C (Muaat,
       Saar, Titans), 6 for D (Argent, Arborec, Ral Nel, Sardakk, Hacan), and 3.75 for a flagship not on the list.
       Plain destroyers are only built for objectives.
    5. **Fill.** The rest of the dock's production: fighters into the dock's 3 free fighter slots and the spare
       capacity of its ships (each carrier keeps room for two infantry, so fighters never block an expansion), then
       infantry.

    It stays within production, fleet supply, reinforcements and the reserve, using Sarween Tools when it has them,
    and pays keeping the same reserve. A build is scored by what it is for: carriers and the infantry the dock is
    short of keep their value, ships for objectives are worth 0.5 per resource (plus 0.3 of a point split over the
    units _Unveil Flagship_, _Make an Example_ or _Fight with Precision_ need), and everything else is filler, worth
    less in rounds 1-4, so a free planet wins the last token over filler.
  - **Position**: move the cheapest ship that helps (or a group, to stack a fleet) into a system that advances a
    presence objective (_Intimidate Council_, _Explore Deep Space_, _Populate the Outer Rim_, _Make History_,
    _Raise a Fleet_, _Achieve Supremacy_ with its flagship or a war sun, and secrets such as _Control the Region_,
    _Cut Supply Lines_ beside an unguarded space dock, and _Foster Cohesion_). It may enter a system where another
    player has planets or structures but no ships. A carrier-type ship (capacity 3 or more and no sustain damage:
    a hauler) that would arrive with nothing aboard pays half a point per slot it leaves empty, so a destroyer or
    cruiser makes the presence move and a carrier only does when it completes something worth the slots. The same
    charge applies to a staging group's empty haulers, to an idle hauler following from a different system, and to a
    hauler that attacks on its own with no ground forces or fighters.
  - **Fight with Precision** (`PrecisionRules`). The barrage dice of the ships in and arriving at a system give the chance
    that they destroy all of an opponent's fighters there (destroyer 2 dice at 9, Destroyer II 3 at 6). An attack counts
    that chance of a point (a quarter when patient) among the combat secrets, so it competes with the other secret the
    combat meets and does not need the combat to be won. Positioning moves barrage ships (or a group) to a system
    empty of enemy ships from which a barrage reaches fighters it could not clear from where they were, worth the
    gain in that chance less the exposure of the new position. Research adds 0.3 of a point to Destroyer II while the
    secret is held and the two red technologies are owned.
  - **Pickup**: carriers go where units wait. When the units in a system with the seat's space dock (the infantry and
    mechs on its planets that the garrison does not keep, those in its space area) and the fighters beyond the dock
    allowance outnumber the capacity of the ships already there, a hauler from a system with nothing for it to carry
    moves in, worth 0.5 per unit it can take less distance, leaving and exposure. It is a low-scoring plan: it runs
    only when the seat has tokens to spare.
  - **Screen** (the Fracture, `FractureScreen`). Enemy ships stop movement, so a ship in an empty Fracture system (the two
    egress systems and the systems between them and Styx) cuts the paths of a rival that is not yet beside Styx. The
    Fracture has two routes from every ingress system (through the left egress system and the right one), so one
    screen alone cuts only a rival already committed to one side, and a pair seals it. When a rival could take Styx next
    round (the force that reaches Styx, with the seat's screens in place, wins the fight and the planet with at least
    0.3), a cheap ship (a destroyer or a cruiser) that can reach an empty Fracture system is worth half of the drop in that
    chance times the rival's denial weight times a victory point (8), plus half of what it would add together with the
    best second screen that is needed to make the cut (neither alone makes it), less the usual costs of leaving and the
    exposure of the ship. It is never planned when the seat holds Styx itself, and the ship it parks is also the start of
    the seat's own strike on Styx.
  - **Defense** (`DefenseOutlook`). Its home system, a system with its space dock, a Mecatol Rex it holds, a Styx it holds
    (its point is in what the planet is worth losing), a system whose
    ships or planets carry at least 4 points of objective progress, and a system where an invasion would complete a
    revealed public objective for the invader are _guarded_. For each guarded system and each other player it works out
    the biggest force that player could bring there in one tactical action (`IncomingForces`): the ships that reach it
    by their move values (the seat's ships block them, Gravity Drive adds one ship's move, a ship starting in a gravity
    rift moves one farther), limited by fleet supply and carrying only the ground forces and fighters their own
    capacity holds. _Before its next turn_ needs a tactic token and a fleet not under its own token, or an unplayed
    Thunder's Edge Warfare, and a player who has not passed; _next round_ ignores tokens and passing and counts half,
    or fully when the enemy holds an unplayed Prophecy of Kings Warfare or the seat will not get another turn. The
    exact combat odds give the chance the enemy wins the space and then takes a planet (it lands on the planet it
    likes best, after the seat's space cannon and bombardment). How likely it is to try is its _fear_: the round (0.3
    in round 1, 0.6 in round 2, full from round 3) times the faction's temperament (`FactionAggression`, plus 0.03 per
    military technology up to 6 and up to 0.2 for a bigger navy) times the share of its fleet the attack leaves spare
    times whether it has a reason to score (a revealed public objective the attack completes, and denying the leader or
    a player near winning; without one fear halves) times how the attack compares with its other options (the planets
    it would take against the ships it would lose and the cost of the action, set against the best free planets it
    could reach instead), and a Ceasefire the seat holds cuts it to 30%. The seat asks for no better than even odds,
    relaxing as fear falls (`target = 0.5 + 0.5 (1 - fear)^2`); the _defense cost_ is the value it expects to lose above
    that target, over both windows. Only the seat's own cards are read; a neighbour contributes its public board,
    objectives and technologies. The cost then drives the plans: ships, ground forces and fighters leave a guarded
    system only while the cost stays within half a point (`holdBack`, `groundToKeep`, with a second pass that keeps
    more infantry once the ships that leave are known); expansions, attacks and moves are scored net of the cost of
    leaving; a _defend_ plan brings ships to an exposed system when that lowers the cost by more than the move costs
    (their spare ground forces land on the weakest planet, and the ships take along the ground forces their capacity
    holds from the system they leave; a hauler that defends with nothing aboard pays the empty-slot charge, but one that
    ferries ground forces which lower the cost by enough is sent on those alone); retaking a planet in its own home is worth the points
    that home holds; and production buys infantry, mechs, fighters or ships at the dock (ground forces on the dock
    planet) until the cost is gone or a unit no longer lowers it by half a point per resource, so a one-planet home is
    defended on the ground and a home with several planets in space, and a hopeless defence buys nothing. Not yet:
    defending with ships from more than one system in one action, planets
    an enemy needs in systems the seat does not guard, and Prophecy of Kings Warfare with Fleet Logistics unlocking a
    second fleet.
  - **Offense** (`StrikeEvaluator`, `OffenseOutlook`, `Standings`). Every attack is a _strike_ whose physics and
    economics are kept apart, and the value of a strike is judged in the world after it:
    1. **Physics.** The exact odds above give the chance to win the space combat, both sides' expected losses in
       resources (a destroyed ship takes its cargo with it, and the surviving ships' capacity is what can land), the
       chance each side loses a unit, and for every planet the chance to take it. Landing needs a win chance of at least
       one half (3 in 10 against a player about to win).
    2. **Standings.** From public information only (scores, objectives revealed and ready, techs, planets, the Imperial
       card): what each player can still score this round, how close each is to winning (_win threat_, full when the
       projected total reaches the goal), how much hurting each is worth (more for the sole leader, less for a player
       behind) and how much denying a point to each is worth (little until the player is close to winning, then more than
       a point of its own).
    3. **Value.** In the planner's currency (a victory point is 8): the seat's own objective progress; the planets taken
       (explored, with the custodians' point for Mecatol Rex); the economy and the public objectives the target loses with
       them or with its ships (an objective it can no longer pay for counts too); stopping a player from scoring public
       objectives at all by taking a planet of its home system, unless it is Nomad; the Imperial point, both the seat's own
       and denying the holder who holds or could land on Mecatol Rex; the opponent's units destroyed; blockading a space
       dock; Technological Singularity's copy as Nekro; the relief at the seat's other guarded systems when the units
       that threaten them are gone (and the exposure of the systems the strike leaves); the secrets the combat scores.
       What a strike can add by hurting a player who is not close to winning is capped below one point of the seat's own, so
       its own scoring outranks random planets and kills; against a player about to win the cap rises to several points.
    4. **Costs.** The seat's expected losses; the anger of the opponent (more against a larger navy, an aggressive faction
       and one that can reach the seat's guarded systems, three times more for a home system or Mecatol Rex, nothing
       against a player about to win); the counterstrike on the ships and planets left in the system (the struck fleet
       excluded); a Nullification Field that can cancel the action; feeding a Nekro opponent's Technological Singularity
       and being captured by Vuil'raith; half a point for each origin beyond the first.
    5. **Gates.** Win chance and the final score of at least 4 after every cost. Another player's home system is landed on
       only when its owner is close to winning, or the block it causes is worth about half a point of the seat's own, or
       the seat's own objective gains as much; otherwise it is a ship-only strike. Planets with enemy structures are
       taken only in their owner's home system, on Mecatol Rex, with _Conquer the Weak_ or against a player close to winning.
    The strikes of a system are first ranked by a cheap score (the value and the exposure of the systems they leave), and
    the two best plus the best held-back and all-in departures get the full economics. Up to three origins join one
    strike while the combined score keeps improving and the fleet pool allows it. Fleets that cannot yet reach a
    winning player's home, Mecatol Rex or a planet that stops its objectives can _stage_: they move to a system from which
    they can strike next round, worth about a third of the strike they prepare (against the opponent's units and the
    reinforcements it could bring), less the counterstrike at the staging system, and half as much when the opponent
    holds an unplayed Diplomacy it could lock a planet with. Targets are ranked by their prize times how much of the
    force the opponent can have there next round the seat's ships can reach within two moves (below half, they are not
    worth staging for), and a staging system gathers the ships of up to three origins that reach it this action and the
    target next round, within fleet supply; the two safest staging systems per target get the future strike checked. Docks within two moves of a player worth hurting value their
    dreadnoughts, cruisers, upgraded destroyers, War Suns and flagships half as much again.
    **The Fracture.** The neutral units of the Fracture are a legal opponent (`singleOpponent` accepts the neutral player), with
    nothing the other players have: no hurt, denial, anger, retaliation, counterstrike, Nekro copy or standings, no
    Nullification or trade terms, and no reinforcements (neutral units never move). The odds read the neutral player's own
    unit models, which match the neutral unit reference card (dreadnought 5 with sustain, carrier 9, cruiser 6, destroyer 8
    with anti-fighter barrage 6 x3, fighter 8, infantry 8, mech 6). A planet of the Fracture is worth its resources and
    influence, a relic when its relic token is still on it (4, three fragments at 1, 1 and 2) and, for Styx, the point of _A Song
    Like Marrow_ (8): the point is charged back by the counterstrike term when a rival can reach Styx, and a rival's Styx
    counts as a point to deny (its denial weight times 8) next to the usual standings terms. Fleets stage for the Fracture
    planets with a lead of three moves instead of one (a staging system from which the target is up to three of the
    ships' moves away): Styx is three moves from every ingress, and a fleet that moved waits under its token until the
    next round, so a fleet of dreadnoughts marches one step a round, from beside the ingress onto it, then the egress,
    then the void beside Styx. Each step must bring every ship closer to the target, so a fleet never stages backward,
    and only while the fleet that would be there can win the fight (the same score of 4 as for any attack). A step is
    worth 0.8 of that strike when one move is left and 0.85 times less per further move (0.68, then 0.58), against 0.35
    for a player's system: the neutral units never move or reinforce, so the strike a step prepares is near certain.
    A fleet on that route keeps what it has marched: any other plan that moves its ships out of an empty Fracture system
    without bringing them closer to Styx (an attack on Cocytus or on Mecatol Rex from the egress, a stage toward another
    planet) is charged the value of the step it stands on, in proportion to the share of the fleet that leaves, while
    the strike from there still scores 4.
    All of this is worth half as much (`FractureCampaign.routeShare`) for a seat with no ingress in its own home, lane or
    slice: it can only go in through Mecatol Rex or a neighbour's slice, so it values the march, the march it would
    give up and the fleet it builds for Styx at half, and does not take the fourth fleet token for it.
    Three things cover two steps of the march in one round. A seat holding _Flank Speed_ also plans its moves into
    the Fracture with one more move for every ship (`MoveBoost`), keeps such a plan only when it needs the extra move,
    at 1.5 less for the card, and plays the card right after activating the system, before it moves
    (`ActionCardRules.flankSpeed`). Thunder's Edge Warfare (`bestForWarfare`) may take a step of the march, which
    places no command token and leaves the fleet free to move again; Prophecy of Kings Warfare and _Unexpected
    Action_ take back the token of a staged fleet when that unlocks the best tactical action. A seat with a dock near
    an ingress and three or more rounds left values carrier II 1.5 more (at its route share), for a faster march.
    The march is remembered across rounds (`FractureMarch`, in memory per game and seat): a step toward Styx records
    where the fleet stands, and while it still has ships there (and stepped within the last two rounds) a plan that
    takes them elsewhere is charged the march, wherever it stands, an ingress on the board included; a plan that moves
    it away without a step forgets it. A march carries six infantry at most and fills the rest of its capacity with
    the fighters at its origin, which count in the strike it prepares (Saar took Styx with nine fighters).
    A dock within two moves of an ingress system, while a Fracture planet is not yet the seat's, values its warships
    the same half as much again, and builds dreadnoughts up to five instead of three. The dock nearest an ingress
    (`FractureCampaign`, while Styx is not the seat's) also builds the smallest fleet that beats the guard of Styx,
    three dreadnoughts, a carrier and six infantry counted in that system, at 0.75 a resource, after the core units and
    before the surplus ships, and the fleet pool grows to 4 for it (three dreadnoughts and a carrier).
    Not yet: locking a captured system with the seat's own Diplomacy; parking ships on Mecatol Rex
    without landing; Magen Defense Grid, Mentak Ambush, L4 Disruptors and Titans' planetary PDS in the odds; retreats in
    the kill estimates; building space docks forward; more than three origins; Prophecy of Kings Warfare with Fleet
    Logistics for a double move; pre-emptive strikes against a fleet that threatens only the system the strike leaves
    from; staging for a single target (`forTarget`) or for Warfare; action cards, agenda points, alliances, systems with
    units of two other players, memory of past attacks, and the seat's hidden aggression level.
  - **Objective value.** Every plan also scores its progress on the revealed public objectives and the seat's own
    secrets (`ObjectiveValue` over a before/after `Footprint` of systems, ships and planets): a point for
    completing one, a share of a point for each step towards it (larger the closer it gets, so it finishes what it
    starts; for a 2-point objective within 3 steps of done the share rises from 0.6 to 0.9), and a point lost for
    giving one up. Planets with attachments count for _Discover Lost Outposts_ and
    _Reclaim Ancient Monuments_.
  - It moves out of its home system before producing there, and keeps enough tokens for a token objective.

  Plans respect move values (nebulae, rifts, asteroid fields, supernovas, enemy ships), capacity,
  fleet supply and stranded cargo. With Gravity Drive one ship per action may move one system farther: the
  transport of an expansion, the ship of a single-ship move, or one extra ship in an attack (the strongest against
  enemy ships, otherwise the one with the most capacity) or a group move. Ships leave a guarded system only as far as its
  defense model allows. `TacticalRules` then walks the bot's own buttons step by step,
  in distance or ring mode, looking for the planned system in every part of a system picker that was split over
  several messages; if it still cannot find it, it activates the best system the picker does offer. Once it has
  taken an action, it only ends its turn. When its units
  share a system with another player's but no combat starts, it hands the choice over.
- **Exploration** (`ti4.ai.explore`). Everything an exploration gives is valued in resources: a trade good 1, a
  command token 2 (nothing once none is left in reinforcements), an action card 1 (nothing at the hand limit), its own
  commodity a third (0.6 with someone to wash it with), a relic fragment 2 (3 for the one that completes a set of
  three; while it holds an unscored Destroy Heretical Works, each of its first two fragments is worth 2 more).
  - **Decks.** The decks are public, so a deck is worth the average of the cards still in its draw pile: an attachment
    by the resources and influence it adds (a research facility 0.3, or 1.6 on a planet with a technology specialty),
    the Demilitarized Zone by -1.5 less the space docks (4) and PDS (3) it returns, Mercenary Outfit 0.75, Freelancers
    1.5 when it could pay for a unit, Lost Crew two action cards, Derelict Vessel a secret objective (valued as a draw from the secret deck). A full deck comes
    to about 1 per exploration. `TacticalPlanner` adds that to every planet nobody holds that a plan expands to or
    invades (with the infantry and mechs the plan lands there); an invaded planet someone holds is never explored.
  - **Offers.** A newly taken planet is explored through the bot's offer, from the deck with the better average when it
    has more than one trait. The bot gives the planet and offers the exploration as soon as a unit lands, but under the
    rules every unit is committed to its planet and every combat is over before any planet is explored: during a
    tactical action the AI finishes landing and waits out ground combat first, then explores hazardous planets first,
    then industrial, then cultural, resolving each card before the next exploration. A Scanlink Drone Network offer (the planet it already holds) is answered only when the
    best deck is worth more than nothing, otherwise ignored; the Crown of Emphidia is exhausted at the end of a
    tactical action only for a planet whose deck is worth more than nothing, then used on the best one.
  - **Cards that ask.** Unowned buttons are only the AI's own on its turn, in a message for it (or one that names no
    one else). Removing an infantry costs 0.7 (production limits included). It costs half the value of
    the best planet left to claim when it is needed for that: more planets within reach of its ships than the seat has
    other ground forces. When it is the last ground force and an enemy fleet can reach the system it also costs 0.15 of
    the planet's stake (`PlanetStake`, also used for retreats), because the enemy pays a token and risks retaliation
    to take it anyway. A mech is free. So a Volatile Fuel Source (2) or Core Mine (1) is usually taken, and kept only
    when the infantry is needed to claim more. A card with no decline button is always answered, even when neither
    option gains anything, and one left with only Decline is declined.
    | Card | Choice |
    | --- | --- |
    | Volatile Fuel Source, Core Mine | the command token (2), or the trade good (1), when the mech or infantry is worth it; otherwise it declines |
    | Expedition | readies the planet when 0.9 of its larger value is worth more than the infantry, and only if it can spend it this round: a dock it can still activate that would build more with one more resource, an unexhausted Sling Relay, a Leadership, Warfare or Technology card still to be played (its own, or another's with a strategy token to follow), or a revealed spend objective the planet completes (that one counts even after passing); never with Pre-Fab Arcologies or a planet that is ready |
    | Local Fabricators | a mech (2, a little more for an empty planet) paid with a commodity or trade good, unless none is left in reinforcements or the planet has the Demilitarized Zone; otherwise a commodity |
    | Functioning Base | an action card for a commodity or trade good, unless the hand is full; otherwise a commodity |
    | Abandoned Warehouses, Merchant Station | convert commodities to trade goods or gain commodities, whichever is worth more |
    | Ion Storm | the side whose other wormholes lead to free planets and its own ships, away from enemy fleets near its planets (alpha on a tie) |
    | Freelancers | below |
  - **Freelancers.** It picks the best single unit for the system: the ship `ProductionPlanner` would build there (plus 1
    in a system without its dock, where a ship saves the trip), a fighter that fits, an infantry or a mech on a planet
    of its own. Each planet pays the higher of its resources and influence, so the payment takes the planets with the
    lowest cost this round (a resource at the filler value of a unit, an influence a quarter, a trade good 1, a third
    of the resources when the AI will not spend more this round), within the scoring reserve. It builds when the unit
    is worth at least 0.25 more than the payment and declines otherwise.
  - **Command tokens** from Volatile Fuel Source (or any exploration that asks for them) go to the pool the command
    token policy grows first.
  - **Relic fragments.** A fragment is worth 2 trade goods, and 3 when it is the third of a kind, so a set of three is
    worth 7 and a relic is bought or sold at about that. Unknown fragments stand in for any kind; the 3 goes to the
    fragment that completes a set (`FragmentValue`), and while it holds an unscored _Destroy Heretical Works_ each of
    its first two fragments is worth 2 more. Receiving a fragment is worth the rise in the value of what it holds,
    giving one the fall (a fragment of a complete set costs 3, a spare one 2), so it never parts with a set it is about
    to purge. With three of one kind (unknown fragments fill in last), it purges exactly three as a
    component action and draws the relic: instead of a tactical action when no plan scores 2.5 (1 for having the relic
    sooner, 1.5 for waiting to see what the others do), and before passing. It keeps two fragments back while it holds
    an unscored _Destroy Heretical Works_, one relic a turn.
  - **Enigmatic Device.** When the best technology it can research is worth at least 4 (the bar for paying for a
    second technology) and it can pay 6 resources after the scoring reserve, it purges the device as a component
    action, picks the type of the best technology and pays the 6 resources through the research payment (the bot posts
    the research without charging them). Nekro cannot research: Propagation turns the research into 3 command tokens, so it uses the
    device only when 3 tokens (6) are worth more than 6 resources at the filler value, it has room for 3 tokens in
    reinforcements and the scoring reserve leaves 6 resources. The bot gives it the tokens without charging anything, so
    it first presses the message's "Exhaust Planets" button (before the tokens, which close the message) and pays
    exactly 6 through the research payment, then takes the tokens as for any Propagation.
- **Relics** (`ti4.ai.explore`). A relic whose ACTION it takes is a component action, played **instead of a weak
  tactical action** (the relic's net value plus the 1.5 for stalling must beat the best plan) or **before passing**
  (any positive net), one component action a turn. `RelicFlows` picks the relic with the best net; each one is a
  `RelicFlow` that answers the buttons the bot posts after the press. Values are in trade goods.
  | Relic | What the AI does |
  | --- | --- |
  | Relic fragments, Enigmatic Device | above |
  | Dynamis Core | purges it for the trade goods of its commodity value, less what the extra 2 commodity value would be worth over the next 3 rounds (its own commodity 0.3, or 0.6 with an outlet) |
  | Nano-Forge | attaches it to the safest non-home, non-legendary planet it holds: +2 resources, +2 influence (0.6 each) and a legendary planet (2), times 0.6 if an enemy can reach it |
  | The Codex | retrieves up to 3 of the best discarded action cards by keep value; the net is the gain over the cards it would have to discard, and it stops when the next card is not worth more than its worst by 0.3 |
  | JR-XS455-O | exhausted before passing to give itself a trade good (never the 3 resources for a structure) |
  | Circlet of the Void | exhausted to explore a frontier token in a system without another player's ships (a frontier card, 1.5) |
  | Book of Latvinia | researches the best two technologies without prerequisites when drawn (in the 2 minutes after the draw); purged for its point (8) only once it controls all four technology specialties, otherwise kept |
  | Stellar Converter | destroys a rival's non-home, non-legendary planet next to its bombardment when the planet is worth at least 4 (its value, structures and ground forces, times 0.6, 0.9 for the leader); never a planet with its own units |
  | The Crown of Emphidia | purges it for its point as soon as the bot offers the button (control of the Tomb of Emphidia in the status phase); the exploration use is above |
  | Maw of Worlds | purges it in the status phase for the best technology it could research when that is worth at least 2 more than the votes the exhausted planets would have cast (0.25 per influence) |
  | The Prophet's Tears | replaces the cheapest planet it would exhaust to skip a prerequisite; when a research needs no skip it draws an action card (room in hand permitting) |
  | Scepter of Emelpar | decides a follow as if a strategy token were free, exhausts the Scepter first (the bot then counts the card as followed without charging a token) and makes the follow; a ready Scepter also counts as a spare token when it checks whether it can follow |
  | The Triad | counted as a planet when paying (3 resources and 3 influence, plus one per kind of fragment held) |
  | Shard of the Throne | a planet that would hand over the Shard (a legendary planet or one in the holder's home system) carries a point (8) more to defend, and one more to take or deny |
  | Obsidian, Quantumcore, Lightrail Ordnance, Metali Void Armaments, Metali Void Shielding | passive; the bot applies them, the combat estimates do not know them |
  | Dominus Orb, Crown of Thalnos, Heart of Ixth, Neuraloop, The Silver Flame | not used: the AI never presses their buttons (a Heart of Ixth prompt is declined, the Silver Flame is a gamble that destroys the home system 90% of the time) |
- **Expeditions** (`ti4.ai.expedition`). A Thunder's Edge expedition slice is claimed at the end of a turn, from the
  end-of-turn abilities menu (or the passing menu), not as a component action, so it replaces no action and is decided
  after the action, with the money the action brought (Trade, Diplomacy and Politics before it count). The AI claims
  the kind with the best net value when that is above 0 and not worse than waiting for the pass, one slice a turn, and the first slice unlocks its
  breakthrough. It runs before Bio-Stims: a paid technology specialty planet is then readied by Bio-Stims. Bio-Stims' choice ranks the spent technology specialty planets by their readying value (`ReadyingValue`, expedition gain included), then prefers the one the expedition exhausted this turn (remembered in `expeditionPaid|`), then the larger. Psychoarchaeology never trades a planet the planned expedition would pay with.
  `ExpeditionOutlook.best(game, seat)` returns the choice; `best(game, seat, holdings)` and `options` value it for an
  adjusted `Holdings` (a wallet with the trade goods or planets a card is about to give, and a card count), for the
  strategy-card values to ask what the expedition would be worth after the card.
  - **Net value** (the planner's currency: a victory point 8, a trade good 1) = breakthrough + Fracture roll +
    Thunder's Edge race + kind nudge + race urgency - cost. A kind the scoring reserve cannot afford is unavailable.
  - **Breakthrough** (only while the seat still has a locked one): `2.0 + 0.5 x lift`, at least 0.75, scaled down when
    fewer than 4 rounds are left. 2.0 is what a free faction ability is worth (about a quarter of a point, like a
    cheap technology); the lift is the faction's win rate with a breakthrough minus without, in percentage points,
    from `expedition_stats.json` (human games), and 0.5 per point is 0.8 (a point is about 10 win-rate points) halved
    because most of the lift is who gets an expedition early, not what the breakthrough does. The "without" rate
    is shrunk toward the faction's own "with" rate minus 1 point by 200 pseudo-games (the global rate is 16% without
    and 17% with), so a faction with 40 games without one does not swing it. Shrinking both rates toward the global
    ones instead would flatten L1Z1X and Xxcha to 0. Lifts (points): Mahact 5.2, Mentak 4.4, Letnev and Yin 3.1,
    Arborec 2.9, L1Z1X 2.3, Deepwrought, Muaat and Xxcha 2.0-2.1; Saar 0.5 and Argent 0.9; Yssaril -0.7, Jol-Nar -0.9,
    Crimson -1.1, Naalu and Nekro -1.5. The strong breakthroughs are therefore worth 3-4.6 (they pay for 5 resources
    at any time), the weak ones 1.25-1.65 (they only pay for a cheap kind, such as 5 resources in the first four rounds
    at 1.0 or 3 trade goods when nothing else is wanted). Obsidian's record serves Firmament.
  - **The Fracture roll** (only while it can enter play): 20% x 2.0 = 0.4.
  - **The Thunder's Edge race.** The holder is the seat with the most slices, and the seat that claims the last slice
    breaks a tie. Holding it is worth 8 (a point: human players with the most expeditions and Thunder's Edge win 54%
    against 16-19%, but that is mostly correlation). The chance of holding is 1/players plus `0.6 / (slices left + 2)`
    per slice of lead, between 0.03 and 0.9, 0.95 when the lead cannot be caught, and exact for the last slice (it
    holds if it has the most slices or ties). A claim is worth 8 x (chance after - chance before), so the last slice is
    rushed when it makes the seat the holder (about +6.9 when tied) and left alone when it gives the infantry to a
    rival (the chance lost plus 4, half of the point, since the seat still chooses where the planet goes).
  - **Kind nudge.** The faction's win rate after each kind against its average over kinds (shrunk by 100 pseudo-games),
    0.2 per point and at most 1.0 (an eighth of a point).
  - **Race urgency.** The kinds go to whoever claims them first. Each other seat that still lacks a breakthrough and
    could pay for the kind adds 0.25 (at most 1.0), but only when the claim is already worth it.
  - **Cost** (the user's exchange rate, 2 resources = 3 influence = 1 command token = 2 trade goods: a resource is
    worth 1 trade good and an influence 2/3, scaled by how much of the round is left to use the planets, `SpendHorizon`):
    | Spending | Resource | Influence |
    | --- | --- | --- |
    | turns ahead where it would spend them (`SpendOutlook`: a tactic token and funds beyond the reserve, a research follow, Leadership tokens) | 1.0 | 0.67 |
    | no tactic token left and no planned spend | 0.4 | 0.27 |
    | the claim is made as the seat passes (planets are wasted then) | 0.1 | 0.1 |

    A trade good is always 1. **Claim now or at the pass.** `claimNow` claims the best kind (without its race urgency)
    only when it is worth at least the best kind priced for the pass times the chance that kind is still open then
    (0.75 per rival that lacks a breakthrough and could pay for it); otherwise the seat waits and `beforePassing`
    claims at the pass rates. A seat that has passed is not offered an expedition.
    | Kind | Cost |
    | --- | --- |
    | 5 resources, 5 influence | the cheapest payment after the scoring reserve (`Wallet`), priced at the planets it exhausts (all of each planet's resources or influence, so two planets for 7 resources cost 7) |
    | 3 trade goods | 3, only with 3 in hand (the bot takes them itself) and when the reserve keeps the rest |
    | Technology specialty planet | the planet's resources or its influence, whichever is worth more (a planet pays one or the other), and the points it would cost a spend objective; 0 with Bio-Stims ready and no skip planet already exhausted, never more than the planet Bio-Stims would have readied instead |
    | Secret objective | 0 for a secret over the limit, otherwise the keep value of the least promising one (at most 1) x 8 x 0.5 |
    | 2 action cards | the keep values of the two worst cards (an expected card counts 1.2 each for cards still to be drawn) |
  - **Flow.** It opens `endOfTurnAbilities` (or `passingAbilities`), presses "Do an Expedition", then the slice, and
    walks the payment through `PaymentRules` with exactly the planned planets (5 resources, 5 influence and the
    technology specialty planet; it holds the turn with a `Wait` until the payment prompt shows or a minute passes).
    A secret is discarded from the buttons the bot posts as an ephemeral message, which the actuator re-posts into the
    seat's private thread. Action cards are discarded one at a time: the bot deletes the discard message after the first,
    so the second comes from the "Discard an Action Card" button of the hand message. A slice another seat claimed in
    the meantime is dropped, because the bot would let the stale button overwrite it.
  - **Fracture.** It presses its own "Roll For The Fracture" at once. When the Fracture enters it presses the ingress
    buttons itself, at most the number the message allows (3 a synergy colour, otherwise 1), then "Done Resolving". A
    system scores `3 x (1 - distance / 4)` for its distance from its home, planets and fleets (`MovementGraph`), plus 1
    for a planet or ship of its own, minus the rivals' pressure: for each rival its aggression factor
    (`FactionAggression`) x (the share of 12 resources of that rival's fleet that can reach the system, plus 1 if the
    rival's home is within 2 systems). A system that already holds an ingress token is skipped.
  - **Thunder's Edge.** The seat that claims the last slice presses "Place Thunder's Edge", picks the empty system with
    the best `1.5 x closeness - 1.5 x pressure + 0.5 x (nearest rival home distance - own distance)` (near itself, away
    from aggressive rivals' fleets and homes) and, among the tied seats, picks itself; if it is not tied it gives the
    infantry to the least threatening seat that is not the points leader.
- **Legendary planets** (`ti4.ai.legendary`). The official ones (every legendary planet whose source is the base game,
  Prophecy of Kings, a Codex or Thunder's Edge) are read from the planet data, the homebrew ones are not played.
  - **Holding value.** `BoardView.planetValue` adds what the ability is worth over the rest of a game instead of a flat 2:
    Thunder's Edge 6, the Ordinian planets 5.5, Mallice, Hope's End, Industrex and Emelpar 4.5, Faunus and Custodia
    Vigilia 4, Primor and Mecatol Rex 3.5, Tempesta 3, the flight academies (Mirage, Illusion, Phantasm) 2.5. Styx keeps
    2 here because its point is priced separately (`FracturePrize`). So the tactical planner, defense and trades chase
    and guard these planets for the ability as well as for the Seize an Icon / Make History / Become a Legend points.
  - **Abilities.** The end-of-turn menu (`endOfTurnAbilities`) and the passing menu (`passingAbilities`) are opened by the
    brain as before; `LegendaryRules` then presses the ready ability worth most (each card at most once a turn), and
    `LegendaryRules.next` answers the buttons that follow with the same figures. Nothing is spent, so an ability is used
    when it is worth 0.1 or more.
    | Planet | When | Worth | Choice |
    | --- | --- | --- | --- |
    | Mallice | end of turn | 2 trade goods, or what converting the commodities gains (`ExploreValues`); plus the points a spend objective would score with the trade goods (`SpendUnlock`) | converts when that beats 2 trade goods |
    | Hope's End | end of turn | a mech (2, plus what `GroundDrops` adds for guarding the planet and _Mechanize the Military_) or an action card (1, 0 with a full hand) | the better one, on the planet with the best mech drop |
    | Primor | end of turn | the best infantry drop of `GroundDrops`, which prices defense, staging for the next invasion and _Occupy the Fringe_ | the planet with the best drop |
    | Mirage, Illusion, Phantasm | end of turn | the 1 resource of 2 fighters at the filler price, only with room for them on the ships | the system with the costliest fleet that has room |
    | Thunder's Edge | end of turn | the score of the tactical plan a second action would take, when that plan is urgent (an attack, or three quarters of a point) and an action was already taken | opens the card, then allows another action |
    | Emelpar | end of turn | the readying value of the best exhausted planet (`ReadyingValue`: spending it this round, a spend objective it unlocks, a tech skip) | readies that planet |
    | Ordinian, Ordinian Rex | passing | an action card plus a command token | always, then gains the token as any token gain |
    | Faunus | passing | 0.7 of the best free planet's value (no units, no attachments, not home or legendary, no custodians token) plus 0.3 if it is taken from someone, plus the objective points the planet would complete (`ObjectiveValue`, so Seize an Icon too) | the best planet |
    | Industrex | passing | the cost of the costliest upgraded ship with fleet room and a reinforcement, plus the objective points the ship's system would complete | that ship in the system with the best objective gain, then the most ships |
    | Tempesta | movement | the planner counts +1 movement (`BoardView.moveValueWithBoosts`, next to Gravity Drive); when a planned move needs it, the AI exhausts the card once in the movement step | |
    | Avernus | before passing | a free destroyer (or 2 fighters) in the system of its war sun, as a component action | |
    | Mecatol Rex (Thunder's Edge) | end of turn | the secret redraw (see Drawing and trading secrets) | |
  - **Not played.** Garbozia (the stored action cards would need a play step), and Custodia Vigilia and Styx, which have no
    button: they count in the holding value and, for Styx, in `FracturePrize`.
  - **Objectives.** Systems with a legendary planet count for _Make History_ and _Become a Legend_ and a controlled
    legendary planet for _Seize an Icon_ (`ObjectiveValue`), so taking one raises the planner's score by those points.
    _Seize an Icon_ in hand is worth most while a legendary planet is free or its own (`SecretValue`), 0.4 of that while
    only rivals hold one (they can be attacked), 0.1 with none on the board. The abilities feed the same objectives: Primor's
    infantry and Hope's End's mechs through _Occupy the Fringe_ and _Mechanize the Military_, Mallice's trade goods and
    Emelpar's readied planet through the spend objectives, Faunus's planet and Industrex's ship through the objective gain of
    their footprint.
- **Actuation.** The AI presses the bot's **real** buttons through the test bed's stand-in
  event (`TestBedPress.standInEvent`), so every rule and side effect runs exactly as for a
  human. The stand-in user carries the seat's id, so the normal owner checks apply. Handler
  edits to the prompt complete synchronously; a handler failure is detected without changing
  `ButtonProcessor`.
- **Fallback.** `AiStallDetector` names what the game is waiting on from a seat: its turn,
  scoring, status homework, strategy-card follows, an ongoing combat, or a when/after decision.
  If that lasts 3 minutes with nothing the seat understands, the newest prompt tied to that
  reason is re-posted with `aiSeatPick_…` buttons that name the seat. The first player to press
  one makes that seat press the original button on the original message.
  - Private prompts are only ever declined, and only when a declining option exists.
    Otherwise the AI says it is stuck.
  - It never presses buttons that open a form.
  - A fallback that presses "Done Redistributing Command Tokens" first puts back any command tokens the seat is short of
    its starting allocation (the `originalCCsFor<faction>` stored value), in the pool each came
    from or else the tactic pool (`RedistributionGuard`), and does not press Done if it cannot.
  - Every fallback (unsure, no progress, a stall timeout, a player asking for the hand-over, the loop stop and every
    "stuck" notice) is recorded by `FallbackLog`: one `BotLogger.info` line starting `AI fallback game=… round=… phase=…
    seat=faction/color kind=… reason=… prompt=… text=… buttons=[…] recent=[…] pending=… outcome=…`, and the last 10 in
    the game stored values `aiFallbackLog|slot0` to `slot9` (base64) with `aiFallbackLog|next`, so they survive a
    restart. `/ai fallbacks` lists them and the self-play report prints an "AI fallbacks" section.
- **Guards.**
  - At most 2 attempts per prompt and state (staged movement and unit positions count as progress).
  - At least 1.5 s between actions in a game, and at most 150 actions an hour per seat.
  - Delegation caps per seat and per game each round.
  - A loop guard: when a seat falls back on the same unsure choice 6 times in one turn without pressing anything
    itself in between, it stops choosing on its own and posts one 🔁 notice (`/ai delegate` still hands the choice
    over). In self-play the harness ends such a game as `LOOPED` instead of waiting for it to stall.
  - A pause in one game after 3 failed presses in 10 minutes, and in every game after 10; `/ai status` says when a seat is holding back.
  - After an undo, the AIs forget what they pressed and planned this turn, so they can redo it.
  - It respects the circuit breaker, the kill switch and the lease, and its shutdown drains in-flight presses before the lease is released.

## Segregation

All AI code is under `src/main/java/ti4/ai/**` (tests under `src/test/java/ti4/ai/**`).
`AiSegregationTest` fails the build if any other file references `ti4.ai` outside its seam
list. Today the seams are:

| Seam | Why |
| --- | --- |
| `SlashCommandManager` registers `AiCommand` | the `/ai` command |
| `TestBedService.findNonDeveloper` skips AI seats | test-bed games with an AI can still be reset and scripted |

Seven small generic changes don't reference `ti4.ai`:
- `GlobalSettings.ImplementedSettings.AI_PLAYERS_ENABLED`, the kill switch.
- A public `TestBedPress.standInEvent` with a configurable repost header.
- The Nekro hero's casualty and trade-good report goes to the player's game channel (`getCorrectChannel`) rather
  than the channel the button was pressed in. Nothing changes for humans, but an AI seat presses that button in its
  private thread, where the report's @mentions of the victims could add them to the thread.
- The bombardment hit prompt offers every defender an "Auto-assign Hits" button (dummy players already had one), so
  humans and AI seats alike can assign bombardment hits automatically.
- The Sabotage button (`ActionCardButtonHandler`) refuses a second press on a window that a Sabotage already canceled
  (the canceled windows' message ids are kept in the game's stored value `canceledActionCardWindows`): it spends no
  Sabotage and marks no play as canceled. Before, that press spent another Sabotage and canceled an earlier play of the
  same card. Any other press behaves as before.
- A canceled _War Machine_ or _Flank Speed_ takes its effect back (the card player's spent `warmachine`, the
  `flankspeedBoost` of the active tactical action) instead of leaving it for the player to undo by hand. It acts on the
  card's player, not on the player who pressed the button.
- A cancelled _Veto_ resumes the agenda's "when" queue, which halts at a Veto and which nothing started again (only
  while the agenda is in the "when" window and the Veto's player was the last to play a "when"). The same button
  erases a rider only when the Sabotage really cancels the card; before, a press by a player with no Sabotage erased it.

## Testing

- Unit tests: `mvn -o test -Dtest='ti4.ai.**.*Test'`.
- **Self-play integration test** (`AiSelfPlayTest`). Six AI seats play a real game in-process.
  - The setup is the same as in production: `createNewGame`, the standard 6-player map, `/ai add` for each seat,
    and the referee starting the game.
  - Every press runs the bot's real button handlers. Only Discord and a few services are replaced:
    - `FakeDiscord` (`src/test/java/ti4/testUtils/discord`) is an in-memory Discord. Messages, threads, edits and
      button ids work as on Discord, and every Discord request completes immediately on the test's thread.
    - `SelfPlayEnvironment` provides the Spring beans the button path needs, stores game files in a temporary
      folder, and turns off map rendering, database sync and metadata files.
  - The AI runtime ticks on a virtual clock. The first round takes about 30 seconds of real time and a whole game
    about 2 minutes. A game keeps its thread busy only part of that time, so whole games run several at once (below).
  - A run ends at the victory point goal, when the objectives run out, or when nothing changes for two virtual
    hours (a stall). The outcome's report shows the game state, the AI's recent decisions and presses, what the bot
    logged, Discord calls the fake doesn't support, and the latest messages in the main channel, combat threads
    and private threads.
  - Each report also has a scoreboard (victory points after every round, each revealed objective and who scored
    it, secrets, techs, planets and trade goods per seat) and the AI's decisions counted by reason. A batch ends
    with the average victory points of the leader and of the whole table, the measure of the AI's strength, the
    tactical plans per game by kind (attacks, attacks on another player's home and the ones that landed there, attacks
    on Mecatol Rex, the ones that landed and the ones against an Imperial holder, staging moves, and how many sampled
    rankings held a stage plan), where expansions went by territory zone and round band, the round the custodians were
    taken with who took them (their initiative, speaker token and first action, and the Politics, speaker and
    Leadership chain around round 1 and 2), how often Mecatol Rex changed hands afterwards, and the time
    `TacticalPlanner.ranked` takes on sampled real boards.
  - By default it plays the first round:
    ```
    mvn -o test -Dtest=AiSelfPlayTest
    ```
  - Whole games, here eight:
    ```
    mvn -o test -Dtest=AiSelfPlayTest -Dai.selfplay.full=true -Dai.selfplay.games=8
    ```
  - `-Dai.selfplay.threads=T` plays up to T games at once (default: half the processors, about one per physical core).
    Each game runs on its own thread with its own fake Discord, environment and virtual clock: the test stubs are
    thread-local, `DiscordRouter` points `JdaService.jda` and `guildPrimary` at the calling thread's fake, and every game
    checks at the end that its fake holds only its own channels. On an 8-core machine 10 games take about 3 minutes
    (about 20 seconds per game, against about 2 minutes each one after another), and 16 take under 5. The test mocks
    are stub-only, so a game does not keep a record of every call it made; that is what lets many games fit in the heap.
    Under heavy load the bot's real-time "Slow Button Warning" can show up in the reports; it says nothing about the AI.
  - `-Dai.selfplay.factions=sol,jolnar,...` seats other factions (default: the first six supported ones). Every seat
    starts with only its own faction note and the five generic notes; the scoreboard lists the notes each seat holds,
    received and has in its play area.
  - `-Dai.trading=false` plays without trading, for an A/B comparison of the batch summary.
  - `SabotageSelfPlayTest` plays a contender's _Overrule_ in a six-seat game and checks that an AI seat cancels it with the bot's real buttons.
  - `-Dai.selfplay.give=<alias,...>` deals those action cards to every seat, to watch a card's flow end to end.
  - `-Dai.selfplay.fracture=true` brings the Fracture into play at the start of round 2 of every game (`FractureTracker`): the
    seven tiles with their neutral units, and an ingress token on a random system of each technology specialty and on
    Mecatol Rex (`-Dai.selfplay.fractureseed=N` picks the systems, default 7). The batch prints, for each Fracture planet,
    the games in which it was taken, with the round and the seat, and how often Styx changed hands. Without the flag a
    game is unchanged. `FractureSelfPlayTest` puts a fleet beside Styx and beside Cocytus and checks that the active seat
    takes the planet through the whole combat against the neutral units (another AI seat rolls for them), with the point
    of Styx and the relic.
  - `-Dai.selfplay.fleetlog=true` prints a `FLEETLOG` line per seat at the start of each round (`FleetLog`): its units on the
    board by type, its fleet pool, its docks with each one's distance to the nearest ingress, its PDS, its ships on an
    ingress and inside the Fracture, its movement technologies and its remembered march, to compare the AI's building
    and movement with a human game.
  - `-Dai.selfplay.styxprobe=true` prints a `STYXPROBE` line for the active seat at the start of each of its action-phase
    turns (`StyxProbe`): the fleet cost that could reach Styx within one to four tactical actions, what defence holds
    back, the distance from its nearest dock to an ingress, its best strike on Styx with the gate that rejects it
    (`spaceWin` or `score`), and the staging toward Styx with the gate that rejects it (`notWorth`, `feas` for
    feasibility, `attackExists`, `noGathering`, `future` for a future strike under 4, or `ok` with the plan's score,
    the biggest gathering and the future strike, counterstrike and leave cost of the best one), next to the seat's
    top-ranked plan.
  - Every action card play carries the round it was first seen in, and the batch summary prints the plays by round
    for the whole batch and each card's average round. `Overrule` gets its own block: its plays by round, the strategy
    cards its picks chose, how many of those picks raised the seat's points before its turn ended, and how many seats
    still held the card when the game ended.
  - The batch summary counts the action cards: plays per seat per game, plays a Sabotage cancelled (per game and as a
    share of all plays), Sabotage plays per game, and each card's plays per seat per game next to the rate at which
    human players play it when they draw it. It also prints each card's plays per draw (a seat holding a copy at any
    point of a game is one draw), flags every card a rule plays in under half the human first-play rate, and lists
    the cards drawn most that no rule plays. `-Dai.actioncards.ab=true` plays half of the seats, a different half in
    each game, with the action card rules from before the statistics, and the summary compares the two groups: average
    final victory points, action cards played per seat per game and top finishes, with their seat counts.
  - The batch summary breaks scoring down (`ObjectiveTracker`): the leader's points by source (the same breakdown as
    the bot's "Winners Path to Victory" statistic, to read next to human games), the share of seats scoring a public
    objective and a secret in each status phase, and for every public objective by stage the share of seats that
    scored it and how far short the others were just before each status phase (taken once every seat has passed, as
    queued scores land when the status phase starts). It ends with every secret scored over the batch.
  - `LegendarySelfPlayTest` hands five seats a legendary planet each (Mallice, Hope's End, Primor, Ordinian, Emelpar) when
    the first action phase begins and checks that the seats exhaust them through the bot's real buttons.
  - `RelicSelfPlayTest` hands six seats relics (Dynamis Core, JR-XS455-O, the Codex, Nano-Forge, the Circlet of the Void with a
    frontier token, the Crown of Emphidia with a Tomb, three cultural fragments, and the Book of Latvinia drawn through the
    bot) when the first action phase begins and checks that the seats use them through the bot's real buttons. A batch
    prints, relic by relic, the games in which it was drawn, the average round, the times it was used, passed between
    seats and held unused at the end (`RelicTracker`), and the points of the seats that held one against the table.
  - `-Dai.secrets.hold=false` plays without patience with action secrets. The batch prints the secrets scored per seat
    by phase and the average round in which the action secrets were scored.
- In a dev guild:
  1. `/testbed apply` a preset that starts in `setup`, or create a game for self-play.
  2. `/ai add` once per AI seat.
  3. Play as the other seats, or watch. `{"do":"runCron","value":"AiRuntime"}` triggers an AI poll from a test-bed script.

## Status

| Version | Scope |
| --- | --- |
| V0 | Seat, runtime, actuation, fallback, `/ai` commands; simple phase decisions. |
| **V1 (in progress)** | Full turn play. Done: tactical actions, combat (including bombardment), Technological Singularity, several AI seats and self-play, scoring (spend objectives, custodians, Imperial), strategy card primaries and secondaries including Warfare's, research, status, action and agenda-phase secrets, agenda voting and Galactic Threat, Nekro's agent and hero, component action cards (economy, Spy, Uprising, research and the rest), Sabotage and action card values from human play statistics and scoring stakes, promissory notes, trading (offers, counter-offers, Trade card terms, debt), Diplomacy and Politics follows, an agenda outcome forecast with riders, Veto and the shenanigans courtesy. Still to do: the rest of the combat action cards and the agenda ones, the mech secrets (Mechanize the Military, Occupy the Fringe), Strengthen Bonds. |
| V1.1 | Draft participation (factions reserved for AI seats, other picks made by the table). |
| V2 | Hidden aggression levels with drift and lock; attacks gated by aggression. |
| V3 | Deeper deals: votes for trade goods, multi-party pacts. |
| V4 | Extortion and pacts. |
| V5 | Action cards, leaders, more depth, then faction-specific rules beyond the shared set. |

### Known limitations

- **Legendary planets, Garbozia.** The AI never uses _Dok 'N Pic's Salvage Yard_: stashing action cards on it only pays when
  they are played from there, and the action card rules only play from the hand.
- **Legendary planets, Thunder's Edge.** A second action is only opened for a tactical action; a strategic action that would be
  worth it is not considered, because the menu that offers the card no longer shows the strategy cards.
- **Secondary ordering, human holder.** A human holder's primary cannot be detected, so AI followers only wait for
  the players ahead of them, not for the primary to finish.
- **Secondary ordering, human followers.** A human who is partway through their secondary counts as done once they
  have pressed follow; the bot does not expose their later steps (technology choice, payment, placement).
- **Secondary ordering, undo.** Rolling the game back with `/game undo` clears the secondary queue, so the AIs
  re-derive who has followed from the game state alone.
- **Action cards, stalls.** Every component action card is worth at least a stall while another player is still
  acting, so the AI plays its weakest cards as soon as it has nothing better to do. In smoke runs it played _Master
  Plan_ on 75% of draws, against 38% for human players. The stall value (1.5) and the "spend the card that gains most
  over keeping it" rule are not yet checked against human play rates in a large self-play batch.
- **Prove Endurance.** The stall count is an estimate from public cards and a guess for hidden hands. Its own count leaves
  out heroes, agents and promissory notes with an ACTION, and it does not trade for other players' notes to stall with.
  Nothing here has been measured in a large self-play batch yet; the 1.5 stall, the 0.3 contest line and the logistic
  spread of 1.5 are first guesses.
- **Action cards, Overrule.** It only performs Imperial and Technology. The bot cannot yet run the other primaries
  from an overruled card, though human players also overrule Warfare (10% of plays), Construction (9%) and
  Leadership (6%). Overruling the seat's own readied Imperial and then playing it assumes the table accepts that.
  It is never the second action of _Master Plan_ or _Fleet Logistics_ (a component action card is never the second
  action), though a points play can wait for the turn after. The wait for the Mecatol Rex swipe trusts the highest
  ranked tactical action; if that action is never taken, the card is played at the pass.
- **Action cards, riders.** Only _Imperial_, _Trade_, _Politics_, _Technology_ and _Leadership_ riders are played; the
  placement riders (Diplomacy, Construction, Warfare), _Sanction_ and the other agenda cards are not. There is no
  rider stacking logic: a seat does not consider that another seat's rider on the same agenda will be queued or placed
  first, so several seats can end up riding the same agenda and the late riders fall to the earlier ones' choices.
  The softmax temperature of the forecast (4 trade goods) leaves a clear favourite about a quarter short of certain.
- **Agenda cards, deferred.**
  - _Confounding Legal Text_: a human's "Resolve with Current Winner" plus the AI's own resolution of the same agenda
    resolve it twice, because the bot only guards a second press of the same winner. The self-play referee picks the
    newest resolution button. It needs a bot-side guard first.
  - _Diplomatic Pressure_: the note it needs comes from a hidden hand, and there is no valuation of a note to aim it by.
  - The poison pill: `AgendaPolicy` values nearly every For/Against agenda at 0, so "predict the outcome you do not
    want" cannot emerge until For/Against agendas are valued.
  - _Deadly Plot_ and _Bribery_ (the bot has no automation for them), the placement riders (Diplomacy, Construction,
    Warfare), _Sanction_ and the D-tier agenda cards.
- **Action cards, combat.**
  - Played: _Morale Boost_, _Fighter Prototype_ and _Emergency Repairs_, at the start of a round. Not yet: _Shields
    Holding_ and _Maneuvering Jets_ (they post nothing: the player presses "Cancel a Hit" on the hit message, and the
    second cancel button is unowned, so the AI needs perception and a two-step play first), and _Fire Team_ (its extra
    hits are posted as text only, with no hit-assignment buttons, so the AI could neither take them nor assign them).
  - No ground combat card is played once a player has asked to automate the combat (the confirmation is on the table).
    An unaccepted "Automate Combat" offer no longer blocks them.
  - The play comes at the seat's round-start decision: when the opponent rolls before the seat has declared, the seat
    plays nothing that round (a human attacker who rolls without waiting). An AI defender now gets its window against
    an AI attacker, which waits for the defender's ready. _Emergency Repairs_ is only played at the start of a round,
    never at its end.
  - The seat waits for an opponent's "ready" up to 120 seconds after the thread's newest message (a human) or 3 minutes
    (an AI seat), so a combat against a human who plays cards slowly is no longer rolled before the human is done, but
    a human who never presses the button still delays the AI by two minutes a round.
  - _Wild Wild Galaxy_'s +2 _Morale Boost_ is valued as +1.
  - Left to humans: _Direct Hit_ (the bot asks for the ship in chat), _Skilled Retreat_, _Rout_, _Intercept_,
    _Parley_ and _Ghost Squad_ (retreat and invasion windows), _Courageous to the End_ (the AI cannot answer its
    `assignHits_` prompts), _Blitz_ and _Experimental Battlestation_ (they roll in the main channel),
    _Reflective Shielding_, _Scramble Frequency_, _Waylay_, _Solar Flare_, _In The Silence Of Space_, _Salvage_ and
    _Crash Landing_.
  - _Bunker_ is a bot bug, not an AI one: its -4 to bombardment is stored in the defender's new combat modifiers, which
    only move in when the defender rolls, so the attacker's bombardment probably never sees it (a To-Do in
    `CombatRollService`).
  - A combat card's Sabotage window can freeze its combat for as long as it stays open (see the combat cards above).
- **Action cards, Strategize.** Never Technology (the bot's button grants the technology without collecting its 4
  resources, so the AI could not pay honestly) or Leadership (the strategy token taken up front has to be regained by
  hand).
- **Action cards, Exchange Program.** Not played: its agreement needs a negotiation, and the other way costs both
  players a fleet token.
- **Action cards, fog of war.** Cards aimed at a player, planet or system, and _Overrule_, are never played in
  fog-of-war games.
- **Action cards, Fleet Logistics.** Wiring _Master Plan_ exposed that the Fleet Logistics second action had never
  started. It now does, so every AI seat with Fleet Logistics takes second actions it did not take before; the effect
  on results is not yet measured.
- **Action cards, Sabotage.** Self-play rarely puts an AI in front of a game-deciding card, so the cancel policy
  (involvement, standing, scoring stakes, the reserve for _Overrule_) is covered by unit tests more than by games.
- **Action cards, rounds left.** The secret draw value and other end-of-game reasoning estimate the rounds left from
  the leader's points at 2 a round, which misjudges tables that score faster or slower.
- **Action cards, priors.** The experts' tiers stop at _Overrule_; the cards after it alphabetically (_Strategize_
  among them) are valued from the human statistics alone.
- **Action cards, legacy switch.** `-Dai.actioncards.ab=true` and the stored value `aiLegacyActionCards` keep the
  rules from before the statistics for comparison, until the large A/B batch at the end of the project.
- **Expeditions, race and strategy cards.** The Thunder's Edge chance is a closed-form estimate from the slices
  claimed so far; it does not model who will claim the rest. The chance that a rival takes a slice first is 0.25 per
  rival that could pay for it, whatever the turn order, and waiting for the pass is not charged for the breakthrough
  coming later in the round.
- **Expeditions, correlation.** The breakthrough lift and the kind nudge are read off human games, where who completes
  an expedition early is entangled with who was already winning; the 0.5 factor on the lift is a guess, not a
  measurement. Holding Thunder's Edge is worth a point on the same evidence.
- **Expeditions, the Fracture.** The ingress tokens are placed for distance and safety, not for a route in, and the
  Fracture roll is only worth 0.4. Styx is taken in about one self-play game in five (3 of 16, rounds 6 to 8): a fleet
  that can beat its guard comes together around round 5, and games end around round 6. Against the human example game
  (pbd28695) the AI builds fewer fighters (5 per seat at the start of round 5 against about 8), fewer destroyers, and
  its flagship later.
  Not yet: production inside the Fracture (Saar's floating docks), building a flagship early (every human seat had
  one by round 3 in the example game), and _Flank Speed_ under Wild Wild Galaxy (+2).
- **Expeditions, fog of war.** In fog the GM places the ingress tokens (their buttons carry no owner), and the AI
  neither places them nor knows which rival holds how many slices.
- **Expeditions, tile choice.** Thunder's Edge is placed by distance and the rivals' home and fleet pressure, not by
  what the system offers a fleet moving there.
