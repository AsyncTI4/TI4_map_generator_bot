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
    objectives. Production, research and following respect the reserve. Production also leaves the custodians' 6
    influence while its ships can reach Mecatol Rex, and the influence-leaning planets a Leadership secondary (or its
    own primary) would spend on command tokens while that card is still to come this round.
  - **Mecatol Rex.** It lands on Mecatol Rex when it can pay the custodians' 6 influence (on top of the reserve),
    pays it honestly, and scores the Imperial point whenever it plays Imperial while holding Mecatol Rex. The tactical
    planner values gaining it (an expansion or an attack) at three quarters of the points it could then cash, valued
    as points: one for its own unplayed Imperial and one for an _Overrule_ it may play while Imperial is still to be
    played (two with both). That makes the swipe, taking Mecatol Rex before Imperial, worth planning for, and it pushes
    those plans over the score at which a second tactical action is taken (Fleet Logistics, _Master Plan_) and above
    the component actions they are compared with.
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
- **Strategy cards.** Every card is picked with a plan for its use (`StrategyCardPlanner`): a list of concrete
  benefits its primary would bring this seat on the current board, valued on one scale (a victory point is 8, a trade
  good 1, the same scale as tactical plans). Nothing the seat would get anyway counts, so Imperial with nothing to
  score and no Mecatol Rex is worth only the secret it draws (no more than the strategy token a follow costs).
  | Card | Uses it plans for |
  | --- | --- |
  | Leadership | the tactical actions its 3 tokens unlock (the plans it could not otherwise afford), and acting first in a race for the custodians |
  | Diplomacy | locking a system with its planets that an enemy fleet can reach (home most, by what an attack on it before the seat's next turn would cost it; more when close to winning), and readying its best 2 planets (more with a spend objective revealed). Base-game Diplomacy has no buttons to resolve, so it is worth only its trade goods |
  | Politics | becoming speaker (more when it will want Imperial next round; a loss when it is already speaker, since the card hands the token away), 2 action cards (a point when they complete _Form a Spy Network_), and looking at the agenda deck once the custodians are taken |
  | Construction | a space dock early, PDS (more under threat), a structure objective it would complete; for the Thunder's Edge card also production at a dock without placing a token, so the units can move out this round |
  | Trade | 3 trade goods, its commodities, a spend objective they make payable, and leverage for deals |
  | Warfare | PoK: a command token plus one reactivation; Thunder's Edge: a free tactical action. Reactivations are a second build at home, building at home and then moving out, moving twice to reach a far system, or activating a system that holds its token |
  | Technology | the best technology it can research (and a second worth 6 resources); for Nekro, Propagation's tokens |
  | Imperial | an extra public objective this round, the Mecatol Rex point (less while an enemy fleet can reach it, half when it can take Mecatol this round), or a secret (only the strategy token a follow would cost, since following Imperial draws the same secret); plus denying the card to a Mecatol Rex holder still to pick |

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
  | Imperial | scores the best public objective it qualifies for, then the Imperial point (Mecatol Rex) or a secret | it has a spare strategy token and room in hand for a secret (it never swaps one secret for another) |
  | Technology | one free technology, a second for 6 resources if one is worth it (`ResearchPolicy`: generic value, informed by the technologies Nekro players most often end games with, plus progress on tech objectives it can still finish in the rounds left, about 1.5 researches a round until the leader is 2 points a round from winning, and Space Dock II for _Produce en Masse_; minus 0.7 per resource or influence lost exhausting a technology specialty planet for a prerequisite, which it then exhausts when paying, free with Psychoarchaeology, or AI Development Algorithm when that costs less); Nekro takes Propagation's 3 command tokens instead | a technology worth at least 3 (most technologies other than the weakest) is affordable after the scoring reserve; as Nekro, Propagation's 3 command tokens for the strategy token and 4 resources |
  | Leadership | 3 command tokens, plus up to 3 more bought with spare influence, within its reinforcements | spare influence buys up to 3 command tokens (3 influence each, no strategy token) |
  | Politics | makes itself speaker (or the player furthest behind), draws 2 action cards, and once the custodians are taken looks at the top 2 agendas, burying _Public Execution_ while it leads on points, agendas that punish the planet owner while it holds the most planets, and elections it would lose on influence (a work in progress that needs a redesign: votes are unknown in the action phase) | with a spare strategy token when _Form a Spy Network_ needs cards (right after Imperial when 2 cards complete it), or with 2 or more spare strategy tokens and room for 2 cards; never with no strategy token, a full hand or _Hold No Action Cards_ |
  | Construction | the Thunder's Edge card produces at the dock its plan chose (no token placed) and then places one structure; otherwise two structures: a space dock while it has a site, otherwise PDS on planets without structures. A dock site is another planet in a home system with more than one planet, or a planet worth 2 or more resources elsewhere, preferring planets worth 3 or more and those in its own slice (next to home or on the way to Mecatol Rex; Styx counts too); any planet for _Fuel the War Machine_ | a revealed or held structure objective is 1 or 2 structures short |
  | Diplomacy | first locks the threatened system its plan chose (PoK card only), then readies the 2 exhausted planets it can use best (the same test as a follow), otherwise its 2 most valuable | its best 2 exhausted planets make a spend objective payable at the status phase (even after passing), or, while it still has actions, pay at least 3 towards production it would actually build or the custodians (both need a tactic token), research (a Technology follow it still has a strategy token for, or the 6-resource second technology of its own primary; never Nekro's free Propagation) or Leadership tokens (within its reinforcements). It readies those planets. |
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
    worth the swap, 0.3, at most. Following _Imperial_, an exploration's secret, _Impersonation_ and _Strategize_ use it. Picking _Imperial_ only for its secret still counts just the strategy token it saves over following.
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
    figure relics, _Enigmatic Device_ and the _Sling Relay_ use) as long as at least one other player has not passed.
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
    overruling Imperial for its point, an extra public objective through Imperial, or both. So the seat keeps it
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
  - **Riders** (`AgendaCardRules`): _Imperial Rider_, _Trade Rider_ and _Politics Rider_, played as "after"s with the
    bot's queue, and worth their keep prior in the hand like any card the AI plays. In the after window, when its after
    slot is empty and it may play cards, it asks the forecast what each rider would bring on each outcome:
    ```
    gain(rider, outcome) = the seat's expected utility with the rider placed on the outcome
                           less its expected utility voting its current ballot
    ```
    The rider's own payoff (a point, 5 trade goods and what they unlock, 3 action cards and the speaker token), the
    votes it gives up (none for Nekro or under the Xxcha commander) and the rival stake other voters see in an
    _Imperial Rider_ are all in the two forecasts. It queues the best rider whose gain reaches the card's keep prior
    (`queueAnAfter`, `queueAfter_ac_<card>`, `lockAftersIn`), one per agenda, never over a queued entry such as Galactic
    Threat and never for an agenda the forecast cannot enumerate. When the queue plays the card the bot posts the
    outcome buttons; the AI re-runs the choice on the current forecast and places the rider at once, before the Sabotage
    window closes. It does not pass on "after"s while a played rider is unplaced (and gives up after 5 minutes); it does
    when the rider was cancelled, placed, or refused by the bot (a seat over the hand limit), and when voting has begun it
    drops a still queued rider and still places a played one. The AI never re-decides once it has locked the rider in,
    and every seat decides on the same table, so several seats may ride one agenda.
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
  - Not yet: combat cards, _War Machine_, _Rally_, _Parley_,
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
  - **Defense** (`DefenseOutlook`). Its home system, a system with its space dock, a Mecatol Rex it holds, a system whose
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
    (their spare ground forces land on the weakest planet); retaking a planet in its own home is worth the points
    that home holds; and production buys infantry, mechs, fighters or ships at the dock (ground forces on the dock
    planet) until the cost is gone or a unit no longer lowers it by half a point per resource, so a one-planet home is
    defended on the ground and a home with several planets in space, and a hopeless defence buys nothing. Not yet:
    screening (a ship parked on a chokepoint), pre-emptive strikes on a threatening fleet, defending with ships from
    more than one system in one action, planets an enemy needs in systems the seat does not guard, and Prophecy of
    Kings Warfare with Fleet Logistics unlocking a second fleet.
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
  commodity a third (0.6 with someone to wash it with), a relic fragment 1 (2 for the one that completes a set, 3 while
  Destroy Heretical Works needs two).
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
    with the average victory points of the leader and of the whole table, the measure of the AI's strength.
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
  - The batch summary counts the action cards: plays per seat per game, plays a Sabotage cancelled (per game and as a
    share of all plays), Sabotage plays per game, and each card's plays per seat per game next to the rate at which
    human players play it when they draw it. It also prints each card's plays per draw (a seat holding a copy at any
    point of a game is one draw), flags every card a rule plays in under half the human first-play rate, and lists
    the cards drawn most that no rule plays. `-Dai.actioncards.ab=true` plays half of the seats, a different half in
    each game, with the action card rules from before the statistics, and the summary compares the two groups: average
    final victory points, action cards played per seat per game and top finishes, with their seat counts.
