# Component rules

Rules for game components the bot loads from JSON data. Each section applies **only** to the
component it names. Don't apply a section's rules to other components, move other Java enums to
JSON, or add these fields to other models unless the developer asks for it.

To cover another component later, add a section for it here **and** add the component, with
its main files, to the list under the `COMPONENT_RULES.md` bullet in [AGENTS.md](AGENTS.md).
Agents only open this file for components on that list.

## Border anomalies

Border anomaly types are data in
[border_anomalies.json](src/main/resources/data/border_anomalies/border_anomalies.json), loaded
into `BorderAnomalyModel` by `Mapper`.

- **State what the bot enforces.** Every entry has an `automation` (`AutomationStatus`: `FULL`,
  `PARTIAL`, `MANUAL`, `NO_RULES`) and `automationNotes`. Keep them accurate when you change a
  border's behaviour.
- **Rules are declarative data, grouped by subsystem** (`rules.adjacency`, later
  `rules.movement`). Add a Java hook only for a rule data can't express.
- **Java names a specific border type only through `BorderAnomalyIds`.** `BorderAnomalyModelTest`
  checks every constant exists. Compare ids with `equals` or `BorderAnomalyHolder.isType`,
  never `==`.
- **Ids are lower case.** Legacy spellings resolve through `Mapper.resolveBorderAnomaly` at the
  boundaries (commands, game load, map import). Hot paths use `Mapper.getBorderAnomaly`.
- **Adding or changing a type means updating `BorderAnomalyModelTest`.** `Mapper` only logs JSON
  import errors, and Jackson ignores misspelt keys, so the test asserts the exact ids and rule values.

## Cards: automation line (30k Content Lab set)

Action cards, explores, relics and secret objectives may carry an optional `automation`
(`AutomationStatus`) and `automationNotes`. When set, the card embed shows a
`-# Bot: <status> — <notes>` line so players know whether the bot enforces the card.

- **Optional in general, required for source `thirtyk`.** `ThirtyKCardsTest` fails if a 30k or JMSC
  card has no `automation`, or is `PARTIAL`/`MANUAL` without notes. Other sets may add it card by card.
- **Keep it accurate.** When you automate more of a card, raise its status and rewrite the notes to
  say what players still do themselves.
- **Ids have no underscores** for explores and relics (button ids are split on `_`). Copies of a card
  end in a number (`conflagration30k1`); `ThirtyKCards.baseId` strips it.
- **Card logic lives in `ti4.helpers.thirtyk`** (`ThirtyKActionCards`, `ThirtyKExplores`,
  `ThirtyKRelics`, `ThirtyKSecrets`). Button handlers go in a `thirtyk` subfolder of the card
  type's handler folder: `handlers/actioncards/thirtyk`, `handlers/explore/thirtyk` (attachments
  too), `handlers/relics/thirtyk`, `handlers/objective/thirtyk`. Core files only call into these.
- **Every hook called from core starts with `if (!ThirtyKCards.inUse(game)) return …;`.**
  - `inUse` is true once any of the game's action card, secret objective, relic or explore decks contains
    a 30k card. It stays true for the rest of the game (stored value `thirtyKInUse`), so cards still in play
    keep working after a deck switch.
  - Games that never use such a deck do no 30k work and get nothing stored.
  - A 30k card added by hand to such a game shows its text but is not automated.
- **"More than 5 hexes away"** is adjacency distance on the map: use `ThirtyKDistance`.
- **"The Fracture"** is any tile where `Tile.isFracture()` is true.
