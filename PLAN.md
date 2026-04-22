# Lazax Plan

This file captures the current planning baseline for the Lazax candidate-recording and hourly-promotion redesign.

## Core Flow

- Listen to every eligible combat start.
- Periodically recompute thresholds from recent combat observations.
- If a combat start is above the current threshold snapshot, create a candidate and track it fully.
- Record all important combat events for tracked candidates.
- Do not post tracked combats publicly while they are live.
- Resolve or cancel candidates privately.
- On the hour, choose the best resolved non-cancelled candidate from the recent window and promote it to a public contest.

## Current Planning Assumptions

- The reduced schema below is the preferred planning baseline for now.
- `combat_observation` exists for every listened combat start, whether it becomes a candidate or not.
- `combat_candidate` exists only for starts that pass the active threshold snapshot.
- `combat_candidate_event` is append-only and is the main replay source for future public recap generation.
- `combat_contest` is the public artifact created after promotion, not the live combat-tracking record.
- Prediction and leaderboard tables are intentionally excluded from this baseline for now.
- `combat_selection_run` is intentionally minimal here, but we may later add explicit threshold columns such as `fairness_floor` and `combat_size_cutoff` if phase 1 still uses the current sequential thresholding model instead of a pure joint score.
- This should be built as a greenfield Lazax pipeline.
- It may reuse shared logic from the existing Lazax implementation, but it should not replace or mutate the current live system in place.

## Tables

### `combat_selection_run`

- id
- computed_at
- window_start
- window_end
- sample_count
- target_candidates_per_hour
- joint_score_cutoff
- notes_json

### `combat_observation`

- id
- started_at
- selection_run_id
- game_name
- tile_position
- combat_type
- attacker_faction
- defender_faction
- attacker_strength
- defender_strength
- attacker_hp
- defender_hp
- attacker_expected_hits
- defender_expected_hits
- fairness_ratio
- joint_score
- eligible_as_candidate
- candidate_id

### `combat_candidate`

- id
- observation_id
- status
- promotion_status
- next_event_sequence
- started_at
- resolved_at
- promoted_at
- game_name
- tile_position
- combat_type
- attacker_faction
- defender_faction
- winner_faction
- loser_faction
- resolution_reason
- cancellation_reason
- promotion_score

### `combat_candidate_event`

- id
- candidate_id
- occurred_at
- sequence_number
- event_type
- round_number
- actor_faction
- summary_text
- payload_json

### `combat_contest`

- id
- candidate_id
- posted_at
- public_channel_id
- public_message_id
- public_thread_id
- replay_status
- replay_start_at
- next_replay_at
- next_event_sequence
- replay_completed_at
- replay_error

## Constraints And Indexes

The schema should reflect the actual access patterns of selection, promotion, replay, and cleanup.

### Core constraints

- `combat_observation.candidate_id` should be unique when non-null
  - one observation can create at most one candidate
- `combat_candidate.observation_id` should be unique
  - every candidate comes from exactly one observation
- `combat_contest.candidate_id` should be unique
  - one candidate can become at most one public contest
- `combat_candidate_event (candidate_id, sequence_number)` should be unique
  - replay order must be stable and unambiguous
- at most one open candidate should exist for the same `(game_name, tile_position, combat_type)`
  - "open" means `status = TRACKING`
  - closed historical candidates on the same tile must not block future candidates

### Suggested indexes

#### `combat_selection_run`

- index on `(computed_at desc)`
  - fetch latest selection snapshot quickly

#### `combat_observation`

- index on `(started_at)`
  - lookback-window scoring and cleanup
- index on `(eligible_as_candidate, started_at)`
  - inspection of recently qualified observations
- index on `(game_name, tile_position, started_at desc)`
  - correlate observations for a combat location over time

#### `combat_candidate`

- index on `(status, started_at)`
  - find active tracking rows and candidate cleanup windows
- index on `(promotion_status, resolved_at)`
  - hourly promotion lookup and expiration
