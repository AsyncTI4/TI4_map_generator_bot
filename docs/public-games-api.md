# Completed game export

`GET /api/public/games/events?page=0&limit=100`

Returns a streamed ZIP attachment of completed competitive games with recorded events. The `since`
parameter has been removed; each exported game includes its full event history.
The single-game `/api/public/game/{gameName}/events` endpoint is unchanged.

| Parameter | Default | Behavior |
| --- | --- | --- |
| `page` | `0` | Zero-based page number. Negative values return HTTP 400. |
| `limit` | `100` | Games per page. Values above 500 are capped at 500; values below 1 return HTTP 400. |

Games are ordered by name in ascending lexicographic order. Filtering and pagination
use cached competitive eligibility and a database query for game names with recorded
events before loading saves or full event histories for the selected page.

Eligible games must have 3–8 real or eliminated players, a 10, 12, or 14 VP goal,
a winner, and at least three rounds. The standard competitive exclusions apply:
no homebrew, Fog of War, Galactic Events, or scenarios (including Alliance).
Both Thunder's Edge and Prophecy of Kings must be enabled, and Twilight's Fall
must be disabled.
Existing statistics consumers retain their six-player, 10 VP competitive defaults.
Games without recorded events are excluded before pagination; the selected game's
current event history is checked again before export to exclude undone histories.

Each ZIP contains:

- `pagination.json`: `{ "page": 0, "limit": 100, "totalGames": 250, "hasNext": true }`.
- `<gameName>.txt`: the game's stored save file in the bot's native save format.
- `<gameName>.json`: the complete array of recorded events for that game, in sequence order.

Increment `page` while `hasNext` is true, keeping `limit` constant. Pages beyond the
end contain only `pagination.json` with `hasNext: false`. The list reflects current
metadata on each request, so newly completed or reopened games can shift pages.

A game that cannot be loaded or exported is logged and omitted from its page;
`totalGames` counts eligible metadata entries, including failed exports. A game
that has reopened, ceased to qualify, or lost its valid events is also omitted after loading. These skips
do not pull replacement games from subsequent pages.

## Proposed incremental collection protocol

The current endpoint uses page numbers. For initial collection followed by daily
updates, a future revision should offer opaque cursors over a durable export-change
index. Each change would receive a monotonically increasing revision when a game
becomes eligible or its save, recorded events, or eligibility changes.

The first request would fix an upper revision for the collection. Responses would
include a `nextCursor` while that bounded collection has more games, and a
`syncCursor` after it is exhausted. A daily request using the saved `syncCursor`
would fetch changes after that revision, returning full saves and event histories
for changed games. Clients would replace their copy by game name and persist the
new sync cursor only after processing every page successfully. Games that become
ineligible or are deleted would need removal records.

The index and revision assignment should be persisted and tied to successful save
and event writes so restarts, delayed event writes, and late corrections cannot
silently lose changes. A completion-date cutoff alone cannot catch those corrections.
This cursor protocol is a design proposal and is not implemented by this endpoint.
