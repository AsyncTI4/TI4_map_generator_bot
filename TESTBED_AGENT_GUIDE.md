# Testbed agent guide

> Only write or edit testbed files when the developer explicitly asks for it (see "Testing" in
> [AGENTS.md](AGENTS.md)). Normal JUnit tests and Maven builds are not restricted.

Instructions for an AI agent helping a developer write testbed scripts: JSON files that press buttons as virtual
seats in a Discord game and check the results. A developer points you here with something like "read
`TESTBED_AGENT_GUIDE.md` and write a testbed script for X". The general `AGENTS.md` rules (no production
comments, Discord limits) still apply to any Java you touch.

Read [DEVELOPER_TESTBED.md](DEVELOPER_TESTBED.md) sections "Write a script" and "Reference" first; they are the
format reference. This file covers how you work with it.

## What you can and cannot do

- **You can** write and change scripts and presets, validate them with the build, look up button ids and state in
  the code, and extend the framework (see "Extending the framework").
- **You cannot run a script.** It needs a live bot, a Discord server and a developer account. Never say a script
  passes; say it is valid and what the developer should see when they run it.
- **You cannot change existing testbed behaviour.** The framework is shared. Additions are fine: new state paths,
  preset fields, `do` actions, expectation forms, scopes and Java test buttons. Do not refactor, rename or alter
  what existing code does unless the developer explicitly asks for that fix. Prefer solving things in scripts and
  presets; extend only when a script genuinely needs it.

## Workflow

