# Developer testbed

Set up a game with virtual players from a small JSON preset, then act as any seat, so every button counts as that
seat. Works in normal and fog games, for developers only. With the switch off, the bot behaves exactly as before.

[Start](#start) · [Play solo](#play-solo) · [Set up any component](#set-up-any-component) ·
[Add a test button](#add-a-test-button) · [Write a script](#write-a-script) · [Your own files](#your-own-files) ·
[Reference](#reference) · [Safety](#safety) · [How it works](#how-it-works)

Writing scripts with an AI agent: point it at [TESTBED_AGENT_GUIDE.md](TESTBED_AGENT_GUIDE.md). Agents only
write or edit testbed files when you ask (see [AGENTS.md](AGENTS.md)), and may extend the framework with new
paths, fields and actions but not change existing behaviour.

## Start

1. Turn the testbed on, once per bot (a dev bot, not production):
   ```
   /developer setting setting_name:testbed_enabled setting_value:true setting_type:bool
   ```
2. In a new game (normal or fog): `/testbed apply preset:action-3p`. Autocomplete lists every preset with a
   description.
3. `/testbed panel` opens your private control panel.
4. `/testbed reset confirm:true` puts the game back exactly as it was before `apply`.

| Command | What it does |
| --- | --- |
| `/testbed apply [preset] [file]` | Sets up a fresh game from a preset or your own `.json`. |
| `/testbed panel` | Your private panel: switch seats, Follow Turn, test buttons. |
| `/testbed act_as [faction_or_color]` | Act as a seat, `turn` to follow the active player, empty for yourself. |
| `/testbed run [script] [file]` | Runs a test script (`all` runs every script) and posts a ✅/❌ report. |
| `/testbed reset confirm:true` | Deletes the testbed's channels and restores the game as it was before `apply`. |
| `/testbed reload` | Re-reads presets, scripts and test button files after you edit them, and lists any invalid file. |
| `/testbed enable [allow_real_players]` / `disable` | Marks or unmarks an existing game as a testbed. |

## Play solo

**Who you act as**, in this order:
1. inside a seat's private channel or cards-info thread: that seat;
2. otherwise the seat picked in the panel or with `/testbed act_as` (the active player with **Follow Turn**);
3. otherwise yourself.

**The panel** is one private message that updates in place; it never posts new ones.
- **Header:** round, phase, active player, preset (and whether reset can restore it), and who you act as with
  their TG, commodities and command tokens.
- **Seat buttons, Me, Follow Turn.** Follow Turn makes you act as whoever is active, so you can play a whole round
  without switching seats.
- **Make Active** starts the turn of the seat you act as. **Refresh** redraws the panel.
- **Turn Buttons** shows the buttons of the bot's latest message for the active player (Tactical Action, play a
  strategy card, Pass, End Turn, …), as they appear in the channel. Pressing one presses the real button as the
  active player; the page then shows the next buttons, so you can play turns without scrolling. Buttons that open
  a form (modal) still need the button in the channel. Buttons a real player would only see in an ephemeral reply
  (Component Action, scoring a secret, …) are re-posted in that seat's own channel (cards-info thread, or private
  channel in fog), marked 🧪, so the panel and scripts can press them.
- **Test Buttons** opens the test buttons page: pick a group, page with ◀ ▶, **Back** to the panel. Results show
  in the panel's status line.

Built-in groups:

| Group | Buttons (they act on the seat you act as) |
| --- | --- |
| Seat | +1 TG, +1 Commodity, +1 Tactic CC, +1 Strategy CC, Ready All, Cards Info, Show Seat State |
| Game | Start Strategy, Start Action, Start Status Scoring, Start Agenda, Clear Active Player, Zero All Strategy CCs |
| Preset | The applied preset's `shortcuts` |

Plus one group per test button file and per Java group ([Add a test button](#add-a-test-button)).

## Set up any component

To test a component, get it into play with the preset (or mid-script with `do: hand`), then press its button.

| Component | Preset field | Example |
| --- | --- | --- |
| Action card, secret objective, relic | seat `acs`, `sos`, `relics` | `"acs": ["sabo1"]` |
| Technology, planet, leader | seat `techs`, `planets`, `leaders` | `"leaders": { "unlock": ["commander"] }` |
| Units | seat `units` | `"units": { "home": "2 dn, 4 ff" }` |
| Promissory note in hand | seat `pns`: an id, or `<short id>:<owner>` for colour notes | `"pns": ["gift", "sftt:hacan"]` |
| Public objective | game `revealedObjectives`, seat `scoredObjectives` | `"revealedObjectives": ["corner"]` |
| Law in play | game `laws` (`id` or `id:elected`) | `"laws": ["arms_reduction"]` |
| Token or attachment | game `tokens`: tile position or planet | `"tokens": { "101": ["frontier"], "mecatolrex": ["dmz"] }` |
| Border anomaly | game `borderAnomalies`: tile position to `<n\|ne\|se\|s\|sw\|nw>:<type>` | `"borderAnomalies": { "202": ["n:spatial_tear"] }` |
| Relic fragment | seat `fragments` | `"fragments": ["crf1"]` |
| Tile outside the map string (maps A-G, corners, Fracture) | game `tiles`: position to tile id | `"tiles": { "a000": "39", "tl": "82" }` |
| Fog option | game `fowOptions` (fog presets) | `"fowOptions": ["map_connections", "ghost_hexes"]` |
| Game stored value (map sectors, feature state) | game `stored` | `"stored": { "fowMapSegments": "core=000:3" }` |
| Breakthrough | seat `breakthrough`: `unlocked` or `exhausted` | `"breakthrough": "unlocked"` |
| TG, commodities, command tokens | seat `tg`, `commodities`, `ccs` | `"ccs": "3/3/2"` |
| State a feature keeps in stored values | script `do: setStored` | `{ "do": "setStored", "key": "x", "value": "y" }` |

Still missing something? Add a Java test button that sets it up, or add a preset field: the model is
`TestBedPreset.Seat`, applying it is `TestBedComponentService`, validation is `TestBedPresetService`.

## Combat

A preset's `combat` positions open the real combat thread on apply (see `2p-combat`). From there:

- **Panel:** **Turn Buttons** also shows the newest buttons in the combat thread of the seat you act as. Pressing
  one counts as that seat; switch seats to roll for the other side.
- **Fog threads:** clicking a button in a seat's own copy of a combat thread (in its private channel) acts as that
  seat, without switching act-as.
- **Scripts:** the scope `<seat>:combat` is that seat's newest combat thread, for `press` and `expect`.
- **Fixed dice:** set the stored value `testBedDice` to space-separated results, e.g. `10 10 1`. Presses made by
  the testbed (panel, scripts, test buttons) use them in order; leftovers wait for the next press, and an empty
  list goes back to random. Buttons you click yourself in Discord still roll random dice. `2p-combat` has
  **Next dice all hit / all miss** test buttons; `combat-roll` is the example script.

## Add a test button

A test button is either a few script steps (JSON, no Java) or a piece of Java code. Each belongs to a group.

**JSON: steps, no code.** Use this for anything a script step can do: press buttons as seats, give cards or
units, start a phase, set stored values. Personal buttons go in a file in `data/testbed/local/shortcuts/`; team
buttons in `data/testbed/shortcuts/shared.json` (empty to start) or a new file per feature. One file is one group:

```json
{
  "group": "Fog QoL",
  "description": "Buttons for testing the fog quality-of-life features.",
  "fog": true,
  "shortcuts": [
    { "label": "Everyone to 0 strategy CCs",
      "steps": [ { "do": "hand", "as": "all", "hand": { "ccs": "3/3/0" } } ] },
    { "label": "Seat 1 passes",
      "steps": [ { "as": "seat1", "pressId": "FFCC_{seat1.faction}_passForRound" } ] }
  ]
}
```

- `fog`: `true` shows the group only in fog games, `false` only in normal games; leave it out for both.
- Steps are script steps ([Write a script](#write-a-script)); seats are `you`, `seat1`, `seat2`, … or factions.
- Empty groups are hidden. `/testbed reload` picks up changes; the build validates every file.

**Java: real logic.** Use this when a button needs code (call a service, build a tricky state). Add an entry to
`src/main/java/ti4/service/testbed/TestBedFeatureShortcuts.java`:

```java
public static final List<JavaShortcut> SHORTCUTS = List.of(
        new JavaShortcut("Combat", "homeFighters", "5 Fighters At Home", (game, seat, event) -> {
            if (seat == null) return "Pick a seat to act as first.";
            AddUnitService.addUnits(event, seat.getHomeSystemTile(), game, seat.getColor(), "5 ff");
            return "Added 5 fighters to " + seat.getFaction() + "'s home system.";
        }));
```

- `(group, id, label, action)`: the action gets the game, the seat you act as (may be `null`) and the click, and
  returns the status line. Ids must be unique within a group; labels at most 80 characters.
- A Java group with the same name as a built-in one (`Seat`, `Game`) adds to it. Java changes need a restart.

A preset can also carry buttons in its `shortcuts` field; they appear as the **Preset** group.

## Write a script

A script is a list of steps run in order: press buttons as seats, change state, check the results. It is a test
suite in JSON. Shared scripts live in `src/main/resources/data/testbed/scripts/`, yours in
`data/testbed/local/scripts/`; you can also attach one with `/testbed run file:`.

```json
{
  "name": "follow-spends-token",
  "description": "Following Politics costs Hacan one strategy token; Jol-Nar cannot end Sol's turn.",
  "preset": "action-3p",
  "stopOnFail": true,
  "steps": [
    { "expect": { "state": "hacan.ccs", "equals": "3/3/2" } },
    { "as": "sol", "press": "strategicAction_3" },
    { "as": "hacan", "press": "sc_follow_3" },
    { "expect": { "state": "hacan.ccs", "equals": "3/3/1" } },
    { "as": "jolnar", "pressId": "FFCC_sol_turnEnd" },
    { "expect": { "ephemeral": "these buttons are for someone else" } },
    { "expect": { "state": "game.activePlayer", "equals": "sol" } }
  ]
}
```

A script with a `preset` always starts clean: the testbed is reset, the preset applied, and step 1 waits until
every seat's new hand has arrived in its cards-info thread.
`selftest-core` and `selftest-components` are worked examples that exercise most of the features.

**1. One behaviour per script**, written as a sentence first ("following Politics costs a strategy token").

**2. Setup belongs in the preset:** fixed factions (seat names are then checked when the script is validated),
specific cards for anything you check (`"acs": ["sabo1"]`), and `"start": "action"` with `sc`, `units`,
`leaders` or `combat` so the script does not replay setup.

**3. Precondition, action, outcome.** Check the starting state first, then press, then check.

**Finding buttons.** Never guess ids.
- From a run: a `press` that finds nothing lists every visible button as ``Label (`handler`, for <seat>)`` and
  what the game is waiting on.
- From the code: `grep -rn '"End Turn"' src/main/java` finds `Buttons.red(... "turnEnd", "End Turn")`.
- Prefer ids over labels: labels change with state (Sol's `End Turn (+1 ability)`).
- `factionButtonChecker()` adds `FFCC_<faction>_`; `press` lets you leave it out. Write dynamic ids with their
  value (`strategicAction_3`).

**`press` or `pressId`.** `press` (the default) also proves the bot offered the button to that seat. `pressId`
posts the button itself and presses it: for buttons not on screen yet, abilities, and wrong-seat guard checks.

**Playing hand cards.** Their buttons are numbered when drawn, so use placeholders:

| Component | Press | Check |
| --- | --- | --- |
| Action card | `ac_play_from_hand_{ac:<id>}` (in `<seat>:cards-info`) | `<seat>.acIds` `notContains`, `game.acDiscard` |
| Secret objective | `so_score_hand_{so:<id>}` | `<seat>.sosScored` |
| Promissory note | `resolvePNPlay_<id>`, e.g. `resolvePNPlay_{hacan.color}_sftt` | `<seat>.pnsInPlay`, `game.purgedPns` |
| Leader, tech, relic | the button id from the code | `leaders`, `exhaustedTechs`, `exhaustedRelics` |

Then press each follow-up button as the seat that must react (`no_sabotage` in `main`, for example).

**Choosing checks.** State first (exact and instant); messages for what a player sees, with short fragments of
fixed text (card names often render as emoji, so `Politics` is not in the play message);
`count` to catch duplicates; `buttons` / `noButtons` for what a seat is offered next; `noFactionLeak` on `main`
in fog games only, with `"since": "start"` so it covers the whole run. A message check sees only messages posted
since the last `press` or `do` (`"since": "start"`: since the script started). Messages posted before the
script started (everything the preset did) are invisible, so check those through state.

**Run, read, fix.** `/testbed run file:<script>.json` in a testbed game or a new game of the right kind. Read the
❌ lines; the attached `.md` log has the full detail: time per step, what each press replied or re-posted, and at
the first failure a snapshot of the game (phase, who it waits on, every seat's state, the last messages per
channel). A press fails when the handler threw (`the handler threw`) or the bot refused the seat (`the bot
refused`). `unsupported interaction calls` means the handler used something the stand-in click only fakes:
check that step by hand once. When it passes twice, keep it in
`data/testbed/local/scripts/` or share it in `data/testbed/scripts/`; `/testbed run script:all` runs it with the
others.

**Not possible yet:** filling in modals, select menus, typing messages into combat threads, reactions and slash
commands. Dice can be fixed with `testBedDice` ([Combat](#combat)).

## Your own files

| Where | Who sees it | Use for |
| --- | --- | --- |
| `src/main/resources/data/testbed/local/{presets,scripts,shortcuts}/` | only you (git ignores it) | day-to-day presets, scripts and test buttons |
| `src/main/resources/data/testbed/` (`*.json`, `scripts/`, `shortcuts/`) | everyone (committed) | files the team should keep, e.g. shipped with a feature |

Local files load next to the shipped ones. A local preset or script with the same name replaces the shipped one;
local test button files appear as their own groups. After editing, run `/testbed reload` (no restart).
`mvn -o test -Dtest=TestBedDataTest` validates every file, yours included. `data/testbed/local/README.md` has
copy-paste templates.

## Reference

### Presets

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
| `fog` | `true`/`false` makes `apply` refuse the other kind of game; leave out for either. |
| `mapString` | Optional; default is a standard 6-player map with homes at 301/304/307/310/313/316. |
| `you` | Your own seat; leave out to only watch. |
| `seats` | Virtual seats (`TestSeat1`, …); `{}` is a random base/PoK faction. At most 8 seats in total. |
| `defaults` | Seat fields for every seat that does not set them. |
| `start` | `setup` (default), `strategy` or `action` (seats without `sc` get the lowest free card). |
| `combat` | Positions where a combat check runs after the start phase; needs `start: action`. |
| `revealedObjectives`, `laws`, `tokens`, `borderAnomalies` | Game state; see [Set up any component](#set-up-any-component). |
| `tiles` | Extra tiles by position, placed over the map string: maps A-G (`a000`-`g848`), corners, Fracture. |
| `fowOptions` | Fog options switched on at apply (names as in the FoW options, e.g. `map_connections`). `fow_plus` turns on full FoW+ mode, including the options it forces. |
| `stored` | Game stored values set at apply. Unlike script `setStored`, values may contain `:` and `,` (sector definitions do). |
| `shortcuts` | Test buttons for the **Preset** group. |

Seat fields (all optional): `faction`, `color`, `home`, `speaker`, `sc`; `acs`, `sos`, `relics` (a number draws
randomly, ids give that card, e.g. `["sabo1", 2]`; an action card held by another seat or in the discard is moved
over); `techs`, `planets`; `tg`, `commodities`, `ccs` (`tactic/fleet/strategy`); `leaders`
(`{ "unlock": ["commander"], "exhaust": ["agent"] }`); `units` (`{ "101": "2 dn, cv", "home": "2 gf" }`); `pns`,
`scoredObjectives`, `fragments`, `breakthrough`. Unknown fields
and ids fail with every error listed at once; problems that only show while applying come back as warnings.

### Scripts

| Script field | Meaning |
| --- | --- |
| `name`, `description` | Shown in autocomplete and the report. JSON has no comments: explain here or in `note` steps. |
| `preset` | Reset and applied before the steps. |
| `stopOnFail` | Stop at the first ❌ (also per step). |
| `settleSeconds` | Pause after each press or action, after its message edits have landed (default 0.5, decimals allowed; also per step). `setStored`, `removeStored` and `actAs` skip it unless the step sets it. |
| `timeoutSeconds` | How long `press` and positive checks wait (default 20; also per step). |
| `shortcuts` | Test buttons, like a preset's. |

| Verb | Example |
| --- | --- |
| `note` | `{ "note": "round 2 starts" }` |
| `press` | `{ "as": "sol", "press": "strategicAction_3", "in": "main" }` |
| `pressId` | `{ "as": "nekro", "pressId": "sc_follow_3" }` |
| `do` | `startPhase` (value), `setActivePlayer` (as), `setStored` (key, value), `removeStored` (key), `runCron` (value), `hand` (as: seat or `all`; hand: seat fields), `actAs` (as or `you`) |
| `wait` | `{ "wait": 5 }` (seconds) |
| `expect` | one of the checks below |

Every step may also have a `label` for the report.

| Check | Example | Passes when |
| --- | --- | --- |
| State | `{ "state": "hacan.tg", "equals": "3" }` | equals (as text, case ignored), or contains every `contains` and no `notContains` |
| Messages | `{ "in": "hacan:cards-info", "contains": "Reminder", "count": 1 }` | among messages since the last `press` or `do`; also `notContains`, `noFactionLeak`, `matches` (regex), `attachment` (file name contains), `buttons`, `noButtons`, `since` |
| Reply | `{ "ephemeral": "these buttons are for someone else" }` | a reply to the previous press contains it |
| Modal | `{ "modal": "tradeModal_" }` | the previous press opened a modal whose id starts with it |

- **Seats:** a faction or color, `you`, `seat1`, `seat2`, … (virtual seats in preset order), and `all` (only for
  `do: hand`).
- **Scopes (`in`):** `main`, `actions` (normal games only; fog games have no actions channel), `gm` (fog games
  only), `<seat>` (the seat's own channel from `getCorrectChannel()`: its private channel when it has one, in fog
  or not, otherwise the main channel), `<seat>:private`, `<seat>:cards-info`, `<seat>:combat` (its newest combat
  thread).
- **State paths:** `<seat>.` + `tg`, `commodities`, `ccs`, `tacticalCcs`, `fleetCcs`, `strategyCcs`, `vp`,
  `debt`, `scs`, `passed`, `followed`, `acs` (count), `acIds`, `sos` (count), `soIds`, `pns`, `pnsInPlay`,
  `sosScored`, `posScored`, `fragments`, `breakthroughs`, `leaders`, `techs`, `exhaustedTechs`, `purgedTechs`,
  `relics`, `exhaustedRelics`, `planets`, `exhaustedPlanets`;
  `game.` + `activeSystem`, `phase`, `round`, `activePlayer`, `speaker`, `playedScs`, `acDiscard`, `agendaDiscard`,
  `laws`, `revealedPos`, `purgedPns`, `exploreDiscard`, `borderAnomalies` (`<tile>:<direction>:<type>`);
  `tile.<position>.` + `units` (`space:blue_dd=2`), `ccs`, `tokens`, `planets`;
  `planet.<id>.` + `owner`, `units`, `tokens`; `stored:<key>`. Lists are sorted and comma-joined.
- **Placeholders:** `{ac:<id>}`, `{so:<id>}`, `{pn:<id>}` (the acting seat's hand number), `{<seat>.faction}`,
  `{<seat>.color}`. Seat placeholders resolve first, so they can sit inside card ones: `{pn:{hacan.color}_sftt}`.
- **Semantics:** `do: hand` sets `tg`, `commodities`, `ccs` and `breakthrough`, and adds cards, techs, units,
  planets, notes, fragments, scored objectives and leader changes. `press` prefers an exact id or label over an
  id prefix, and a prefix never stops inside a number, so `ac_play_from_hand_1` never presses card 12. `press`
  skips disabled buttons (`pressId` does not). Among matches it prefers the acting seat's own buttons, then the
  newest message, and says when others also matched. A press passes only if the handler ran for that seat (a form
  opening counts), or when the very next step is an `ephemeral` check, which is how wrong-seat guard tests expect
  the refusal; it waits for the handler's message edits before settling. Presses wait for their button and
  positive checks retry until `timeoutSeconds`, finishing as soon as the message lands. `notContains`,
  `noFactionLeak` and `noButtons` keep watching until nothing new arrives for a second (or `timeoutSeconds`), and
  fail on the first hit. `buttons` looks at messages posted or edited since the step began; `noButtons` at every
  message the channel shows.

## Safety

1. **Global switch** `testbed_enabled`, off by default (see [Start](#start)). Setting it as text `true` also works.
2. **Per game:** `apply` and `enable` refuse a game with a seated non-developer (bots do not count) unless
   `enable allow_real_players:true` is used.
3. **Per user:** only members with a developer role (`JdaService.developerRoles`, which includes admins) can use
   `/testbed` or act as another seat.

**Games with real players** (`enable allow_real_players:true`, e.g. to unblock a stuck game):
- enabling and disabling post a notice; every button press as another player's seat and every panel press is
  posted to the main channel (the GM activity log in fog games);
- act-as covers buttons, selects, modals and the panel only; slash commands, chat and map or card views stay
  your own;
- `reset`, `apply` and `run` are refused.

## How it works

- `TestBedService` decides who acts. Hooks: `ListenerContext` (buttons, selects, modals), `CommandHelper` (slash
  commands), `MessageListener` (chat and whispers), `MapGenerator` (fog map), and the card and discard viewers.
  Each returns the normal player before doing anything else when the switch is off.
- Virtual seats are ordinary players with fake user ids starting `90000000000000`. Their private messages go to
  their cards-info thread or fog private channel.
- Normal games: you are added to each virtual seat's cards-info thread. Fog games: each seat gets a private
  channel `<game>-testseatN-private` and you get the `<game> GM` role.
- Channels the testbed creates are recorded in `testBedChannels`; `reset` deletes only those.
- `apply` copies the game file to `storage/testbed/<game>.txt` first; `reset` restores and reloads it. Games
  applied before snapshots existed fall back to rebuilding seats, map, played strategy cards and the main decks.
- Script presses build a stand-in click on a real message and run it through `ButtonProcessor.processNow`, the
  normal button path with the game lock. `pressId` carrier messages delete themselves afterwards.
- A script run listens to Discord message events (`TestBedMessageLog`) instead of re-reading channel history:
  checks and button searches wake when a message arrives, is edited or deleted. A channel's recent history is
  read once, the first time a press searches it.
- Presets, scripts and test button files are read once and cached; `/testbed reload` re-reads them.

### Regression guards

`src/test/java/ti4/service/testbed/` fails the build when a change would break the testbed: every preset,
script and test button file is its own test case; the panel and test button pages are checked against Discord's
limits; every state path and built-in group must exist; the stand-in click must answer every method JDA
requires; everything the testbed stores must be safe for the game save format.

### Known gaps

- `SecretObjectiveButtonHandler` and `DrawSecretService` show the "you drew …" note only when the presser is the
  seat itself; the cards still go to the seat.
- Membership and role code (`Join`, `Replace`, `UserLeaveServerListener`, `RoleService`) and moderation logging
  (`SusSlashCommandService`) always use the real user.
- Code that looks a virtual seat up as a Discord member gets nothing back; report anything that breaks.
