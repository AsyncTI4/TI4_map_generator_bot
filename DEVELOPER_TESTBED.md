# Developer test bed

Spin up a game with virtual players from a small JSON preset, then act as any seat, so every button and command
counts as that seat. Works in normal and fog games. For developers only; it has no effect on real games.

## Safety

Everything is gated three ways. Without all three, the bot behaves exactly as before.

1. **Global switch.** `testbed_enabled` is off by default. Turn it on once per bot (a dev bot, not production):
   ```
   /developer setting setting_name:testbed_enabled setting_value:true setting_type:bool
   ```
2. **Per game.** A game becomes a test bed through `/testbed apply` or `/testbed enable`. Both refuse a game that
   has a seated player without the developer role (bots do not count), unless the developer opts in; see
   [Games with real players](#games-with-real-players).
3. **Per user.** Acting as another seat only applies to members with a developer role
   (`JdaService.developerRoles`, which includes admins).

### Games with real players

`/testbed enable allow_real_players:true` turns the test bed on in a game with real (non-developer) players, for
example to unblock a stuck game or reproduce a bug in place. Extra rules apply there:
- **Announced:** enabling and disabling post a notice in the game channel. Every button press made as another
  player's seat, and every panel press, is posted to the main channel (the GM activity log in fog games, so no
  faction leaks).
- **Buttons only:** act-as applies to buttons, selects, modals and the panel. Slash commands, typed messages and
  whispers, and map or card views stay the developer's own, so a developer cannot chat as a player or see their
  fog or hidden cards through act-as.
- **Blocked:** `/testbed reset` (it would unseat everyone), `/testbed apply` and `/testbed run`.

## Quick start

1. Create a new game (normal or fog) and go to its channel.
2. `/testbed apply preset:2p-combat`
3. Press buttons inside a seat's private channel or cards-info thread to act as that seat. For shared channels
   (strategy card follows, agenda votes, scoring), switch with `/testbed act_as faction_or_color:nekro` or the panel.
4. `/testbed panel` opens your private control panel.
5. `/testbed reset confirm:true` puts the game back exactly as it was before `apply`, so you can apply again in
   the same game.

## Commands

| Command | What it does |
| --- | --- |
| `/testbed apply [preset] [file]` | Sets up a fresh game from a shipped preset or an attached `.json`. |
| `/testbed act_as [faction_or_color]` | Acts as that seat in shared channels; leave empty to be yourself again. |
| `/testbed panel` | Private panel: act-as buttons, +1 TG/commodity/tactic/strategy, ready all, make active player, cards info, shortcuts, start a phase. |
| `/testbed run [script] [file] [stop_on_fail] [reset]` | Runs a test script: presses buttons as seats, checks state and messages, posts a ✅/❌ report and a Markdown log. |
| `/testbed status` | Shows the test-bed state of this game. |
| `/testbed reset confirm:true` | Deletes the test bed's channels and restores the snapshot `apply` took, so the game is exactly as it was before (seats, map, decks, played strategy cards, round, stored values). |
| `/testbed enable [allow_real_players]` / `disable` | Marks or unmarks an existing game as a test bed. `allow_real_players:true` is needed when real players are seated. |

## Who am I acting as?

For a developer in a test-bed game, in this order:

1. inside a seat's private channel or cards-info thread: that seat;
2. otherwise, the seat chosen with `act_as` or the panel;
3. otherwise, yourself.

In fog games, a button press made as another seat is logged in the GM activity thread as `[dev <name> as <faction>]`.

## Presets

Shipped presets live in `src/main/resources/data/testbed/` and are validated by `TestBedDataTest`. You can
also attach your own `.json` with `file:`; nothing needs to be committed.

```json
{
  "name": "2p-combat",
  "description": "Two fleets above Mecatol Rex; the combat thread opens on apply.",
  "fog": false,
  "mapString": "{18} 19 20 ...",
  "you":   { "faction": "letnev", "home": "301", "units": { "101": "2 dn, cv, 4 ff" } },
  "seats": [ { "faction": "nekro", "home": "310", "units": { "101": "ca, 2 dd, 3 ff" } } ],
  "defaults": { "acs": 3, "tg": 4, "ccs": "3/3/2" },
  "start": "action",
  "combat": ["101"]
}
```