1. **Pin down the behaviour.** Restate it as one sentence ("when Hacan follows Politics, Hacan loses one strategy
   token"). If the request covers several behaviours, propose one script each. Ask only if the expected outcome is
   unclear; otherwise state your reading and continue.
2. **Read the feature's code.** Find the handler and service that implement it. Note which buttons a player
   presses, which state changes, and which messages are posted where.
3. **Choose or write a preset.** Reuse one from `src/main/resources/data/testbed/` when it fits. Otherwise add one
   beside it: fixed factions, specific card ids for anything checked, and `start` as late as possible (`action`,
   with `sc`, `units`, `leaders`, `combat`), so the script does not replay setup. Set `"fog"` when the feature is
   fog-only.
4. **Write the script** in `src/main/resources/data/testbed/scripts/<behaviour>.json` (shared) or
   `src/main/resources/data/testbed/local/scripts/` (only for the developer, ignored by git): a precondition check,
   the presses, then the checks. Rules below.
5. **Validate:** `mvn -o test -Dtest=TestBedDataTest`. It parses and validates every preset, script and test button
   file, shipped and local: seat names against fixed-faction presets, verbs, scopes, state paths, card ids, and
   Discord id limits. Fix every error. A one-off script the developer will attach with `file:` goes in `local/scripts/`
   so it is validated without being committed.
6. **Hand over** (format below).

## Rules for scripts

- **Never invent button ids.** Find each in the code: searching `src/main/java` for `"<Label>"` leads to
  `Buttons.<style>(<id>, "<Label>")`. Cite the file and line in your hand-over.
  - `player.factionButtonChecker()` prefixes `FFCC_<faction>_`. `press` may omit it. In `pressId`, include it when
    the faction check should apply (guard tests); without it the check is skipped.
  - Ids built from values (`"strategicAction_" + sc`, `"sc_follow_" + sc`) are written with concrete values.
  - If an id is built from something you cannot know in advance, use `press` with a stable id prefix, or say so.
- **Get the component into play through the preset.** The table "Set up any component" in
  `DEVELOPER_TESTBED.md` lists a preset field for each kind (cards, notes, objectives, laws, tokens, fragments,
  breakthroughs). If one is missing, add the field (`TestBedPreset.Seat`, `TestBedComponentService`,
  `TestBedPresetService` validation, a test) rather than working around it in steps.
- **Played components use placeholders.** Hand cards have buttons numbered at draw time
  (`ac_play_from_hand_<n>`, `so_score_hand_<n>`): write `ac_play_from_hand_{ac:<id>}` and give the card in the
  preset or a `do: hand` step first; validation fails otherwise. For color-based ids use `{<seat>.color}`
  (`resolvePNPlay_{hacan.color}_sftt`). Check the outcome with the played-component paths (`acIds` with
  `notContains`, `game.acDiscard`, `sosScored`, `pnsInPlay`, `leaders`, `exhaustedTechs`, ...). Use a preset with
  no random card draws (like `ac-2p`) so a random draw cannot take the card. "Playing hand cards" under "Write a
  script" in `DEVELOPER_TESTBED.md` lists each component type.
- **`press` by default.** It proves the bot offered the button to that seat. Use `pressId` only for buttons not on
  screen at that point, ability buttons, or deliberate wrong-seat guard checks.
- **State checks first.** Use the paths in `TestBedStateResolver` (`SEAT_FIELDS`, `GAME_FIELDS`,
  `tile.<position>.<field>`, `planet.<id>.<field>`, `stored:<key>`).
  If you need a value that has no path, add it to the list and the matching `switch`; `TestBedGameTest` fails if a
  listed path is not implemented. Many features keep their state in game stored values, which `stored:<key>` can
  read: find the key in the code.
- **Message checks second,** with short stable fragments taken from the code's message strings, never whole
  sentences, emoji or mentions. Names often render as emoji (strategy cards do), so match the fixed text
  around them. Use `count` for "exactly once", `matches` for a regex, `buttons` / `noButtons` for what a seat is
  offered next. A message check only sees messages since the last `press` or `do`; add `"since": "start"` to see
  the whole run. In fog scripts add
  `{ "expect": { "in": "main", "noFactionLeak": true, "since": "start" } }` after anything that could announce
  publicly.
- **One behaviour, small and deterministic.** A precondition check first; `stopOnFail: true` when later steps
  depend on earlier ones; no checks that depend on dice rolls or random draws.
- **Know what a script cannot see:** messages from before it started (including everything the preset did),
  modal contents, select menus, reactions and slash commands. Combat threads are visible through the
  `<seat>:combat` scope. Check the rest through state, or list it for the developer to verify by hand.
- **Timing:** `press` waits for its button and positive checks retry, both for up to `timeoutSeconds`
  (default 20), and finish as soon as the message lands. Absence checks (`notContains`, `noFactionLeak`,
  `noButtons`) keep watching until nothing new arrives for a second. A press fails if the handler threw or the
  bot refused the seat. Crons and timers need a `wait`.
- **Use ids, not labels, for buttons whose label carries state** (`End Turn (+1 ability)`, `Tactical Action (3)`).
- JSON cannot hold comments: put intent in `description` and `note` steps.

## Adding test buttons

When a developer wants a button in the panel for their feature (not a full test), add a **test button**:
- **Prefer JSON.** Personal buttons go in `src/main/resources/data/testbed/local/shortcuts/<feature>.json`
  (ignored by git); buttons the team should keep go in `src/main/resources/data/testbed/shortcuts/` (`shared.json` or
  `<feature>.json`). One file is one group in the panel. Each button is a `label` plus script `steps`; set
  `"fog": true|false` when it only makes sense in one kind of game.
- **Java only for logic** that steps cannot express: add `new JavaShortcut("<group>", "<id>", "<label>",
  (game, seat, event) -> "<status line>")` to `TestBedFeatureShortcuts.SHORTCUTS`. `seat` may be `null`; return a
  status line, never post messages yourself. No comments in the Java (AGENTS.md).
- Validate with `mvn -o test -Dtest=TestBedDataTest` (every file is its own case; ids must be unique per group,
  labels at most 80 characters). Tell the developer to run `/testbed reload` to pick the file up.

## Extending the framework

Only when a script genuinely needs it, and say so in the hand-over. Add, never change existing behaviour:
- **A new state path:** `TestBedStateResolver` (list + `switch`), the paths list in `DEVELOPER_TESTBED.md`.
- **A new `do` action, expectation form or scope:** `TestBedScript` (`ACTIONS`), `TestBedScriptService`
  (validation), `TestBedScriptRunner` (execution), a case in `TestBedDataTest`, and the reference tables in
  `DEVELOPER_TESTBED.md`.
- Then run the full build: `mvn -o clean verify -P ci-spotless` (spotless, spotbugs and every test).

## Hand-over format

End with a short message to the developer containing:
1. **What the script tests,** in one sentence, and the file path.
2. **How to run it:** "`/testbed run script:<name>` in a test-bed or new normal (or fog) game; it resets and
   applies its preset first" (`file:` for an attachment; `script:all` runs every shipped script).
3. **What a pass looks like:** the steps that must be ✅, and which message or state each one proves.
4. **Button ids used,** each with the file and line it came from.
5. **Not covered:** anything the script cannot check that needs a manual look.
6. **Validation result:** `TestBedDataTest` passed (or what is still failing), and that the script has not been
   run live yet.

If a run fails, ask the developer for the attached `.md` log. Its "visible" button list (``Label (`id`)``) and
the untruncated expected and actual values are usually enough to fix the script.
