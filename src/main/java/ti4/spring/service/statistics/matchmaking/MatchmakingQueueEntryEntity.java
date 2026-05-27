package ti4.spring.service.statistics.matchmaking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "matchmaking_queue", uniqueConstraints = @UniqueConstraint(columnNames = {"user_id"}))
public class MatchmakingQueueEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "username", nullable = false)
    private String username;

    @Column(name = "queued_at_utc", nullable = false)
    private LocalDateTime queuedAtUtc;

    @Column(name = "expansions_csv", nullable = false)
    private String expansionsCsv;

    @Column(name = "player_counts_csv", nullable = false)
    private String playerCountsCsv;

    @Column(name = "victory_points_csv", nullable = false)
    private String victoryPointsCsv;

    @Column(name = "restrictions_csv", nullable = false)
    private String restrictionsCsv;

    @Column(name = "max_queue_time_minutes", nullable = false)
    private int maxQueueTimeMinutes;
}
