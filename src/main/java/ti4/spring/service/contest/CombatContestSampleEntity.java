package ti4.spring.service.contest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "combat_predictor_sample",
        indexes = {
            @Index(name = "idx_combat_sample_started_at", columnList = "started_at"),
            @Index(name = "idx_combat_sample_game_tile", columnList = "game_name, tile_position, started_at")
        })
public class CombatContestSampleEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "tile_position", nullable = false)
    private String tilePosition;

    @Column(name = "attacker_strength", nullable = false)
    private Double attackerStrength;

    @Column(name = "defender_strength", nullable = false)
    private Double defenderStrength;

    @Column(name = "attacker_hp", nullable = false)
    private Double attackerHp;

    @Column(name = "defender_hp", nullable = false)
    private Double defenderHp;

    @Column(name = "attacker_expected_hits")
    private Double attackerExpectedHits;

    @Column(name = "defender_expected_hits")
    private Double defenderExpectedHits;

    @Column(name = "weaker_strength", nullable = false)
    private Double weakerStrength;

    @Column(name = "stronger_strength", nullable = false)
    private Double strongerStrength;

    @Column(name = "weaker_hp", nullable = false)
    private Double weakerHp;

    @Column(name = "stronger_hp", nullable = false)
    private Double strongerHp;

    @Column(name = "fairness_ratio", nullable = false)
    private Double fairnessRatio;

    @Column(name = "contest_score", nullable = false)
    private Double contestScore;

    @Column(name = "score_cutoff_at_start", nullable = false)
    private Double scoreCutoffAtStart;

    @Column(name = "selection_mode_at_start", nullable = false)
    private String selectionModeAtStart;

    @Column(name = "eligible_under_current_thresholds", nullable = false)
    private Boolean eligibleUnderCurrentThresholds;

    @Column(name = "contest_posted", nullable = false)
    private Boolean contestPosted;
}
