package ti4.contest.replay.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.contest.replay.core.CombatReplayHouse;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "combat_replay_house_score",
        indexes = {@Index(name = "idx_replay_house_score_house", columnList = "house")},
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_replay_house_score_contest_house",
                    columnNames = {"contest_id", "house"})
        })
/**
 * Stores per-contest house-only scoring, including house ability bonuses that do not affect individual players.
 */
public class CombatReplayHouseScoreEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "house", nullable = false)
    private CombatReplayHouse house;

    @Column(name = "prediction_points", nullable = false)
    private Integer predictionPoints;

    @Column(name = "side_bet_points", nullable = false)
    private Integer sideBetPoints;

    @Column(name = "ability_points", nullable = false)
    private Integer abilityPoints;

    @Column(name = "favor_points")
    private Integer favorPoints;

    @Column(name = "total_points", nullable = false)
    private Integer totalPoints;

    @Column(name = "prediction_count", nullable = false)
    private Integer predictionCount;

    @Column(name = "correct_predictions", nullable = false)
    private Integer correctPredictions;

    @Column(name = "ability_breakdown_json", nullable = false, columnDefinition = "TEXT")
    private String abilityBreakdownJson;

    @Column(name = "scored_at", nullable = false)
    private LocalDateTime scoredAt;
}