- index on `(game_name, tile_position, status)`
  - route live events to active tracked candidates for a combat
- index on `(promoted_at)`
  - reporting and cleanup of promoted candidates

#### `combat_candidate_event`

- unique index on `(candidate_id, sequence_number)`
  - ordered replay reads and event idempotency
- index on `(candidate_id, occurred_at)`
  - timeline reads and candidate cleanup

#### `combat_contest`

- unique index on `(candidate_id)`
  - one contest per promoted candidate
- index on `(replay_status, next_replay_at)`
  - find due replay work efficiently
- index on `(posted_at)`
  - reporting and cleanup

## Retention And Janitor

This feature will generate a lot of observation and event rows. Phase 1 should include a janitor cron from the start.

### Retention policy

- `combat_selection_run`
  - keep for 30 days
- `combat_observation`
  - keep for 14 days
- `combat_candidate`
  - keep indefinitely for now
  - this is the useful historical record of tracked combats
- `combat_candidate_event`
  - keep for 14 days after candidate promotion or terminal resolution
  - if replay for a promoted contest is still active, do not delete
- `combat_contest`
  - keep indefinitely for now

### Janitor behavior

Add a janitor cron such as `CombatContestJanitorCron`.

Responsibilities:

- expire resolved candidates that aged out of the promotion window:
  - `promotion_status = PENDING`
  - `resolved_at < now - 4 hours`
  - set `promotion_status = EXPIRED`
- delete old `combat_observation` rows past retention
- delete old `combat_selection_run` rows past retention
- delete old `combat_candidate_event` rows only when:
  - candidate is `RESOLVED`, `CANCELLED`, or `PROMOTED`
  - associated replay is `COMPLETED` or no contest exists
  - event age is past retention
- optionally clear stale `replay_error` values on completed contests

### Janitor scheduling

- Run every hour.
- Janitor work should run only on the scheduled-work leaseholder.
- Cleanup should be conservative: do not delete data tied to in-progress replay or unresolved candidates.

## Enum Baseline

### `combat_candidate.status`

- `TRACKING`
  - candidate was created and is still being monitored
- `RESOLVED`
  - combat finished with a valid winner and can be considered for promotion
- `CANCELLED`
  - combat became invalid for promotion or ended before meeting minimum replay requirements

### `combat_candidate.promotion_status`

- `PENDING`
  - resolved candidate has not yet been promoted or expired from the promotion window
- `PROMOTED`
  - candidate has been selected and became a public contest
- `EXPIRED`
  - candidate aged out of the promotion window without being promoted

### `combat_contest.replay_status`

- `PENDING`
  - replay thread exists but replay has not started yet
- `REPLAYING`
  - replay is currently posting events
- `COMPLETED`
  - all replay events were posted
- `FAILED`
  - replay encountered an error and needs operator attention or retry logic

### `combat_candidate_event.event_type`

- `START`
  - opening snapshot for a newly created candidate
- `ROLL`
  - combat roll output
- `HIT_ASSIGN`
  - hit assignment or damage assignment update
- `CARD`
  - combat-relevant card play
- `AGENT`
  - combat-relevant agent use
- `INFO`
  - general combat update that should appear in replay
- `RESOLVED`
  - terminal success event with winner
- `CANCELLED`
  - terminal failure event with reason

## Event Payload Contract

`combat_candidate_event.payload_json` should be typed by `event_type`. The payload should be compact, replayable, and not require historic Discord message lookup.

### Common payload fields

Every payload should support these top-level keys when applicable:

- `game_name`
- `tile_position`
- `round_number`
- `actor_faction`
- `actor_user_name`
- `source`
- `raw_text`
- `render_context`

`render_context` is optional structured context for future formatting, such as faction display data or tile representation.

### `START` payload

Use for the candidate opener snapshot.

- `attacker_faction`
- `defender_faction`
- `attacker_strength`
- `defender_strength`
- `attacker_hp`
- `defender_hp`
- `attacker_expected_hits`
- `defender_expected_hits`
- `fairness_ratio`
- `joint_score`
- `summary`