| Field | Meaning |
| --- | --- |
| `fog` | `true` or `false` makes `apply` refuse a game of the other kind; omit it for either. |
| `mapString` | Optional. Without it, a standard 6-player map with homes at 301/304/307/310/313/316. |
| `you` | Your own seat. Omit it to only watch. |
| `seats` | Virtual seats (`TestSeat1`, `TestSeat2`, ...). `{}` is a random base/PoK faction. |
| `defaults` | Hand fields applied to every seat that does not set them. |
| `start` | `setup` (default), `strategy` or `action`. `action` gives seats without `sc` the lowest free strategy card. |
| `combat` | Positions where a combat check runs after the start phase. Needs `start: action`. |
| `shortcuts` | Named mini-scripts shown under **Shortcuts** in the panel (see below). |

Per seat (all optional):

| Field | Example | Notes |
| --- | --- | --- |
| `faction`, `color`, `home`, `speaker` | `"nekro"`, `"blue"`, `"310"`, `true` | Defaults: random faction, preferred color, next default home, first seat speaker. |
| `sc` | `5` | Only used with `start: action`. |
| `acs`, `sos`, `relics` | `3`, `"ans"`, `["sabo1", 2]` | A number draws randomly from the game's deck; ids draw that card. |
| `techs`, `planets` | `["amd"]`, `["mecatolrex"]` | |
| `tg`, `commodities`, `ccs` | `4`, `2`, `"3/3/2"` | `ccs` is tactic/fleet/strategy. |
| `leaders` | `{ "unlock": ["commander"], "exhaust": ["agent"] }` | Leader type or id. |
| `units` | `{ "101": "2 dn, cv", "home": "2 gf" }` | Any unit string `/add_units` accepts; `home` is the seat's home system. |

