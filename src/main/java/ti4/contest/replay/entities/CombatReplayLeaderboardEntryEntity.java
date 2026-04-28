package ti4.contest.replay.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "combat_replay_leaderboard_entry",
        indexes = @Index(name = "idx_replay_leaderboard_points", columnList = "total_points"),
        uniqueConstraints = @UniqueConstraint(name = "uk_replay_leaderboard_user", columnNames = "discord_user_id"))
/**
 * Stores cumulative replay leaderboard totals per Discord user.
 */
public class CombatReplayLeaderboardEntryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discord_user_id", nullable = false)
    private String discordUserId;

    @Column(name = "discord_user_name", nullable = false)
    private String discordUserName;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    @Column(name = "prediction_count", nullable = false)
    private Integer predictionCount;

    @Column(name = "correct_predictions", nullable = false)
    private Integer correctPredictions;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
