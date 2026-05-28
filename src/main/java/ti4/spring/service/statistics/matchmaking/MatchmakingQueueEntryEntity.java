package ti4.spring.service.statistics.matchmaking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ti4.spring.service.persistence.UserEntity;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "matchmaking_queue", uniqueConstraints = @UniqueConstraint(columnNames = "user_id"))
public class MatchmakingQueueEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

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
    private int maxQueueTimeHours;

    @Column(name = "channel_id")
    private String channelId;

    public List<String> getVictoryPoints() {
        return splitToList(victoryPointsCsv);
    }

    public List<String> getPlayerCounts() {
        return splitToList(playerCountsCsv);
    }

    public List<String> getExpansions() {
        return expansionsCsv == null ? List.of() : splitToList(expansionsCsv);
    }

    public List<String> getRestrictions() {
        return restrictionsCsv == null ? List.of() : splitToList(restrictionsCsv);
    }

    private List<String> splitToList(String csv) {
        return Arrays.asList(csv.split(","));
    }
}