Unknown fields and ids fail the preset with every error listed at once. Problems that only show up while applying
(a card not in this game's deck, a missing leader) come back as warnings.

## Test scripts

A script is a list of steps run in order against a test-bed game: press buttons as seats, change state, and check
the results. It is a hand-written test suite in JSON. Shipped scripts live in
`src/main/resources/data/testbed/scripts/` and are validated by `TestBedDataTest`; attach your own with
`/testbed run file:`. The reference comes first; [Writing scripts](#writing-scripts) explains how to design one.
To have an AI agent write scripts for you, point it at [TESTBED_AGENT_GUIDE.md](TESTBED_AGENT_GUIDE.md).

```json
{
  "name": "follow-spends-token",
  "description": "Following Politics costs Hacan one strategy token; Jol-Nar cannot end Sol's turn.",
  "preset": "action-3p",
  "stopOnFail": true,
  "steps": [
    { "expect": { "state": "hacan.ccs", "equals": "3/3/2" } },
    { "as": "sol", "press": "strategicAction_3" },
    { "expect": { "state": "game.playedScs", "contains": "3" } },
    { "as": "hacan", "press": "sc_follow_3" },
    { "expect": { "state": "hacan.ccs", "equals": "3/3/1" } },
    { "as": "jolnar", "pressId": "FFCC_sol_turnEnd" },
    { "expect": { "ephemeral": "these buttons are for someone else" } },
    { "expect": { "state": "game.activePlayer", "equals": "sol" } }
  ]
}
```

### Reference

Script fields:

| Field | Meaning |
| --- | --- |
| `name`, `description` | Shown in autocomplete and the report. Scripts cannot hold comments; say it here or in a `note` step. |
| `preset` | Applied first when the game has no seated factions. In a game already set up with the same preset it is skipped (noted in the report); set up with a different one, the run stops and asks for `reset:true`. |
| `settleSeconds` | Pause after each press or action so Discord catches up. Default 2; a step can set its own. |
| `timeoutSeconds` | How long `press` waits for its button and a positive check waits to pass. Default 20; a step can set its own. |
| `stopOnFail` | Stop at the first ❌ (also `/testbed run stop_on_fail:true`, or per step). |
| `shortcuts` | Mini-scripts for the panel (see [Shortcuts](#shortcuts)). |

Each step has exactly one verb:

| Verb | Example | What it does |
| --- | --- | --- |
| `note` | `{ "note": "round 2 starts" }` | A line in the report. |
| `press` | `{ "as": "sol", "press": "strategicAction_3" }` | Finds a button the bot is showing and presses it as that seat, waiting up to `timeoutSeconds` for it to appear (see [Finding buttons](#finding-buttons)). |
| `pressId` | `{ "as": "nekro", "pressId": "sc_follow_3" }` | Posts a one-button carrier message in the seat's own channel and presses it: fires any button id, shown or not. |
| `do` | `{ "do": "hand", "as": "all", "hand": { "tg": 0 } }` | `startPhase` (value), `setActivePlayer` (as), `setStored` (key, value), `removeStored` (key), `runCron` (value), `hand` (as: seat or `all`; hand: any preset seat fields), `actAs` (as or `you`). |
| `wait` | `{ "wait": 5 }` | Seconds. |
| `expect` | see below | A check; ✅ or ❌ in the report. |

Every step may also have `label` (report text instead of the generated one), `settleSeconds` and `stopOnFail`.
`press` and `pressId` take `in` to pin the channel.

Expectations (exactly one form per `expect`):

| Form | Example | Passes when |
| --- | --- | --- |
| Game state | `{ "state": "hacan.tg", "equals": "3" }` | The value equals (case and surrounding spaces ignored), or contains every item of `"contains"` and none of `"notContains"`. |
| Messages | `{ "in": "hacan:cards-info", "contains": "Reminder", "count": 1 }` | Among messages posted **since the script started**: each `contains` text is in some message; no message has a `notContains` text; `count` messages contain the single `contains` text; `noFactionLeak` finds no faction name, mention or emoji. |
| Reply | `{ "ephemeral": "these buttons are for someone else" }` | A reply or private follow-up to the previous press contains the text. |
| Modal | `{ "modal": "tradeModal_" }` | The previous press opened a modal whose id starts with this. |

- **Seats** (`as`, scopes, state paths): a faction or color, `you`, `seat1`, `seat2`, … (the virtual seats in
  preset order; use these with random factions), and `all` (only for `do: hand`).
- **Scopes** (`in`): `main`, `actions`, `gm`, `<seat>` (its usual channel: the private channel in fog, otherwise
  the main channel), `<seat>:private`, `<seat>:cards-info`.
- **State paths** (lists are sorted and comma-joined):

  | Area | Paths |
  | --- | --- |
  | Seat resources | `<seat>.tg`, `commodities`, `ccs` (`tactic/fleet/strategy`), `scs`, `passed`, `followed` |
  | Seat hand | `acs` (count), `acIds`, `sos` (count), `soIds`, `pns` (promissory notes in hand) |
  | Seat played components | `pnsInPlay` (promissory notes in its play area), `sosScored`, `leaders` (`id:ready\|exhausted\|locked`), `exhaustedTechs`, `purgedTechs`, `relics`, `exhaustedRelics`, `planets`, `exhaustedPlanets`, `techs` |
  | Game | `game.phase`, `game.round`, `game.activePlayer` (faction), `game.speaker`, `game.playedScs` |
  | Game played components | `game.acDiscard`, `game.agendaDiscard`, `game.laws`, `game.revealedPos`, `game.purgedPns`, `game.exploreDiscard` |
  | Anything else | `stored:<key>` (any game stored value; many features keep their state there) |

- **Placeholders** are filled in just before a step runs, in any text of the step:

  | Placeholder | Becomes | Example |
  | --- | --- | --- |
  | `{ac:<id>}`, `{so:<id>}`, `{pn:<id>}` | The acting seat's (`as`) current hand number for that card | `ac_play_from_hand_{ac:sabo1}` |
  | `{<seat>.faction}`, `{<seat>.color}` | That seat's faction or color | `resolvePNPlay_{hacan.color}_sftt` |

  Seat placeholders resolve first, so they can be nested: `{pn:{hacan.color}_sftt}`. A card that is not in the
  seat's hand when the step runs fails the step with the hand's contents.

Presses take the normal button path (game lock, act-as, `FFCC_` faction check, handler, save), so a script tests
the real buttons, not a copy of their logic.

### Writing scripts

**1. Start from one behaviour.** Write it as a sentence: "When Hacan follows Politics, Hacan loses one strategy
token." One behaviour per script, named after it. A long script that tests five things stops being useful at
the first failure.

**2. Make it repeatable with the preset.** Do setup in the preset, not in steps:
- fixed factions (seat names are then checked when the script is validated), fixed homes and colors when they
  matter;
- specific cards (`"acs": ["sabo1"]`) for anything you check; random counts only for filler;
- start as late as possible: `"start": "action"`, `sc`, `units`, `leaders`, `combat`, so the script does not
  replay setup.
Reuse a shipped preset when one fits; otherwise ship a new one next to the script.

**3. Shape: precondition, action, outcome.** Check the starting state first (`hacan.ccs = 3/3/2`), so a ❌ later
means the behaviour broke, not the setup. Then one or a few presses, then the checks. Use `stopOnFail` when later
steps only make sense if earlier ones passed.

#### Finding buttons

Do not guess button ids; find them:
1. **From a run.** A `press` that finds nothing fails with every visible button as ``Label (`id`)``. The `.md` log
   has the full list. Running a draft with a deliberately wrong `press` is a quick way to see what is on screen.
2. **From the code.** Search for the label: `grep -rn '"End Turn"' src/main/java` finds
   `Buttons.red(player.factionButtonChecker() + "turnEnd", "End Turn")`. The id is `turnEnd`. Labels change with
   state: Sol's end-turn button reads `End Turn (+1 ability)` (`endOfTurnAbilities`), so `press: "End Turn"`
   never matches it. Use the id.
   `factionButtonChecker()` adds `FFCC_<faction>_`; `press` lets you leave that out.
3. **Dynamic ids.** Many ids carry a number or name: `strategicAction_<sc>`, `sc_follow_<sc>`,
   `sc_no_follow_<sc>`. Write the concrete value.

A button matches `press` **exactly** when the text is its full id, its id without the `FFCC_<faction>_` part
(`passForRound` matches `FFCC_sol_passForRound`), or its label (case ignored); it matches **by prefix** when the
text starts its id. `press` looks through the newest 25 messages of the seat's cards-info thread, then its usual
channel, then the main channel, newest first. It presses the first exact match, and only falls back to the first
prefix match when there is none, so `ac_play_from_hand_1` never presses card 12. Prefer ids: labels change and
often carry state ("Pass (2 abilities)"). Use `in` when the same button can appear in several places.

#### Playing components

Hand cards have numbered buttons whose number is assigned when the card is drawn, so use placeholders:

| Component | Give it with | Press | Check it was played |
| --- | --- | --- | --- |
| Action card | preset or `do: hand` `"acs": ["<id>"]` | `ac_play_from_hand_{ac:<id>}` in `<seat>:cards-info` | `<seat>.acIds` `notContains`, `game.acDiscard` `contains` |
| Secret objective (score) | `"sos": ["<id>"]` | `so_score_hand_{so:<id>}` | `<seat>.sosScored` `contains` |
| Promissory note | owned at setup, or traded | `resolvePNPlay_<pn id>`, e.g. `resolvePNPlay_{hacan.color}_sftt` | `<seat>.pnsInPlay`, `game.purgedPns` |
| Leader | preset `"leaders": { "unlock": [...] }` | the leader's button id from the code | `<seat>.leaders` (`id:exhausted`) |
| Tech, relic | preset `"techs"`, `"relics"` | the component action's button id from the code | `exhaustedTechs`, `purgedTechs`, `exhaustedRelics` |

Then press the card's follow-up buttons like any others, as each seat that must react (`no_sabotage` in `main`
for every other seat, for example). Cards whose resolution opens a form cannot be finished by a script yet: check
`{ "expect": { "modal": "<id prefix>" } }` and verify the rest by hand. See the shipped `ac-play` script with
the `ac-2p` preset, which holds known cards and draws nothing at random so the card is always in hand.

#### `press` or `pressId`

| Use | When |
| --- | --- |
| `press` | Default. It also proves the bot offered that button to that seat at that moment. |
| `pressId` | The button is not on screen yet or is hard to reach (an ability, a later step of a flow), or you are testing a guard by pressing another seat's button. It skips "was it offered?", so do not use it for flows a player clicks through. |

#### Choosing expectations

- **State first.** It is exact, instant and independent of wording. Messages are for what a player should see:
  notices, reminders, and anything that must not leak.
- **Short, stable fragments.** `"contains": "Reminder"`, not a whole sentence; avoid emoji, mentions and numbers
  that change.
- **`count` catches duplicates** ("exactly one reminder"), a common bug.
- **Preset output is invisible.** Message checks only see messages posted after the script starts, so anything
  the preset caused (a combat thread, setup notices) must be checked through state; see `combat-opens`.
- **Guards.** Press another seat's faction-checked button with `pressId` (`FFCC_sol_turnEnd` as Jol-Nar), then
  expect the `ephemeral` refusal and unchanged state. Shared buttons such as strategy card follows have no
  `FFCC_` check, so anyone may press them.
- **Fog.** Add `{ "in": "main", "noFactionLeak": true }` after any step that could announce something publicly.
  Do not use it in normal games, where faction names appear in the main channel all the time.

#### Semantics worth knowing

- `do: hand` **sets** `tg`, `commodities` and `ccs`, and **adds** cards, techs, units, planets and leader
  changes. `"acs": 2` draws two more cards.
- `acs` and `sos` in state are counts; `acIds` and `soIds` are the ids. To check that a card left the hand use
  `"notContains"` on `acIds`.
- `equals` compares as text: write `"3"`, not `3`.
- A message `contains` list means each text appears in some message, not all in one.
- Scripts change the game. Run them with `reset:true`, or in a new game, so a run never starts from what an
  earlier run left behind.

#### Timing

Discord is not instant, and a preset or a strategy card play posts many messages that arrive over several
seconds. The runner deals with that in three ways:
- **`press` waits** up to `timeoutSeconds` (default 20) for its button to appear, checking every 2 seconds.
- **Positive checks retry** until they pass or `timeoutSeconds` runs out: state checks, and message checks with
  only `contains`/`count`.
- **Absence checks run once**, after the step's `settleSeconds` pause (default 2): message checks with
  `notContains` or `noFactionLeak`. Retrying those would let them pass before the unwanted message arrived.

Raise `settleSeconds` on a step before an absence check that follows a busy action (a map render, a phase start:
try 5). Crons and timers need an explicit `wait` before their results are checked.

#### Run, read, fix

1. Run `/testbed run file:<your script>.json reset:true` in a test-bed game (or without `reset` in a new game of
   the right kind, normal or fog). `reset:true` resets the test bed and applies the script's preset, so every run
   starts from the same state.
2. Read the ❌ lines; the attached `.md` log has the untruncated expected/actual for every step.
   - `no button `X`; visible: …`: pick the right label or id from the list.
   - `unsupported interaction calls [...]`: the handler used part of the click the stand-in only fakes; check
     that step by hand once and mention it in the PR.
   - `found `X` in: …` under `noFactionLeak`: a real leak, or a check in the wrong channel.
3. Fix the script and run it again with `reset:true`.
4. When it passes twice in a row, move it to `data/testbed/scripts/`. The build then validates it on every change.

#### What scripts cannot do yet

Fill in modals, choose from select menus, read messages inside threads other than cards-info (combat threads),
react, or run slash commands (use `do` actions or the preset instead). Anything depending on dice rolls must be
checked on outcomes that do not depend on the roll.

## Shortcuts

The panel's **Shortcuts** button lists:
- **Preset shortcuts** (blue): a preset's `shortcuts`, each a label plus script steps, stored with the game on
  `apply`. Example: `{ "label": "Everyone to 5 TG", "steps": [ { "do": "hand", "as": "all", "hand": { "tg": 5 } } ] }`.
  Failures come back as a short private note instead of a full report.
- **Built-in shortcuts** (gray): `TestBedShortcuts.JAVA_SHORTCUTS`. A feature branch adds one entry there
  (id, label, `(game, actingSeat, event) -> status`). Shipped: clear active player, zero everyone's strategy tokens,
  dump seat state.

## How it works

- `TestBedService.resolveActingPlayer` is the single resolver. It is called from `ListenerContext` (buttons, selects,
  modals), `CommandHelper.getPlayerFromEvent` (slash commands), `MessageListener` (typed messages and whispers),
  `MapGenerator` (fog map perspective) and the viewer lookups listed below. It returns the normal player unless the
  switch, the game marker and the developer role all apply.
- Virtual seats are ordinary players with fake user ids starting `90000000000000`. Pings to them go nowhere.
- In normal games you are added to each virtual seat's cards-info thread. In fog games each seat gets a private
  channel `<game>-testseatN-private`, and you get the `<game> GM` role.
- Every channel or thread the test bed creates is recorded in the stored value `testBedChannels`; `reset` deletes
  only those.
- `apply` first copies the game file to `storage/testbed/<game>.txt`; `reset` restores that copy and reloads the
  game. The folder is outside `storage/maps`, so it is never loaded as a game or an undo file. A game applied
  before snapshots existed falls back to rebuilding: seats removed, map, played strategy cards and the action
  card, secret objective and relic decks reset.

## Regression guards

Tests in `src/test/java/ti4/service/testbed/` fail the build when an upstream change would break the test bed:
- `TestBedDataTest`: every shipped preset and script is its own test case; validation, short forms, and
  save-format safety of everything the test bed stores (game stored values are split on `,` and `:` when a game
  loads, so `TestBedService.store` refuses such values).
- `TestBedGameTest`: who a developer acts as, the no-op guarantee for real games, reset, every advertised state
  path, and the panels against Discord's 5×5 / 100-char / 80-char limits.
- `TestBedPressTest`: button matching and the stand-in click, including a check that every method JDA requires of
  a `ButtonInteraction` is answered (catches JDA upgrades).
Script validation also checks seat names against the preset, `pressId` length, and `setStored` values.

## Known gaps

These sites still identify the real Discord user rather than the seat being acted as:

| Site | Effect |
| --- | --- |
| `SecretObjectiveButtonHandler`, `DrawSecretService` | The "you drew …" private note is only shown when the presser is the seat itself; the cards still go to the seat. |
| `Join`, `Replace`, `UserLeaveServerListener`, `RoleService` | Membership and roles; intentionally the real user. |
| `CreateFoWGameService.createPrivateChannelForPlayer` | Needs a real member; the test bed creates virtual seats' channels itself. |
| `SusSlashCommandService` | Moderation logging; intentionally the real user. |

Already seat-aware: `ShowGameService`, `StellarConverterService`, `ShowActionCardsService`,
`ShowPurgedActionCards`, `ShowGarboziaActionCards`, `AgendaHelper.showDiscards`, the action card discard
autocomplete, `FowSetupTableOrderService.rollDice`, `PrivateCommunicationsCheck` and `FOWCombatThreadMirroring`.
Private messages to virtual seats (`MessageHelper.sendPrivateMessageToPlayer`) go to their cards-info thread or
fog private channel instead of being dropped.

Other limits:
- Code that looks a virtual seat up as a Discord member or user gets nothing back. Report anything that breaks
  because of that.
- `reset` restores the game exactly only when `apply` took a snapshot (any apply since snapshots were added).
  Without one it rebuilds the main parts but not revealed objectives, agendas, explores or game options.