### `ROLL` payload

Use for combat roll replay.

- `roller_faction`
- `opponent_faction`
- `round_number`
- `roll_type`
- `message_text`
- `raw_rolls`
- `hits`
- `expected_hits`

`message_text` should be enough for basic replay even if richer fields are absent.

### `HIT_ASSIGN` payload

Use for hit or damage assignment updates.

- `assigning_faction`
- `round_number`
- `message_text`
- `assigned_hits`
- `assigned_damage`
- `affected_units`

### `CARD` payload

Use for combat-relevant card plays.

- `playing_faction`
- `card_name`
- `message_text`
- `embed_title`
- `embed_description`

### `AGENT` payload

Use for combat-relevant agent plays.

- `playing_faction`
- `agent_name`
- `message_text`
- `embed_title`
- `embed_description`

### `INFO` payload

Use for general combat updates that should appear in the replay but are not rolls, assignments, cards, or agents.

- `message_text`
- `detail_type`
- `details`

### `RESOLVED` payload

Use for successful combat completion.

- `winner_faction`
- `loser_faction`
- `round_number`
- `message_text`
- `resolution_reason`

### `CANCELLED` payload

Use for invalidated or non-qualifying combat completion.

- `message_text`
- `cancellation_reason`

## Event Rendering Guidance

- `summary_text` should contain the plain-text replay line that can be posted directly if richer rendering fails.
- `payload_json` should carry enough structured data to support better formatting later.
- Replay should prefer structured rendering from `payload_json`, but always be able to fall back to `summary_text`.
- The payload contract should be version-tolerant: missing optional fields should not break replay.

## Normalized Joint Scoring Model

The target is to choose candidates by one normalized joint threshold, not by separately targeting fairness and strength.

### Inputs Per Observed Combat

- `attacker_strength`
- `defender_strength`
- `attacker_hp`
- `defender_hp`
- `attacker_expected_hits`
- `defender_expected_hits`

### Derived Metrics

- `weaker_strength = min(attacker_strength, defender_strength)`
- `stronger_strength = max(attacker_strength, defender_strength)`
- `weaker_hp = min(attacker_hp, defender_hp)`
- `stronger_hp = max(attacker_hp, defender_hp)`
- `weaker_expected_hits = min(attacker_expected_hits, defender_expected_hits)`
- `stronger_expected_hits = max(attacker_expected_hits, defender_expected_hits)`
- `strength_fairness_ratio = weaker_strength / stronger_strength`
- `hp_fairness_ratio = weaker_hp / stronger_hp`
- `expected_hits_fairness_ratio = weaker_expected_hits / stronger_expected_hits`
- `fairness_ratio = average(strength_fairness_ratio, hp_fairness_ratio, expected_hits_fairness_ratio)`

### Normalized Components

Use normalized values for candidate selection rather than raw combat scale.

For each selection recompute window:

1. Compute `fairness_ratio` for every observation in the window.
2. Compute `weaker_strength` for every observation in the window.
3. Convert both into percentile-style normalized scores in `[0, 1]`.

- `normalized_fairness_score = percentile_rank(fairness_ratio)`
- `normalized_strength_score = percentile_rank(weaker_strength)`

This makes the score distribution stable even if overall combat scale changes across hours.

### Normalized Joint Score

For the first pass, use one combined normalized score:

- `normalized_joint_score = normalized_fairness_score * normalized_strength_score`

Store that value in `combat_observation.joint_score` and use it for thresholding.

This keeps the behavior easy to reason about:

- combats that are weak relative to the recent window score down
- combats that are unfair relative to the recent window score down
- combats that are both large and fair relative to the recent window score up

### Threshold Selection

On each selection recompute:

1. Look at all `combat_observation` rows in the last hour.
2. Compute `normalized_joint_score` for each observation.
3. Sort the scores descending.
4. Choose the cutoff score that would admit the target number of candidates per hour.
5. Persist that cutoff to `combat_selection_run.joint_score_cutoff`.
6. Persist any normalization details needed for runtime scoring in `combat_selection_run.notes_json`.

