package ti4.spring.api.dashboard;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "player_aggregates")
/*
 Persistent cache row for dashboard aggregates for a single player.

 <p>The row is keyed by {@code user_id}. Aggregate payloads are stored in {@code aggregates_json}
 so the schema can evolve without adding a new table for each aggregate type.

 <p>{@code completed_games_hash} and {@code completed_game_count} are used for cache-busting:
 when either no longer matches the currently observed completed games for a player, aggregates
 are recomputed asynchronously and this row is overwritten.
*/
class PlayerAggregatesCache {

    @Id
    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "completed_games_hash", nullable = false)
    private String completedGamesHash;

    @Column(name = "completed_game_count", nullable = false)
    private int completedGameCount;

    @Column(name = "aggregates_version", nullable = false)
    private int aggregatesVersion;

    @Lob
    @Column(name = "aggregates_json", nullable = false)
    private String aggregatesJson;

    @Column(name = "computed_at_epoch_ms", nullable = false)
    private long computedAtEpochMs;

    @Column(name = "last_error")
    private String lastError;

    @Column(name = "last_error_at_epoch_ms")
    private Long lastErrorAtEpochMs;
}
