package ti4.contest.replay.entities;

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
        name = "combat_observation",
        indexes = {
            @Index(name = "idx_combat_observation_started_at", columnList = "started_at"),
            @Index(
                    name = "idx_combat_observation_game_tile_started_at",
                    columnList = "game_name, tile_position, started_at")
        })
/**
 * Stores the initial snapshot and scoring inputs for a combat observed by the replay selector.
 */
public class CombatObservationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "tile_position", nullable = false)
    private String tilePosition;

    @Column(name = "attacker_faction", nullable = false)
    private String attackerFaction;

    @Column(name = "defender_faction", nullable = false)
    private String defenderFaction;

    @Column(name = "attacker_strength", nullable = false)
    private Double attackerStrength;

    @Column(name = "defender_strength", nullable = false)
    private Double defenderStrength;

    @Column(name = "attacker_hp", nullable = false)
    private Double attackerHp;

    @Column(name = "defender_hp", nullable = false)
    private Double defenderHp;

    @Column(name = "attacker_expected_hits", nullable = false)
    private Double attackerExpectedHits;

    @Column(name = "defender_expected_hits", nullable = false)
    private Double defenderExpectedHits;

    @Column(name = "fairness_ratio", nullable = false)
    private Double fairnessRatio;

    @Column(name = "joint_score", nullable = false)
    private Double jointScore;
}