### Runtime Normalization Rule

Keep this simple: candidate evaluation at combat start should recompute normalization against the live rolling lookback window rather than trying to persist a full normalization model.

Rules:

- When a combat starts, load the current lookback window of `combat_observation` rows.
- Recompute percentile ranks for `fairness_ratio` and `weaker_strength` from that live window.
- Compute the new combat's `normalized_joint_score` against that live distribution.
- Use the current target candidate rate to derive the cutoff from that same live window.
- `combat_selection_run` remains a persisted snapshot for observability and debugging, not the sole source of truth for runtime normalization.
- `combat_selection_run.notes_json` may store summary values for inspection, but phase 1 does not require storing the full empirical distribution.

### Warmup Rule

The system should remain technically functional before enough data exists to tune it well.

Rules:

- If the live lookback window is empty, do not create candidates.
- If the live lookback window is very small, still compute scores against the available rows and allow candidate creation.
- During this warmup period the quality of selection may be poor, but the system should not crash or become unusable.
- Operationally, Lazax can remain disabled until enough observation data has accumulated.

### Candidate Creation Rule

At combat start:

- compute the combat's normalized component scores from the live rolling lookback window
- compute the combat's `normalized_joint_score`
- compare it to the cutoff derived from that same live rolling window
- if `normalized_joint_score >= joint_score_cutoff`, mark the observation as eligible and create a `combat_candidate`

### Why This Model

- It gives one targetable distribution instead of trying to make two independent thresholds jointly hit one rate.
- It is more statistically stable than multiplying a bounded fairness ratio by a raw strength value.
- It keeps the selection logic comparable across hours with different combat sizes.
- It keeps `fairness_ratio` available for debugging and future ranking, even if `normalized_joint_score` is the actual gating threshold.

### Possible Refinements

- Add a hard minimum fairness floor if percentile normalization still allows lopsided combats through in low-quality windows.
- Change the combination rule from multiplication to a weighted geometric or weighted harmonic mean if one dimension needs to matter more.
- Use the promotion stage to rank "best contest" differently from the candidate stage if qualification and promotion turn out to want different behavior.

## Replay Architecture

Replay should be database-driven and durable, not scheduled as a chain of in-memory delayed tasks.

### Replay Trigger

When a candidate is promoted:

1. Create the public contest record.
2. Create the public replay thread immediately.
3. Initialize contest replay state:
   - `replay_status = PENDING`
   - `replay_start_at = promoted_at + 10 minutes`
   - `next_replay_at = replay_start_at`
   - `next_event_sequence = 1`

### Replay Source

- `combat_candidate_event` is the source of truth for replay.
- Events are replayed in ascending `sequence_number` order.
- Each event row should contain enough information to render one replay step without re-reading historic Discord messages.

### Event Source Inventory

Phase 1 event recording should be wired from the existing Lazax integration points:

- `START`
  - emitted from `CombatContestService.onSpaceCombatStarted`
- `ROLL`
  - emitted from `CombatContestService.mirrorCombatRoll`
  - currently fed by `CombatRollService`
- `HIT_ASSIGN`
  - emitted from `CombatContestService.trackHitAssignments`
  - currently reached from `CombatContestService.onButtonInteractionSettled`
- `CARD`
  - emitted from `CombatContestService.mirrorCombatEvent`
  - currently called from `ActionCardHelper`
- `AGENT`
  - emitted from `CombatContestService.mirrorCombatEvent`
  - currently called from `ButtonHelperAgents`
- `INFO`
  - emitted from `CombatContestService.mirrorCombatEvent`
  - currently called from other combat-relevant helpers such as `PlayHeroService`
- `RESOLVED`
  - emitted when candidate resolution determines a valid winner
- `CANCELLED`
  - emitted when candidate tracking is invalidated or ends below minimum replay quality

### Event Ordering And Idempotency

