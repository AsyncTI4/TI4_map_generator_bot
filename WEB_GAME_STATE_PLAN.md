# Web Game State Implementation Plan

**Goal:** End to end: expose a slim, non-duplicated `gameState` payload (phase, active player, agenda voting, active system/combat) in the existing web data, stream realtime **delta updates** (deep-mergeable partial objects) over the existing STOMP WebSocket whenever something changes — undo-safe, minimal bytes on the wire, no game-file bloat — and render it in `ti4_web_new` as a compact, non-obtrusive block in the existing left sidebar with a live turn timer.

**Architecture:** Backend (this repo): no new files beyond the two already in the working tree — and one of those gets folded away. `WebGameState` absorbs phase inference (delete `PhaseInferenceService`). Combat inference lives in `StartCombatService`, which already owns those stored values. The existing `WebSocketNotifier` becomes the single service/contract for all web push: it decides *whether* anything changed, computes the merge-patch delta, and publishes it. Frontend (`../ti4_web_new`): the existing single STOMP client gains a second subscription; patches are applied into a React Query cache entry seeded by the new `/game-state` endpoint; one new panel component renders inside the existing `LeftSidebar`.

**Tech Stack:** Backend: Java 21+/Maven, Spring Boot (web + websocket/STOMP already configured), Jackson `JsonNode` tree diff (no new dependency), Caffeine, Lombok. Frontend: React 19 + TypeScript/Vite, TanStack React Query v5, @stomp/stompjs v7, Mantine v7.

**Build/verify:** Backend: `mvn -q compile`, `mvn -q spotless:apply` (mvn is at `/opt/homebrew/bin/mvn`). Frontend (in `../ti4_web_new`): `yarn lint` / `npx tsc -p tsconfig.app.json --noEmit`, `yarn dev` against the local bot (`ws://localhost:8081/ws` option is already commented in `src/config.ts`).

---

## Status of work already done (in working tree)

| Piece | File | State |
|---|---|---|
| Canonical phase inference | `src/main/java/ti4/website/PhaseInferenceService.java` | Done — **to be folded into `WebGameState` and deleted** (Task A1) |
| `WebGameState` DTO | `src/main/java/ti4/website/model/WebGameState.java` | Done, needs enrichment + combat extraction |
| `AgendaHelper.getCurrentAgendaId` (+ dedup in `RehashedDebatesAcd2ButtonHandler`) | `src/main/java/ti4/helpers/AgendaHelper.java` | Done |
| Start-vote-count snapshot at agenda reveal (`agendaStartVoteCounts` storedValue) | `AgendaHelper.java:2571` (call), `:2831-2861` (impl) | Done; missing lifecycle cleanup |
| `AgendaSummaryHelper.getCurrentOutcomeVoteCounts` (+ refactor of `countNumericVotes`) | `src/main/java/ti4/helpers/AgendaSummaryHelper.java` | Done |
| Wire `gameState` into web data | `src/main/java/ti4/spring/api/webdata/GameWebDataService.java:113` | Done (`versionSchema` stays 7 — purely additive) |

## Ground truth discovered (do not re-derive)

- **All `phaseOfGame` values ever set** (verified by grep over `setPhaseOfGame(`): `action`, `agenda`, `agendaEnd`, `agendaVoting`, `agendawaiting`, `miltydraft`, `playerSetup`, `statusHomework`, `statusScoring`, `strategy`. The inference switch covers all 10 (plus legacy `status`/`voting` aliases).
- **Web data pipeline:** `MapGenerator.sendToWebsite()` (`src/main/java/ti4/image/MapGenerator.java:424-434`) → `AsyncTi4WebsiteHelper.putPlayerData()` (`src/main/java/ti4/website/AsyncTi4WebsiteHelper.java:28-59`) → `GameWebDataService.put()` (Caffeine cache, 30-min TTL) → `WebSocketNotifier.notifyGameRefresh()` sends the string `"refresh"` to `/topic/game/{gameId}`. Guards: skipped when `TESTING` env set, `displayTypeBasic != DisplayType.all`, or `isFoWPrivate`.
- **REST:** `GET /api/public/game/{gameName}/web-data` (`GameWebDataController.java:20`), returns 4xx for `game.isFowMode()` (`:26`). `/api/public/**` and `/ws/**` are `permitAll` in `SecurityConfiguration`.
- **WebSocket:** STOMP endpoint `/ws`, simple broker on `/topic` (`src/main/java/ti4/spring/websocket/WebSocketConfig.java`); `WebSocketNotifier` (`src/main/java/ti4/spring/websocket/WebSocketNotifier.java`) is the existing publish contract.
- **Save/undo:** every command/button ends in `GameManager.save(game, reason)` (`src/main/java/ti4/game/persistence/GameManager.java:99`), which writes the full game `.txt` (storedValueMap included → **anything in storedValue is undo-safe automatically**) and creates an undo copy. `GameUndoService.undo` (`src/main/java/ti4/game/persistence/GameUndoService.java:96-97`) replaces the file and reloads via `GameLoadService.load` — it does **not** go through `GameManager.save`, so it needs its own publish hook.
- **Combat stored values are owned by `StartCombatService`** (`src/main/java/ti4/service/combat/StartCombatService.java:276-281`): `factionsInCombat` = `faction1_faction2`; `combatRoundTracker<faction><tilePosition><unitHolderName>` = round number. Cleared by `game.updateActivePlayer` (`Game.java:1432`) and `StartTurnService` (removes `combatRoundTracker*` keys). Inference of "current combat" belongs there, next to the writes.
- **Already exposed elsewhere in webData — do NOT duplicate in `gameState`:** round (`gameRound`), strategy cards picked/played/exhausted/TGs (`strategyCards`), per-player passed/eliminated/speaker/CCs/VPs (`playerData` via `WebPlayerArea`), objectives, laws, deck sizes (`cardPool`), score breakdowns.
- **Genuinely missing from webData (the enrichment gaps):** whose turn it is (`game.getActivePlayer()`), when the turn started (`game.getLastActivePlayerChange()` → enables turn timers), game finished/winner (`game.isHasEnded()` / `game.getWinner()`), combat round number.
- **Agenda resolution end:** `game.setPhaseOfGame("agendaEnd")` at `AgendaHelper.java:592`. Whens/afters flags set in `AgendaWhensAftersHelper.java:498`/`:703`, cleared at reveal (`:61-62`).
- **Reuse, don't reimplement:** `game.getActivePlayer()`, `game.getWinner()`, `game.getLastActivePlayerChange()`, `game.getPlayerFromColorOrFaction()`, `AgendaHelper.getVoteTotal`/`getVotingOrder` (already behind `getVoteCountByColor`), `JsonMapperManager.basic()`.