- In a dev guild:
  1. `/testbed apply` a preset that starts in `setup`, or create a game for self-play.
  2. `/ai add` once per AI seat.
  3. Play as the other seats, or watch. `{"do":"runCron","value":"AiRuntime"}` triggers an AI poll from a test-bed script.

## Status

| Version | Scope |
| --- | --- |
| V0 | Seat, runtime, actuation, fallback, `/ai` commands; simple phase decisions. |
| **V1 (in progress)** | Full turn play. Done: tactical actions, combat (including bombardment), Technological Singularity, several AI seats and self-play, scoring (spend objectives, custodians, Imperial), strategy card primaries and secondaries including Warfare's, research, status, action and agenda-phase secrets, agenda voting and Galactic Threat, Nekro's agent and hero, component action cards (economy, Spy, Uprising, research and the rest), Sabotage and action card values from human play statistics and scoring stakes, promissory notes, trading (offers, counter-offers, Trade card terms, debt), Diplomacy and Politics follows. Still to do: combat and agenda action cards, the mech secrets (Mechanize the Military, Occupy the Fringe), Strengthen Bonds. |
| V1.1 | Draft participation (factions reserved for AI seats, other picks made by the table). |
| V2 | Hidden aggression levels with drift and lock; attacks gated by aggression. |
| V3 | Deeper deals: votes for trade goods, multi-party pacts. |
| V4 | Extortion and pacts. |
| V5 | Action cards, leaders, more depth, then faction-specific rules beyond the shared set. |

### Known limitations

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
- **Action cards, Overrule.** It only performs Imperial and Technology. The bot cannot yet run the other primaries
  from an overruled card, though human players also overrule Warfare (10% of plays), Construction (9%) and
  Leadership (6%). Overruling the seat's own readied Imperial and then playing it assumes the table accepts that.
  It is never the second action of _Master Plan_ or _Fleet Logistics_ (a component action card is never the second
  action), though a points play can wait for the turn after. The wait for the Mecatol Rex swipe trusts the highest
  ranked tactical action; if that action is never taken, the card is played at the pass.
- **Action cards, riders.** Only _Imperial_, _Trade_ and _Politics_ riders are played (Technology and Leadership next); the
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