Event recording and replay both need simple, deterministic sequencing rules.

#### Candidate event sequence assignment

- `combat_candidate.next_event_sequence` is the allocator for candidate event ordering.
- When writing a candidate event:
  - load and lock the candidate row
  - read `next_event_sequence`
  - insert the event using that value as `sequence_number`
  - increment `combat_candidate.next_event_sequence`
- Add a uniqueness constraint on `(candidate_id, sequence_number)`.

This keeps event ordering stable even if multiple event hooks fire close together.

#### Replay advancement rule

- `combat_contest.next_event_sequence` means "the next candidate event that still needs to be replayed".
- Replay only advances this value after the Discord send succeeds.
- If the Discord send fails:
  - do not increment `next_event_sequence`
  - keep the contest pointed at the same event
  - set `next_replay_at = now + 15 seconds`
  - record the error in `replay_error`

This makes replay idempotent enough for phase 1: the system never skips an event and always retries the same event until it posts successfully.

### Replay Execution Model

Use a polling cron such as `CombatContestReplayCron` rather than a chain of `scheduleOnce` calls.

Recommended behavior:

- Run the replay cron every 5 to 15 seconds.
- On each tick, find contests where:
  - `replay_status in (PENDING, REPLAYING)`
  - `next_replay_at <= now`
- For each due contest:
  - load the candidate event with `sequence_number = next_event_sequence`
  - render and post that event into the contest thread
  - if the post succeeds:
    - increment `next_event_sequence`
    - set `next_replay_at = now + 15 seconds`
    - set `replay_status = REPLAYING`
    - clear `replay_error`
  - if the post fails:
    - leave `next_event_sequence` unchanged
    - set `next_replay_at = now + 15 seconds`
    - keep `replay_status = REPLAYING`
    - store the failure in `replay_error`
- If no next event exists:
  - set `replay_status = COMPLETED`
  - set `replay_completed_at = now`

### Why This Model

- Durable across restarts
- Easy to resume if the bot dies mid-replay
- Easy to inspect from the database
- Avoids a large number of fragile in-memory delayed tasks
- Keeps replay logic simple: one due contest, one due event, one posted message

### Concurrency

- Replay work should only run on the scheduled-work leaseholder process.
- Contest replay updates should happen transactionally so the same event is not posted twice.
- If needed later, add explicit claim fields or a replay lock token, but phase 1 can likely rely on the active lease plus careful row updates.

### Replay Retry Policy

Keep the retry model intentionally simple in phase 1.

Rules:

- There is no backoff.
- There is no skip-ahead behavior.
- There is no retry limit.
- If replay of one event fails, replay stays pinned on that event and retries it on the next replay tick.
- Later events do not post until the blocked event succeeds.
- `FAILED` remains available for future manual or operator-controlled behavior, but phase 1 should normally retry in `REPLAYING` state rather than transition to `FAILED` on ordinary send errors.

## Promotion Selection Notes

- Phase 1 promotion should optimize for exciting combats, not merely large ones.
- Use a simple promotion score composed of:
  - number of rounds observed
  - meaningful losses suffered by both sides
- A reasonable v1 shape is:
  - `promotion_score = rounds_observed + mutual_loss_score`
- `mutual_loss_score` should reward combats where both sides lost meaningful value, not just one-sided stomps.
- The exact formula can be tuned later, but phase 1 should clearly favor long, costly, back-and-forth combats.
- If no resolved candidates exist in the hourly promotion window, do not post a contest for that hour.
- Promotion should mark the winning candidate as `PROMOTED` only after the public contest row and thread are created successfully.
- If public contest creation fails, leave the candidate eligible for a future promotion run rather than consuming it.
- If multiple resolved candidates tie on the main promotion score, use higher `joint_score` as the first tie-breaker.
- If candidates are still tied after that, use earlier `resolved_at`.
- If they are still tied after that, use lower `candidate.id`.

This keeps the first implementation deterministic without forcing the final promotion-scoring formula yet.