## Sister repo ground truth (`/Users/jstrong/webapps/asyncti/ti4_web_new`)

- **Stack:** React 19 (React Compiler on), TypeScript strict, Vite, Mantine v7 (dark themes), TanStack React Query v5 (server state), Zustand (`src/utils/appStore.ts`, UI settings incl. `leftPanelCollapsed`), `@stomp/stompjs` v7. Path alias `@/` → `src/`.
- **Data fetch:** `usePlayerData` (`src/hooks/usePlayerData.ts:17-25`) does `GET ${config.api.gameDataUrl}/${gameId}/web-data` into React Query key `["playerData", gameId]`, typed as `PlayerDataResponse` (`src/entities/data/types.ts:306-328`). `config.api.gameDataUrl` = `/bot/api/public/game` in dev (Vite proxy → `bot.asyncti4.com`), `https://bot.asyncti4.com/api/public/game` in prod (`src/config.ts`).
- **WebSocket today:** `useGameSocket` (`src/hooks/useGameSocket.ts`) creates ONE STOMP client per game view (`brokerURL = config.api.websocketUrl`), subscribes only to `/topic/game/{gameId}`, and calls `onRefresh` when `msg.body === "refresh"` (`:36-38`). `usePlayerDataSocket` (`usePlayerData.ts:27-57`) wires that to `refetch()` + invalidates `["playerHand", gameId]`, skipping the first message via `hasConnectedBefore`. **Important: any new subscription must ride this existing client — a second `useGameSocket` call would open a second socket.**
- **Layout:** route `/game/:mapid` → `NewMapUI.tsx` → Mantine `AppShell` + `Tabs` (map/objectives/general/players). The map tab renders `MapView` (`src/domains/map/components/MapView.tsx`), which mounts `LeftSidebar` (`src/domains/game-shell/components/LeftSidebar.tsx`) as a collapsible overlay (`classes.leftSidebarOverlay`, toggled by `settings.leftPanelCollapsed`). LeftSidebar already shows game name, `Round {gameRound}`, objectives, point totals, laws — **it is the natural non-obtrusive home for the game-state block** (already collapsible, already "table info").
- **Reusable pieces:** `Panel` primitive (`src/shared/ui/primitives/Panel.tsx`, variants + accent colors), `Chip` (`src/shared/ui/primitives/Chip.tsx`), `SmoothPopover` (`src/shared/ui/SmoothPopover.tsx`) for detail popovers, `classes.sectionHeading` for headings, `LawDetailsCard`/`CompactLaw` as the pattern for agenda display.
- **Static lookups on the client:** `src/entities/data/agendas.ts` (auto-generated; `alias` → name/type/target/text) — agenda **id from the wire resolves to full card text client-side, so the backend payload stays id-only as designed**. Colors: `useFactionColors` (`src/hooks/useFactionColors.ts`) gives faction → color data; `playerData[].color` gives the reverse (color → faction/displayName) needed since `gameState` speaks colors.
- **No timer component exists** — elapsed-turn display needs a small `useEffect` interval (1 s tick) off `turnStartedAt`.

---

## Final payload shape

```json
{
  "gameState": {
    "phase": "agenda.voting",
    "activePlayer": "blue",
    "turnStartedAt": 1751500000000,
    "winner": null,
    "agenda": {
      "id": "incentive_program",
      "startVoteCounts": { "red": 14, "blue": 9, "green": 0 },
      "outcomeVoteCounts": { "for": 12, "against": 8 }
    },
    "activeSystem": "305",
    "activeCombat": {
      "system": "305",
      "unitHolder": "space",
      "round": 2,
      "participantColors": ["blue", "red"]
    }
  }
}
```

Design decisions (locked in):

