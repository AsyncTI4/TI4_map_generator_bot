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

**When an AI is confused**, it posts the choice in the main game channel and **any player**
may pick for it. Choices involving hidden information (its hand, its secret objectives) are
never shown: the AI picks a safe option and says so.

**Trading:** they trade through the bot's own transaction buttons and never read chat. They start deals on their
own turn, in the agenda phase, as Trade holder with the players who followed, and ask for debt payments at any time;
they answer offers whenever they arrive. They trade trade goods, commodities, debt and at most one of their own
promissory notes per deal, never action cards, relics or secret objectives. They keep the terms they accept (an AI
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
     resolving agendas, breaking ties and moving on to the next round. It never ends the game.
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
  - "No Sabotage" after the same seeded delay in every window, so the timing never reveals its hand;
  - Nekro: Propagation's command tokens from any source, the commander's card draw, Dacxive Animators' infantry,
    and its agent and hero (below);
  - paying for a Nekro agent that names it (any AI seat);
  - playing economy action cards and answering cards that target it (below);
  - playing the promissory notes other players gave it, and respecting its own notes played against it (below);
  - trading (below).
- **Scoring comes first.** Victory points drive most choices:
  - **Public objectives**, including "spend" objectives. The bot takes trade goods and tokens off by itself;
    for resources and influence it only posts exhaust buttons and trusts the player, so the AI plans the cheapest
    payment (`Wallet`: planets before trade goods, each planet paying resources or influence, never both) and
    exhausts exactly that. It prefers the objective worth more points, then one that costs nothing.
  - **Reserve.** During the action phase it keeps back what the cheapest affordable spend objective needs at the
    status phase (unexhausted planets, trade goods, tokens), unless it already qualifies for enough free
    objectives. Production, research and following respect the reserve. Production also leaves the custodians' 6
    influence while its ships can reach Mecatol Rex, and the influence-leaning planets a Leadership secondary (or its
    own primary) would spend on command tokens while that card is still to come this round.
  - **Mecatol Rex.** It lands on Mecatol Rex when it can pay the custodians' 6 influence (on top of the reserve),
    pays it honestly, and scores the Imperial point whenever it plays Imperial while holding Mecatol Rex.
  - **Secret objectives** (`ti4.ai.secrets`), within the scoring limits:
    - **Status phase:** one per status phase, choosing among those it qualifies for the one it could lose first
      (board presence) and the ones with a cost last. It pays those costs honestly: 5 action cards for
      _Form a Spy Network_, 2 relic fragments for _Destroy Heretical Works_. A cost (like a spend public objective's
      planets) stays owed until it is paid, even when the bot queues the score behind slower players.
      _Adapt New Strategies_ counts only the seat's own faction technologies, so Nekro's copies never score it.
    - **Action phase:** at most one per combat (a space combat and each ground combat are separate). Before anything
      else on every tick it records each combat it is in (opponent, whether that opponent leads on points, its
      flagship, the system), and it also picks up a combat it only saw after the fact from the bot's combat round
      trackers. It judges each combat when it ends, and counts a win only if both sides rolled a round: _Spark a
      Rebellion_, _Brave the Void_, _Darken the Skies_, _Unveil Flagship_, _Betray a Friend_, _Demonstrate Your
      Power_, _Destroy Their Greatest Ship_ (the enemy's flagship or war sun gone from the board, not just
      retreated), _Fight with Precision_ (anti-fighter barrage clears the fighters before the first round), _Turn
      Their Fleets to Dust_ (its space cannon, and no other player's, destroys an invader's last ships), _Become a
      Martyr_ and _Prove Endurance_. Earned secrets wait in a queue, so two combats in one turn can score two.
      _Make an Example of Their World_ scores when its bombardment destroys the last ground forces on a planet
      before it lands; it builds ships with bombardment for it and keeps the secret while it has some.
    - **Agenda phase:** no limit: _Dictate Policy_ (3 laws in play) and _Drive the Debate_ (it or its planet is
      elected by the agenda just resolved, never one from an earlier round), for which it also votes for itself.
    - It keeps the secrets it can achieve (weighted by how achievable they are for it) and uses Imperial to replace
      the rest; it never pursues _Mechanize the Military_, _Occupy the Fringe_ or _Strengthen Bonds_ yet.
- **Command tokens** (`CommandTokenPolicy`). Every gain (status phase, Leadership, Propagation) goes to the first
  unmet level of one priority list, and status-phase redistribution moves a token out of any pool above its share
  (fleet first, then strategy, then tactic) and gains it back where the list needs it, so each move brings the pools
  closer and it always settles:
  1. fleet up to its largest stack, so no ships are lost;
  2. tactic up to 2 (tactics are what turn into actions);
  3. fleet up to what a revealed _Raise a Fleet_ or _Command an Armada_ needs, once a stack can grow into it this
     round;
  4. fleet up to 3;
  5. strategy up to 2;
  6. fleet up to its largest stack plus one (at most 5), only when that stack has at least 3 ships and other ships
     could join it;
  7. tactic up to 5;
  8. strategy up to 3, never more;
  9. the rest to tactic.

  Every fleet level stays within _Fleet Regulations_' 4, and _Raise a Fleet_ is ignored under it. A normal round
  ends near 2 tactic, 2 strategy and 3 fleet. When it must lose a token (for example for a Nekro agent), it takes it
  from the pool furthest above its share, a fleet token at its largest stack last.
- **Strategy cards.** Card picks weigh Imperial by the point it adds beyond the free status-phase score (a second
  scorable objective, Mecatol Rex, or the custodians within reach this round), Technology early and when tech
  objectives are revealed, Trade when trade-good objectives are revealed, Leadership early and more when it is short
  of tactic tokens. After playing a card it resolves the primary before ending its turn, paying every cost the bot
  leaves to the player:
  | Card | Primary | Follows when |
  | --- | --- | --- |
  | Imperial | scores the best public objective it qualifies for, then the Imperial point (Mecatol Rex) or a secret | it has a spare strategy token and room in hand for a secret (it never swaps one secret for another) |
  | Technology | one free technology, a second for 6 resources if one is worth it (`ResearchPolicy`: generic value, informed by the technologies Nekro players most often end games with, plus progress on tech objectives it can still finish in the rounds left, about 1.5 researches a round until the leader is 2 points a round from winning, and Space Dock II for _Produce en Masse_; minus 0.7 per resource or influence lost exhausting a technology specialty planet for a prerequisite, which it then exhausts when paying, free with Psychoarchaeology, or AI Development Algorithm when that costs less); Nekro takes Propagation's 3 command tokens instead | a technology worth at least 3 (most technologies other than the weakest) is affordable after the scoring reserve; as Nekro, Propagation's 3 command tokens for the strategy token and 4 resources |
  | Leadership | 3 command tokens, plus up to 3 more bought with spare influence, within its reinforcements | spare influence buys up to 3 command tokens (3 influence each, no strategy token) |
  | Politics | makes itself speaker (or the player furthest behind), draws 2 action cards | with a spare strategy token when _Form a Spy Network_ needs cards (right after Imperial when 2 cards complete it), or with 2 or more spare strategy tokens and room for 2 cards; never with no strategy token, a full hand or _Hold No Action Cards_ |
  | Construction | two structures: a space dock while it has a site, otherwise PDS on planets without structures. A dock site is another planet in a home system with more than one planet, or a planet worth 2 or more resources elsewhere, preferring planets worth 3 or more and those in its own slice (next to home or on the way to Mecatol Rex; Styx counts too); any planet for _Fuel the War Machine_ | a revealed or held structure objective is 1 or 2 structures short |
  | Diplomacy | readies the 2 exhausted planets it can use best (the same test as a follow), otherwise its 2 most valuable | its best 2 exhausted planets make a spend objective payable at the status phase (even after passing), or, while it still has actions, pay at least 3 towards production it would actually build or the custodians (both need a tactic token), research (a Technology follow it still has a strategy token for, or the 6-resource second technology of its own primary; never Nekro's free Propagation) or Leadership tokens (within its reinforcements). It readies those planets. |
  | Warfare | a tactical action without a command token, which may reactivate a system that already holds its token (for example a second production at home) | producing at home is worth as much as a tactical action's production would need to be (carriers, infantry for free planets, ships for objectives, or enough filler) |
  | Trade | the bot resolves the primary; an AI holder then announces X−k terms per player (k = 2 for 4 or more commodities or a player ahead of it, otherwise 1; none for a player close to winning, the leader, a bad payer or one the commodities would let score) and sends each follower the deal: their commodities for that many minus k trade goods (k debt when they are not neighbours, k trade goods after Replenish and Wash), an even wash for Hacan and for players who spent a strategy token | for free when an AI holder's terms are worth it (it holds its own _Trade Agreement_, gains commodities, and the deal or the debt is worth more than what it gives up), always as Hacan, and with a strategy token for a human holder only for 4 or more new commodities and somewhere to wash them |

  Buying tokens with influence uses only planets worth more as influence than as resources, and keeps what a spend
  objective and (when its ships can reach Mecatol Rex) the custodians need. Bought tokens are only gained once the
  influence is actually paid. While another payment or token gain is still open, it waits up to 90 seconds before
  deciding on a Leadership, Technology, Warfare or Diplomacy follow, instead of declining it.

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
  - **Nekro** cannot vote (Galactic Threat): it never plans a ballot unless the Xxcha commander lets it. Once per
    agenda phase it queues Galactic Threat as an "after" (and waits quietly while it is queued) when
    another player has a technology it could gain, predicts the outcome most likely to win (the election of the
    player with the most votes), and on a correct prediction takes the best technology (`CopiedTechPolicy`, which
    also caps copied faction technologies at its two Valefar Assimilators). It never researches: every research is
    replaced by Propagation's command tokens, all of them, within its reinforcements.
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
- **Action cards** (`ti4.ai.actioncards`, first stage).
  - **When it plays.** On its own turn, when it is about to pass (no tactical action worth taking, every strategy card
    played), it plays the most valuable of: _Mining Initiative_ (trade goods equal to its best planet's resources),
    _Industrial Initiative_ (2 or more industrial planets), _Economic Initiative_ (only when readying its cultural
    planets lets it pay for a spend objective), _Rise of a Messiah_ (3 or more planets), _Frontline Deployment_ (on a
    planet a dock or carrier can pick the infantry up from, or next to enemy ships) and _War Effort_ (a cruiser where
    the fleet pool and cruiser limit allow it). A card that would unlock a spend objective is worth far more. It plays
    sooner when its hand is near the limit. _Summit_ is pre-played as soon as it is drawn.
  - **Honest play.** It never plays while censured, over the hand limit, or passed against Transparasteel, and keeps
    5 cards while it holds _Form a Spy Network_. The bot posts the resolve button at once, so the AI holds its turn
    until every other player has answered the Sabotage window (at most a day), waits a few seconds more in case a
    Sabotage is landing, and never resolves a sabotaged card. With no window it still waits a while if someone could
    cancel with Instinct Training or the Watcher mech. It remembers the resolve button in case the channel moves on,
    and after an undo or a restart it picks an unfinished play up again from the board.
  - **Answering others.** Every AI seat answers each Sabotage window after the same seeded delay, including windows
    that have scrolled out of the messages it reads.
  - **When targeted** (once the card's Sabotage window has closed). It hands a card to a _Spy_ (if it has one), gives the least harmful promissory note to _Diplomatic Pressure_
    (keeping Support for the Throne), and under _Extreme Duress_ plays its strategy card rather than lose its hand.
  - **Discards** (hand limit, _Form a Spy Network_, Nekro's agent) go to the cards it can never use first, then by
    how much each card is worth right now. Nekro's agent only spends a card worth less than its 2 trade goods.
  - Not yet: combat cards (_Morale Boost_, _Shields Holding_), agenda riders and _Sabotage_ against others.
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
    while they can trade with it, less otherwise) × 0.5 near the end; promissory notes follow a table (its own
    _Support for the Throne_ and _Alliance_ only when desperate, and only to a player well behind). A victory point is
    worth 6 trade goods, rising to 12 near the goal and 30 for a winning score, so a deal that completes a spend
    objective is worth paying extra for (**desperation**, never more than 3/4 of the point).
  - **Stinginess** (public information only). It subtracts what the deal gives the partner, weighted by how far the
    partner leads (fully for a player close to winning), and refuses to give a point to a player who could win with it
    or to a clear leader. It accepts deals worth at least 0.3 to it (from an AI) or 0.5 (from a human) after that, and
    prices its own offers to another AI by working out what that AI would accept.
  - **Trust.** Human players start at 0.8 and AI seats are always 1: +0.1 for accepting its offer or paying debt, −0.3
    for not paying for a free Trade follow, −0.2 for not paying a debt it collects. It lives in the AI's memory and
    resets on undo or restart.
  - **What it starts.** On its own turn, before its action (at most 2 offers a turn): paying its own debt, buying the
    trade goods it is short for a spend objective (the cheapest package of commodities, debt or a note the lender would
    accept, at most twice a round), a 1:1 commodity wash, selling commodities when nobody has any to wash, and
    collecting debt from human players. In the agenda phase: paying debt (with the "Send" buttons of the bot's debt
    reminder, never "Erase"), collecting debt and washing (with another AI only when its faction sorts first). At
    most 12 new offers an hour, 3 waiting for an answer, and one unsolicited offer per human player per round.
  - **Trade card.** As holder it bills each follower once (its pending offers wait up to 30 minutes for trade goods to
    pay with), and tells a strategy-token follow from a free one by the follower's strategy tokens. As follower it
    holds its commodities back for the holder's bill for 10 minutes (it neither offers them nor accepts another
    player's deal for them meanwhile) and honours the announced shape of the deal.
  - **Builder.** An offer is built with the bot's transaction buttons, one press per tick: `transaction`, the
    partner, what it asks for, what it gives (so an old Accept pressed during the build can only make the partner
    pay), then "Send the Offer". A counter-offer starts from "Reject and CounterOffer" on the offer it received.
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
    the acceptance rate and the trade goods gained by trading per seat.
- **Checks and Balances.** When it must give its picked card away, it plans the card and the recipient together,
  among the players who can still receive a card: if the strongest of them is ahead of it (or leads the game), it
  gives them the card least useful to them; otherwise it gives the player with the fewest points the card most
  useful to them. The trade goods on a card, which the picker keeps, make that card a little more attractive.
- **Tactical actions.** At the start of its turn, `TacticalPlanner` scores every system it can
  activate and remembers the best plan for the turn:
  - **Expand**: a carrier (or other transport) takes ground forces (infantry first, mechs last, except
    that mechs go to hazardous planets first) to free planets, keeping one ground force (a mech when there is one) on each home planet and on Mecatol Rex, and avoiding systems
    covered by enemy space cannon. In rounds 1-4 every planet taken is worth an extra 0.8, fading to nothing by round
    8, so small planets are worth a token early. The last carrier to leave home takes a full load of spare infantry
    for its next expansions. Every ground force it brings lands: a mech on each hazardous planet it can, then one
    force on every other planet, then a second infantry on each hazardous planet (the cards that remove one become
    nearly free), then the rest spread over the planets, ready to move on next round. A carrier with a sister at home
    brings only what it lands plus a second infantry for each hazardous planet.
  - **Attack**: only a single opponent, and only when the exact combat odds (`CombatOdds`, with standing combat
    modifiers such as Fragile or Unrelenting) give at least 80% in space and on each planet it lands on. It always
    clears enemy ships out of its own home system. It attacks another player's home system only while it holds
    _Darken the Skies_ or _Conquer the Weak_ is unscored, taking the expected space cannon losses off its fleet
    first; other systems covered by enemy space cannon are avoided. The odds also count its own space cannon (PDS in
    the system, PDS II and other deep space cannon next to it) firing first, Assault Cannon on both sides (the
    cheapest non-fighter ship lost), anti-fighter barrage on both sides, Non-Euclidean Shielding (a sustained hit
    cancels two), Duranium Armor repairs, X-89 ΩΩ doubling ground combat and bombardment hits, and the expected
    bombardment hits on the planet the bot will bombard. Damaged ships join attacks but can no longer
    sustain damage, and damaged defenders cannot either. An attack can be worth it for objective
    progress alone. It also weighs the attack with the fighters at the origin riding along in the fleet's capacity
    (the infantry and mechs to land share what is left) and takes whichever plan scores better; the ground odds use
    the infantry and mechs actually landed.
  - **Produce** (`ProductionPlanner`): build at a space dock, scoring first. Activating a system to build is only
    worth it for at least 4 units, unless the build scores or advances an objective. In order:
    1. **Scoring.** A flagship or war sun (the cheaper) while _Engineer a Marvel_ is unscored, one that could reach
       Mecatol Rex or another home from that dock for _Achieve Supremacy_, and the flagship itself for _Unveil
       Flagship_. Destroyers to stack five (or eight) ships for _Raise a Fleet_ (_Command an Armada_) unless a big
       enough stack already exists; the fleet pool grows to match what it can field, never past 4 under _Fleet
       Regulations_. Dreadnoughts up to five for _Gather a Mighty Fleet_. Two ships with bombardment (dreadnoughts
       first) for _Make an Example of Their World_ and two with anti-fighter barrage (destroyers) for _Fight with
       Precision_. A few destroyers to move around for presence objectives.
    2. **Core.** Carriers up to two, the infantry the dock is short of a carrier load, and two mechs (four for
       _Mechanize the Military_).
    3. **Surplus.** Its flagship, a war sun when it has the technology, dreadnoughts up to three, upgraded cruisers
       and destroyers up to four, then plain cruisers up to two. Each only while the resources left could still fill
       the rest of the production with cheap units. Every flagship is worth building with money to spare, ahead of
       dreadnoughts, and its tier in `FlagshipRating` sets how much more it needs on top: nothing for S (Nekro,
       Ghosts, Nomad, L1Z1X, Deepwrought, Crimson Rebellion, Xxcha), 1.5 resources for A (Yin, Sol, Naalu, Yssaril,
       Empyrean, Keleres, Naaz-Rokha, Mahact), 3 for B (Jol-Nar, Winnu, Letnev, Mentak, Cabal), 4.5 for C (Muaat,
       Saar, Titans), 6 for D (Argent, Arborec, Ral Nel, Sardakk, Hacan), and 3.75 for a flagship not on the list.
       Plain destroyers are only built for objectives.
    4. **Fill.** The rest of the dock's production: fighters into the dock's 3 free fighter slots and the spare
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
    player has planets or structures but no ships.
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
  enemy ships, otherwise the one with the most capacity) or a group move. A ship leaving home stays as the guard only
  when the move would otherwise empty the home system. `TacticalRules` then walks the bot's own buttons step by step,
  in distance or ring mode, looking for the planned system in every part of a system picker that was split over
  several messages; if it still cannot find it, it activates the best system the picker does offer. Once it has
  taken an action, it only ends its turn. When its units
  share a system with another player's but no combat starts, it hands the choice over.
- **Exploration** (`ti4.ai.explore`). Everything an exploration gives is valued in resources: a trade good 1, a
  command token 2 (nothing once none is left in reinforcements), an action card 1 (nothing at the hand limit), its own
  commodity a third (0.6 with someone to wash it with), a relic fragment 1 (2 for the one that completes a set, 3 while
  Destroy Heretical Works needs two).
  - **Decks.** The decks are public, so a deck is worth the average of the cards still in its draw pile: an attachment
    by the resources and influence it adds (a research facility 0.3, or 1.6 on a planet with a technology specialty),
    the Demilitarized Zone by -1.5 less the space docks (4) and PDS (3) it returns, Mercenary Outfit 0.75, Freelancers
    1.5 when it could pay for a unit, Lost Crew two action cards, Derelict Vessel a secret objective. A full deck comes
    to about 1 per exploration. `TacticalPlanner` adds that to every planet nobody holds that a plan expands to or
    invades (with the infantry and mechs the plan lands there); an invaded planet someone holds is never explored.
  - **Offers.** A newly taken planet is explored through the bot's offer, from the deck with the better average when it
    has more than one trait. A Scanlink Drone Network offer (the planet it already holds) is answered only when the
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
    | Expedition | readies the planet when it is worth more than the infantry: its larger value while the AI still has something to build, 0.3 of it otherwise; never with Pre-Fab Arcologies or a planet that is ready |
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
  - **Relic fragments.** With three of one kind (unknown fragments fill in last), it purges exactly three as a
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

Four small generic changes don't reference `ti4.ai`:
- `GlobalSettings.ImplementedSettings.AI_PLAYERS_ENABLED`, the kill switch.
- A public `TestBedPress.standInEvent` with a configurable repost header.
- The Nekro hero's casualty and trade-good report goes to the player's game channel (`getCorrectChannel`) rather
  than the channel the button was pressed in. Nothing changes for humans, but an AI seat presses that button in its
  private thread, where the report's @mentions of the victims could add them to the thread.
- The bombardment hit prompt offers every defender an "Auto-assign Hits" button (dummy players already had one), so
  humans and AI seats alike can assign bombardment hits automatically.

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
  - The AI runtime ticks on a virtual clock, so a game takes about 25 seconds of real time.
  - A run ends at the victory point goal, when the objectives run out, or when nothing changes for two virtual
    hours (a stall). The outcome's report shows the game state, the AI's recent decisions and presses, what the bot
    logged, Discord calls the fake doesn't support, and the latest messages in the main channel, combat threads
    and private threads.
  - Each report also has a scoreboard (victory points after every round, each revealed objective and who scored
    it, secrets, techs, planets and trade goods per seat) and the AI's decisions counted by reason. A batch ends
    with the average victory points of the leader and of the whole table, the measure of the AI's strength.
  - By default it plays the first round:
    ```
    mvn -o test -Dtest=AiSelfPlayTest
    ```
  - Whole games, here eight in a row:
    ```
    mvn -o test -Dtest=AiSelfPlayTest -Dai.selfplay.full=true -Dai.selfplay.games=8
    ```
  - `-Dai.selfplay.factions=sol,jolnar,...` seats other factions (default: the first six supported ones). Every seat
    starts with only its own faction note and the five generic notes; the scoreboard lists the notes each seat holds,
    received and has in its play area.
  - `-Dai.trading=false` plays without trading, for an A/B comparison of the batch summary.
- In a dev guild:
  1. `/testbed apply` a preset that starts in `setup`, or create a game for self-play.
  2. `/ai add` once per AI seat.
  3. Play as the other seats, or watch. `{"do":"runCron","value":"AiRuntime"}` triggers an AI poll from a test-bed script.

## Status

| Version | Scope |
| --- | --- |
| V0 | Seat, runtime, actuation, fallback, `/ai` commands; simple phase decisions. |
| **V1 (in progress)** | Full turn play. Done: tactical actions, combat (including bombardment), Technological Singularity, several AI seats and self-play, scoring (spend objectives, custodians, Imperial), strategy card primaries and secondaries including Warfare's, research, status, action and agenda-phase secrets, agenda voting and Galactic Threat, Nekro's agent and hero, economy action cards, promissory notes, trading (offers, counter-offers, Trade card terms, debt), Diplomacy and Politics follows. Still to do: combat and agenda action cards, Sabotage, the mech secrets (Mechanize the Military, Occupy the Fringe), Strengthen Bonds. |
| V1.1 | Draft participation (factions reserved for AI seats, other picks made by the table). |
| V2 | Hidden aggression levels with drift and lock; attacks gated by aggression. |
| V3 | Deeper deals: votes for trade goods, multi-party pacts. |
| V4 | Extortion and pacts. |
| V5 | Action cards, leaders, more depth, then faction-specific rules beyond the shared set. |
