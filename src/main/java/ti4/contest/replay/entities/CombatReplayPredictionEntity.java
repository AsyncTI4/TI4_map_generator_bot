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
        name = "combat_replay_prediction",
        indexes = @Index(name = "idx_replay_prediction_contest_id", columnList = "contest_id"),
        uniqueConstraints = @UniqueConstraint(name = "uk_replay_prediction_contest", columnNames = "contest_id"))
/**
 * Stores the locked replay prediction snapshot for a contest in one compact row.
 */
public class CombatReplayPredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "announced_at")
    private LocalDateTime announcedAt;

    @Column(name = "scored_at")
    private LocalDateTime scoredAt;

    @Column(name = "attacker_prediction_count", nullable = false)
    private Integer attackerPredictionCount;

    @Column(name = "defender_prediction_count", nullable = false)
    private Integer defenderPredictionCount;

    @Column(name = "mentak_prediction_override_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer mentakPredictionOverrideCount = 0;

    @Column(name = "attacker_predictions_json", nullable = false, columnDefinition = "TEXT")
    private String attackerPredictionsJson;

    @Column(name = "defender_predictions_json", nullable = false, columnDefinition = "TEXT")
    private String defenderPredictionsJson;
}