- **Players are colors only** everywhere (`activePlayer`, `winner`, `participantColors`).
- **`agenda.voter` is removed** in favor of a top-level `activePlayer`. During `agenda.voting` the active player *is* the voter (voting sets it via `game.updateActivePlayer(nextInLine)`), and `activePlayer` is also what's needed in `strategy` (who's picking) and `action` (whose turn). One field, three phases, no duplication.
- **`turnStartedAt`** is epoch millis from `game.getLastActivePlayerChange()`; null when unset.
- **New phase `finished`**: when `game.isHasEnded()` or `game.hasWinner()`, overrides everything else. `winner` is the winner's color (null if ended without one).
- **`activeSystem` stays separate from combat** (tactical action system vs. wherever combat is happening).
- **`versionSchema` stays 7.** Additive field; existing consumers unaffected.

---

# Part A — Finish the gameState payload

### Task A1: Fold phase inference into `WebGameState`; delete `PhaseInferenceService`

The inference and the enum are only consumed by `WebGameState` — a separate file buys nothing.

**Files:**
- Modify: `src/main/java/ti4/website/model/WebGameState.java`
- Delete: `src/main/java/ti4/website/PhaseInferenceService.java`

**Steps:**

1. Move `CanonicalPhase` (with its `@JsonProperty` values) and `infer(Game)` + `inferAgendaWaitingPhase(Game)` into `WebGameState` as a nested enum and private static methods. Keep the javadoc explaining the raw-string → canonical mapping.
2. Update the two imports in `WebGameState`; grep for any other `PhaseInferenceService` reference (`GameWebDataService` doesn't reference it directly).
3. `git rm src/main/java/ti4/website/PhaseInferenceService.java`
4. `mvn -q compile`, commit: `refactor: fold phase inference into WebGameState`

### Task A2: Snapshot lifecycle cleanup (no stale data, no lingering storedValue)

The start-vote snapshot is written at reveal (`AgendaHelper.java:2571`) but never removed. If it lingers after resolution, (a) the game file carries a dead line every round, and (b) if `currentAgendaInfo` also lingers into `agendaEnd`, the web payload would show last agenda's counts as current.

**Files:**
- Modify: `src/main/java/ti4/helpers/AgendaHelper.java` (around line 592, where `setPhaseOfGame("agendaEnd")` happens)

**Steps:**

1. Read the method containing `AgendaHelper.java:592` to see the resolution flow (what else is cleared there — e.g. whether `currentAgendaInfo` is reset).
2. Add `game.removeStoredValue(AGENDA_START_VOTE_COUNTS);` next to `game.setPhaseOfGame("agendaEnd")`. Re-reveal already overwrites the snapshot, so this is belt-and-braces for file hygiene + correctness between agendas.
3. `mvn -q compile`, commit: `feat: clear agenda start-vote snapshot when agenda resolves`

### Task A3: Move combat inference into `StartCombatService`, enrich with round + unit holder

`StartCombatService` already writes `factionsInCombat` and the `combatRoundTracker` keys (`:276-281`) — reading them back belongs beside the writes, not in a web DTO. While parsing the key we get two enrichments free: combat **round** (the tracker's value) and **unit holder** (`space` or a planet name → space vs. ground combat).

**Files:**
- Modify: `src/main/java/ti4/service/combat/StartCombatService.java` (add record + method)
- Modify: `src/main/java/ti4/website/model/WebGameState.java` (delete `inferCombatSystem`, consume the service)

**Step 1: Add to `StartCombatService`:**

```java
public record CurrentCombat(String tilePosition, String unitHolderName, int round, List<String> factions) {}

public static CurrentCombat getCurrentCombat(Game game) {
    String factionsInCombat = game.getStoredValue("factionsInCombat");
    if (factionsInCombat == null || factionsInCombat.isBlank()) {
        return null;
    }
    List<String> factions = Arrays.stream(factionsInCombat.split("_"))
            .filter(f -> !f.isBlank())
            .toList();

    for (var entry : game.getStoredValueMap().entrySet()) {
        if (!entry.getKey().startsWith("combatRoundTracker")) continue;
        String suffix = entry.getKey().substring("combatRoundTracker".length());
        for (String faction : factions) {
            if (!suffix.startsWith(faction)) continue;
            String location = suffix.substring(faction.length());
            // Longest matching tile position wins so "30" doesn't shadow "305".
            String tilePosition = game.getTileMap().keySet().stream()
                    .filter(location::startsWith)
                    .max(Comparator.comparingInt(String::length))
                    .orElse(null);
            if (tilePosition == null) continue;
            String unitHolder = location.substring(tilePosition.length());
            int round = NumberUtils.toInt(entry.getValue(), 1);
            return new CurrentCombat(tilePosition, unitHolder.isBlank() ? null : unitHolder, round, factions);
        }
    }
    // Combat flagged but no tracker key yet (combat just declared).
    return new CurrentCombat(null, null, 1, factions);
}
```

If the existing key-construction lines at `:279-281` don't already use a constant, extract `"combatRoundTracker"` into one and use it in both places.

**Step 2: Rewrite `WebCombat`** (delete `inferCombatSystem` from `WebGameState.java`):

```java
@Data
public static class WebCombat {
    private String system;
    private String unitHolder;
    private Integer round;
    private String[] participantColors;

    static WebCombat fromGame(Game game) {
        StartCombatService.CurrentCombat current = StartCombatService.getCurrentCombat(game);
        if (current == null) return null;
        WebCombat combat = new WebCombat();
        combat.system = current.tilePosition();
        combat.unitHolder = current.unitHolderName();
        combat.round = current.round();
        combat.participantColors = current.factions().stream()
                .map(faction -> colorForFaction(game, faction))
                .toArray(String[]::new);
        return combat;
    }
}
```

**Step 3:** `mvn -q compile`, commit: `refactor: combat state inference lives in StartCombatService; expose round + unit holder`

### Task A4: Enrich `WebGameState` — activePlayer, turnStartedAt, finished/winner; drop agenda.voter

**Files:**
- Modify: `src/main/java/ti4/website/model/WebGameState.java`

**Steps:**

1. In the (now-nested) phase inference, after the null check:

```java
if (game.isHasEnded() || game.hasWinner()) {
    return CanonicalPhase.FINISHED;
}
```

and add to the enum: `@JsonProperty("finished") FINISHED`.

2. New fields + assembly (all existing getters, nothing reimplemented):

```java
private CanonicalPhase phase;
private String activePlayer;
private Long turnStartedAt;
private String winner;
private WebAgenda agenda;
private String activeSystem;
private WebCombat activeCombat;

public static WebGameState fromGame(Game game) {
    WebGameState state = new WebGameState();
    state.phase = inferPhase(game);
    state.activePlayer = colorForPlayer(game.getActivePlayer());
    state.turnStartedAt = game.getLastActivePlayerChange() == null
            ? null
            : game.getLastActivePlayerChange().getTime();
    state.winner = game.getWinner().map(WebGameState::colorForPlayer).orElse(null);
    state.agenda = WebAgenda.fromGame(game);
    state.activeSystem = blankToNull(game.getCurrentActiveSystem());
    state.activeCombat = WebCombat.fromGame(game);
    return state;
}
```

3. In `WebAgenda`: delete the `voter` field and the `phase` parameter (its only use).
4. `mvn -q compile`, commit: `feat: expose active player, turn start, winner in web game state; drop redundant agenda voter`

### Task A5: Verification pass for Part A

1. `mvn -q spotless:apply && mvn -q compile` → BUILD SUCCESS; commit any reformats.

---

# Part B — Lightweight fresh-read endpoint

**Why:** the cached full web-data (30-min TTL, rebuilt only after map generation) will routinely carry a stale `gameState` relative to the realtime stream — and with deltas, clients also need a resync source after a missed message. One cheap endpoint computes `WebGameState` fresh on demand; clients use it on page load, reconnect, and sequence gaps.

### Task B1: `GET /api/public/game/{gameName}/game-state`

**Files:**
- Modify: `src/main/java/ti4/spring/api/webdata/GameWebDataController.java`

**Steps:**

1. Mirror the existing `web-data` handler exactly (same `RequestContext` game resolution, same FOW guard at `:26`):

```java
@GetMapping("/game-state")
public ResponseEntity<WebGameState> getGameState() {
    Game game = RequestContext.getGame();
    if (game.isFowMode()) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
    }
    return ResponseEntity.ok(WebGameState.fromGame(game));
}
```

(The `GameLockAndRequestContextInterceptor` already read-locks and injects the game for `/api/public/game/{gameName}/**`.)

2. `mvn -q compile`, commit: `feat: add lightweight fresh game-state endpoint`

---

# Part C — Realtime delta updates over WebSocket

## Design

**How hard are deltas? Easy here** — precisely because the publisher must already keep the last-published state to answer "did anything change?". Diffing two Jackson `JsonNode` trees into an RFC 7386-style *merge patch* (the exact "send only what changed, client deep-merges, `null` means field cleared" contract) is ~35 lines with no new dependency. The costs of deltas are not in producing them; they're in the failure modes — and each has a cheap answer:

| Delta failure mode | Mitigation |
|---|---|
| Client misses a message → merge produces wrong state | `seq` per game; client detects `seq != last + 1` → one `GET /game-state` resync |
| Bot restart → server baseline lost | in-memory cache empty → first publish is flagged `full: true`; client replaces instead of merging |
| Arrays can't be deep-merged | merge-patch replaces arrays wholesale (only `participantColors`, ~30 bytes — fine) |
| Field cleared (combat ends) must reach the client | diff emits explicit `null` for keys that disappeared; deep merge treats `null` as delete |

**Wire protocol** — new sub-topic, one envelope for both cases:

```
destination: /topic/game/{gameId}/state
payload: {
  "type": "gameState",
  "seq": 42,              // per-game, in-memory, monotonic; resets on bot restart
  "timestamp": 1751500000000,
  "full": false,          // true => patch is the complete gameState, replace don't merge
  "patch": { "phase": "agenda.voting", "agenda": { "outcomeVoteCounts": { "for": 15 } } }
}
```

Typical messages are tens of bytes (a vote changes one nested integer; a turn change touches `activePlayer` + `turnStartedAt`). Worst case (`full: true` after restart) is the ~1 KB snapshot, once per game per restart.

**Single service/contract:** everything — building the snapshot, deciding *whether* to publish, computing the patch, sequencing, publishing — lives in the existing **`WebSocketNotifier`** (`src/main/java/ti4/spring/websocket/WebSocketNotifier.java`). No new class. It keeps its existing `notifyGameRefresh` (the `"refresh"` signal that the heavy payload changed) and gains `notifyGameStateChanged(Game)`.

**Change detection:** in-memory Caffeine cache `gameId → last published JsonNode`. Serialize fresh `WebGameState` to a tree (`valueToTree` keeps nulls as `NullNode` — verify `JsonMapperManager.basic()` isn't configured `NON_NULL`; if it is, use a locally-built mapper in the notifier), diff against cached; empty diff → publish nothing. This coalesces the many saves that don't move public state.

**Undo compatibility & file bloat (unchanged reasoning):** the baseline lives only in memory, nothing is persisted for the stream — no journal, no seq in save files, so game files don't grow at all and undo needs no special code: the post-undo state simply diffs against the last-published state and the difference streams like any other change.

**Hook points (both required):**

1. `GameManager.save(game, reason)` — `GameManager.java:99`, right after `GameSaveService.save` succeeds. Every command/button funnels through here (including web-API mutations via the interceptor), so votes, phase flips, turn changes, combat starts stream within the same interaction — no waiting for a map render or the 30-min cache.
2. `GameUndoService` — after the reload at `GameUndoService.java:97` (and the `:202` load path if user-reachable), since undo bypasses `GameManager.save`.

**Guards:** skip when `game.isFowMode()` (public topics must never carry fog-of-war state) and when `System.getenv("TESTING") != null` (matches `MapGenerator.sendToWebsite`). All exceptions swallowed + logged — websocket publishing must never fail a game save (same posture as the existing `notifyGameRefreshWebsocket` wrapper).

**Client contract** (implemented concretely in Part D):

- On load: `GET .../web-data` (full) or `.../game-state` (state only); remember nothing about seq yet.
- Subscribe `/topic/game/{gameId}` → on `"refresh"`, refetch full web-data (heavy payload changed).
- Subscribe `/topic/game/{gameId}/state` → if `full` or first message: replace local `gameState`, store `seq`. Else if `seq == last + 1`: deep-merge `patch` (recursive; `null` deletes the key; arrays replace). Else: `GET .../game-state`, replace, store `seq`.
- On WebSocket reconnect: refetch `.../game-state` once, then resume.

## Tasks

### Task C1: Extend `WebSocketNotifier` with delta publishing

**Files:**
- Modify: `src/main/java/ti4/spring/websocket/WebSocketNotifier.java`

**Step 1: Implement** (full class — it currently only holds `notifyGameRefresh`):

```java
package ti4.spring.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import ti4.game.Game;
import ti4.json.JsonMapperManager;
import ti4.logging.BotLogger;
import ti4.spring.context.SpringContext;
import ti4.website.model.WebGameState;

/**
 * Single contract for pushing game updates to web clients.
 *
 * <p>{@code notifyGameRefresh} signals that the heavy web-data payload changed
 * (map render pipeline). {@code notifyGameStateChanged} owns the lightweight
 * realtime channel: it rebuilds the small {@link WebGameState} snapshot,
 * decides whether anything actually changed against an in-memory baseline,
 * and publishes only the changed fields as an RFC 7386-style merge patch
 * (client deep-merges; {@code null} deletes a key; arrays replace wholesale).
 * Nothing is persisted, so undo needs no special handling: the post-undo
 * snapshot diffs against the last published one like any other change.</p>
 */
@RequiredArgsConstructor
@Component
public class WebSocketNotifier {
    private final SimpMessagingTemplate messagingTemplate;

    private final Cache<String, JsonNode> lastPublishedState = Caffeine.newBuilder()
            .maximumSize(500)
            .expireAfterAccess(Duration.ofHours(6))
            .build();
    private final Map<String, AtomicLong> stateSequences = new ConcurrentHashMap<>();

    public void notifyGameRefresh(String gameId) {
        String destination = "/topic/game/" + gameId;
        messagingTemplate.convertAndSend(destination, "refresh");
    }

    /** Static entry point for non-Spring callers (GameManager, GameUndoService). Never throws. */
    public static void notifyGameStateChange(Game game) {
        try {
            SpringContext.getBean(WebSocketNotifier.class).notifyGameStateChanged(game);
        } catch (Exception ignored) {
            // Spring context may not be up during warmup; state streams on the next save.
        }
    }

    public void notifyGameStateChanged(Game game) {
        try {
            if (game == null || game.isFowMode() || System.getenv("TESTING") != null) return;

            String gameId = game.getName();
            JsonNode current = JsonMapperManager.basic().valueToTree(WebGameState.fromGame(game));
            JsonNode previous = lastPublishedState.getIfPresent(gameId);

            boolean full = previous == null;
            JsonNode patch = full ? current : diff(previous, current);
            if (patch == null) return; // nothing changed

            lastPublishedState.put(gameId, current);
            long seq = stateSequences.computeIfAbsent(gameId, k -> new AtomicLong()).incrementAndGet();
            messagingTemplate.convertAndSend(
                    "/topic/game/" + gameId + "/state",
                    new GameStateMessage("gameState", seq, System.currentTimeMillis(), full, patch));
        } catch (Exception e) {
            // Never let websocket publishing break a game save.
            BotLogger.error("Failed to publish game state update", e);
        }
    }

    /**
     * Merge patch of {@code after} relative to {@code before}: only changed keys,
     * {@code null} for removed/nulled keys, arrays and scalars replaced wholesale.
     * Returns null when the trees are identical.
     */
    private static JsonNode diff(JsonNode before, JsonNode after) {
        if (before.equals(after)) return null;
        if (!before.isObject() || !after.isObject()) return after;

        ObjectNode patch = JsonMapperManager.basic().createObjectNode();
        Set<String> fieldNames = new HashSet<>();
        before.fieldNames().forEachRemaining(fieldNames::add);
        after.fieldNames().forEachRemaining(fieldNames::add);
        for (String field : fieldNames) {
            JsonNode beforeValue = before.path(field);
            JsonNode afterValue = after.path(field);
            if (afterValue.isMissingNode()) {
                patch.putNull(field); // key disappeared -> delete on client
            } else {
                JsonNode childPatch = diff(beforeValue, afterValue);
                if (childPatch != null) patch.set(field, childPatch);
            }
        }
        return patch.isEmpty() ? null : patch;
    }

    public record GameStateMessage(String type, long seq, long timestamp, boolean full, JsonNode patch) {}
}
```

Implementation notes:
- Verify `JsonMapperManager.basic().valueToTree(...)` keeps null DTO fields as `NullNode` (default Jackson does; only a `NON_NULL` serialization inclusion would drop them). If dropped, build a private `JsonMapper` in this class without that inclusion — null transitions (`activeCombat` ending) must reach the wire.
- `beforeValue.isMissingNode()` (field newly appeared) falls through to `diff(missing, value)` → `!before.isObject()`… `MissingNode.isObject()` is false → returns `after` → whole new value emitted. Correct.
- Jackson version: repo uses `tools.jackson` (Jackson 3) in some places and `com.fasterxml` in others (see `AgendaHelper` imports) — match whatever `JsonMapperManager.basic()` returns.

**Step 2:** `mvn -q compile`, commit: `feat: WebSocketNotifier streams deduplicated game-state merge patches`

### Task C2: Hook into save and undo

**Files:**
- Modify: `src/main/java/ti4/game/persistence/GameManager.java` (`save`, line 99)
- Modify: `src/main/java/ti4/game/persistence/GameUndoService.java` (after reloads at `:97` and, if user-reachable, `:202`)

**Steps:**

1. In `GameManager.save`, right after the `throw`-guard on `GameSaveService.save(game, reason)`:

```java
WebSocketNotifier.notifyGameStateChange(game);
```

2. In `GameUndoService`, after `Game loadedGame = GameLoadService.load(gameName);` succeeds:

```java
WebSocketNotifier.notifyGameStateChange(loadedGame);
```

3. Import direction check: `ti4.game.persistence` → `ti4.spring.websocket` via `SpringContext.getBean` is the established pattern (`AsyncTi4WebsiteHelper.java:61-66` already does exactly this from non-Spring code).
4. `mvn -q compile`, commit: `feat: stream game state deltas on every save and after undo`

### Task C3: End-to-end smoke test (manual)

1. Run the bot locally with a test game.
2. Connect a STOMP client (browser console with @stomp/stompjs) to `/ws`, subscribe to `/topic/game/{gameId}/state`.
3. First action after boot → expect `full: true` message with complete gameState.
4. Pass a turn → expect a small patch touching only `activePlayer`/`turnStartedAt`, `seq` incremented by 1.
5. Press an informational button that doesn't change public state → expect **no** message (dedup).
6. Vote on an agenda outcome → expect patch like `{ "agenda": { "outcomeVoteCounts": { "for": 15 } } }`.
7. Start then finish a combat → expect `activeCombat: {...}` patch, then `activeCombat: null`.
8. Run `/game undo` → expect a patch carrying the reverted fields.
9. `GET /api/public/game/{gameId}/game-state` after each step and confirm deep-merging the patches reproduces it exactly.

### Task C4: Final verification

1. `mvn -q spotless:apply && mvn -q compile` → SUCCESS.
2. Update this file's status table.
3. Commit.

---

# Part D — Web UI (`../ti4_web_new`): live game-state side panel

## Design

**Placement:** a compact "Game State" block at the **top of the existing `LeftSidebar`** (`src/domains/game-shell/components/LeftSidebar.tsx`), right under the game name / round lines. This is deliberately not a new drawer or floating window: the left sidebar is already the "table info" surface (round, objectives, laws), already collapsible via `settings.leftPanelCollapsed`, and already overlays the map non-obtrusively. The block is small (3 stacked rows max) and sections render only when relevant — outside agenda phase there is no agenda row; outside combat no combat row.

**Sketch** (Mantine `Panel`/`Chip` primitives, existing `sectionHeading` class):

```
┌──────────────────────────────┐
│ AGENDA · VOTING              │  phase badge (accent color per phase group)
│ ● Blue is voting     · 12:30 │  active player chip (player color) + live timer
├──────────────────────────────┤
│ 🗳 Incentive Program          │  agenda name via agendas.ts alias lookup, popover → full text
│ For 12 · Against 8           │  outcomeVoteCounts
│ Blue has 9 votes to cast     │  startVoteCounts[activePlayer]
├──────────────────────────────┤
│ ⚔ Combat — 305 (space) · R2  │  system + unitHolder + round
│ Blue vs Red                  │  participantColors as colored chips
└──────────────────────────────┘
```

When `phase === "finished"`: replace everything with a winner banner (`winner` color → faction via playerData).

**Data flow (one socket, one query):**

```
useGameSocket (existing single STOMP client)
  ├─ /topic/game/{id}         → "refresh" → refetch ["playerData"] (unchanged)
  └─ /topic/game/{id}/state   → GameStateMessage → applyGameStatePatch()
                                   │
queryClient cache ["gameState", gameId]  ← seeded by GET .../game-state
                                   │
GameStatePanel ← useQuery(["gameState", gameId])
```

Patches land in the React Query cache via `queryClient.setQueryData` — no new store, no context change; the panel is a plain query consumer and re-renders only when the cached object actually changes.

**Merge/sequence rules (mirrors Part C's wire contract):**
- `full: true` or no cached data → `setQueryData(patch)` wholesale, store `seq`.
- `seq === lastSeq + 1` → `setQueryData(deepMergePatch(cached, patch))`, store `seq`.
- anything else (gap, seq reset from bot restart while we held state) → `invalidateQueries(["gameState", gameId])` → refetch from `/game-state`, reset `lastSeq` from scratch on next message.
- `deepMergePatch`: recursive on plain objects; `null` deletes the key; arrays and scalars replace. ~15 lines.
- On socket reconnect (existing `reconnect()` flow): invalidate `["gameState", gameId]` once.

## Tasks

### Task D1: Types

**Files:**
- Modify: `src/entities/data/types.ts` (existing types file)

Add (and extend `PlayerDataResponse` with `gameState?: GameState` since the full web-data now carries it too):

```typescript
export type GamePhase =
  | "unknown"
  | "setup.draft"
  | "setup.players"
  | "strategy"
  | "action"
  | "status.scoring"
  | "status.homework"
  | "agenda.readyToFlip"
  | "agenda.whens"
  | "agenda.afters"
  | "agenda.voting"
  | "agenda.resolving"
  | "finished";

export type GameStateAgenda = {
  id: string;
  startVoteCounts: Record<string, number>;   // player color -> votes available at reveal
  outcomeVoteCounts: Record<string, number>; // outcome -> votes cast
};

export type GameStateCombat = {
  system: string | null;
  unitHolder: string | null; // "space" or planet name
  round: number | null;
  participantColors: string[];
};

export type GameState = {
  phase: GamePhase;
  activePlayer: string | null;   // player color
  turnStartedAt: number | null;  // epoch ms
  winner: string | null;         // player color
  agenda: GameStateAgenda | null;
  activeSystem: string | null;
  activeCombat: GameStateCombat | null;
};

export type GameStateMessage = {
  type: "gameState";
  seq: number;
  timestamp: number;
  full: boolean;
  patch: Partial<GameState> | GameState;
};
```

### Task D2: Second subscription on the existing socket

**Files:**
- Modify: `src/hooks/useGameSocket.ts`

Add an optional third argument; subscribe alongside the existing refresh subscription inside the same `onConnect` (`useGameSocket.ts:33-39`), with the same ref pattern used for `onRefresh`:

```typescript
export function useGameSocket(
  gameId: string,
  onRefresh: () => void,
  onStateMessage?: (msg: GameStateMessage) => void
) {
  // ... existing refs; add:
  const onStateMessageRef = useRef(onStateMessage);
  useEffect(() => { onStateMessageRef.current = onStateMessage; }, [onStateMessage]);

  // inside client.onConnect, after the existing subscribe:
  client.subscribe(`/topic/game/${gameId}/state`, (msg) => {
    try {
      onStateMessageRef.current?.(JSON.parse(msg.body) as GameStateMessage);
    } catch (e) {
      console.error("Bad game state message", e);
    }
  });
}
```

Existing callers pass two args and are unaffected.

### Task D3: `useGameState` hook — fetch, patch application, sequencing

**Files:**
- Create: `src/hooks/useGameState.ts` (single new hook owning the whole client contract)
- Modify: `src/hooks/usePlayerData.ts` (wire the handler through `usePlayerDataSocket`)

```typescript
// src/hooks/useGameState.ts
import { useCallback, useRef } from "react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { GameState, GameStateMessage } from "@/entities/data/types";
import { config } from "@/config";

// RFC 7386-style merge: null deletes, arrays/scalars replace, objects recurse.
export function deepMergePatch<T>(base: T, patch: any): T {
  if (patch === null || typeof patch !== "object" || Array.isArray(patch)) return patch as T;
  if (base === null || typeof base !== "object" || Array.isArray(base)) base = {} as T;
  const result: any = { ...base };
  for (const [key, value] of Object.entries(patch)) {
    if (value === null) result[key] = null;
    else result[key] = deepMergePatch((result as any)[key], value);
  }
  return result;
}

async function fetchGameState(gameId: string): Promise<GameState> {
  const response = await fetch(`${config.api.gameDataUrl}/${gameId}/game-state`);
  if (!response.ok) throw new Error(`Failed to fetch game state: ${response.status}`);
  return response.json();
}

export function useGameState(gameId: string) {
  return useQuery<GameState>({
    queryKey: ["gameState", gameId],
    queryFn: () => fetchGameState(gameId),
    retry: false,
  });
}

/** Returns a stable handler for GameStateMessages; give it to useGameSocket. */
export function useGameStatePatcher(gameId: string) {
  const queryClient = useQueryClient();
  const lastSeqRef = useRef<number | null>(null);

  return useCallback(
    (msg: GameStateMessage) => {
      const key = ["gameState", gameId];
      const cached = queryClient.getQueryData<GameState>(key);

      if (msg.full || cached === undefined) {
        queryClient.setQueryData(key, msg.patch as GameState);
        lastSeqRef.current = msg.seq;
        return;
      }
      if (lastSeqRef.current !== null && msg.seq === lastSeqRef.current + 1) {
        queryClient.setQueryData(key, deepMergePatch(cached, msg.patch));
        lastSeqRef.current = msg.seq;
        return;
      }
      // Gap or seq reset (bot restart): resync from the cheap endpoint.
      lastSeqRef.current = null;
      void queryClient.invalidateQueries({ queryKey: key });
    },
    [gameId, queryClient]
  );
}
```

In `usePlayerDataSocket` (`usePlayerData.ts:27-57`): create the patcher and pass it as the third argument to `useGameSocket`; in the existing reconnect-aware refresh callback, also invalidate `["gameState", gameId]` when a reconnect happened (the `hasConnectedBefore` branch already distinguishes first connect).

Note on `lastSeqRef === null` after a resync: the next incoming message hits the `cached === undefined`-or-gap logic — if the refetch already landed, treat the first post-resync message's `seq` as the new baseline only when it's `full`; otherwise one extra invalidate fires. Harmless (self-heals) and keeps the logic tiny.

### Task D4: `GameStatePanel` component

**Files:**
- Create: `src/domains/game-shell/components/GameStatePanel.tsx`
- Modify: `src/domains/game-shell/components/LeftSidebar.tsx` (mount it at the top of the `Stack`, `LeftSidebar.tsx:23`)

Structure (reuse `Panel`, `Chip`, `SmoothPopover`, `classes.sectionHeading`; no new CSS module unless spacing demands it):

```tsx
export function GameStatePanel() {
  const gameId = useGameContext().gameId; // or however LeftSidebar siblings get it
  const { data: gameState } = useGameState(gameId);
  const gameData = useGameData(); // playerData for color -> faction/name mapping
  if (!gameState || gameState.phase === "unknown") return null;

  return (
    <Panel variant="subtle">
      <PhaseBadge phase={gameState.phase} />
      {gameState.phase === "finished" ? (
        <WinnerBanner color={gameState.winner} playerData={gameData?.playerData} />
      ) : (
        <>
          <ActivePlayerRow
            color={gameState.activePlayer}
            turnStartedAt={gameState.turnStartedAt}
            phase={gameState.phase}
            playerData={gameData?.playerData}
          />
          {gameState.agenda && <AgendaRow agenda={gameState.agenda} activePlayer={gameState.activePlayer} />}
          {gameState.activeCombat && <CombatRow combat={gameState.activeCombat} playerData={gameData?.playerData} />}
        </>
      )}
    </Panel>
  );
}
```

Sub-pieces (all private to this file — one component file, not a directory of fragments):

- **`PhaseBadge`**: map `GamePhase` → label + accent (`strategy` blue, `action` green, `status.*` yellow, `agenda.*` orange, `finished` red). Labels: `"agenda.voting"` → "Agenda · Voting", etc. Small `Record<GamePhase, {label, accent}>` in the same file.
- **`ActivePlayerRow`**: dot/chip tinted with the player color (resolve display name via `playerData.find(p => p.color === color)`), phrase varies by phase group ("is picking", "'s turn", "is voting"); elapsed timer from `turnStartedAt` with a 1 s `setInterval` in a `useEffect` (format `h:mm:ss`, drop hours when 0). Skip the interval entirely when `turnStartedAt` is null.
- **`AgendaRow`**: name from `agendas.ts` lookup by `id` (fallback to raw id); `SmoothPopover` on click showing target/text1/text2 (same pattern as `CompactLaw`/`LawDetailsCard`, `LeftSidebar.tsx:60-79`); outcome votes as `For 12 · Against 8` (join entries of `outcomeVoteCounts`; for elect-style agendas the keys are already display-ready outcome strings); when `activePlayer` is set and phase is `agenda.voting`, show `{name} has {startVoteCounts[activePlayer]} votes to cast`.
- **`CombatRow`**: `Combat — {system}{unitHolder ? ` (${unitHolder})` : ""} · R{round}` plus participant chips colored by `participantColors`.
- **`WinnerBanner`**: `🏆 {factionOrName} wins!` tinted by winner color.

Mount in `LeftSidebar` above the game-name block; it renders nothing until the first `/game-state` response arrives, so the sidebar is unchanged for games where the backend hasn't shipped `gameState` yet (the query 404s → `data` stays undefined → panel hidden — exactly the graceful-degradation we want).

### Task D5: Frontend verification

1. `npx tsc -p tsconfig.app.json --noEmit` and `yarn lint` → clean.
2. `yarn dev` with `websocketUrl` switched to `ws://localhost:8081/ws` and the `/bot` proxy pointed at the local bot (`src/config.ts` dev block); run the backend locally with a test game.
3. Walk the same script as Task C3 while watching the panel: turn pass → chip + timer reset; agenda reveal → agenda row appears; each vote → outcome counts tick without any full refetch (verify in devtools network tab: no `/web-data` request); combat start/end → combat row appears/disappears; undo → panel reverts; kill and restart the bot → panel heals on next game action (`full: true` path); toggle sidebar collapse → panel respects `leftPanelCollapsed`.
4. Commit in `ti4_web_new`.

---

## Future extensions (deliberately out of scope)

- **Streaming other webData slices** (objectives scored, player stats): same notifier pattern — cache a baseline per `gameId + slice`, diff, publish to `/state/<slice>`; add when the web UI needs it.
- **`stateHash` in the full web-data payload** so clients can detect their cached full payload predates the stream — only if staleness bugs show up despite the reconnect/gap refetch.
