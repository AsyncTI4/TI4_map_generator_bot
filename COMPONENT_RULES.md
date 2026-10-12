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
