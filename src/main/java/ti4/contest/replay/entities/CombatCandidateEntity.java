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
import ti4.contest.replay.core.CombatCandidatePromotionStatus;
import ti4.contest.replay.core.CombatCandidateStatus;
import ti4.spring.service.contest.CombatContestType;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "combat_candidate",
        indexes = {
            @Index(name = "idx_combat_candidate_status_started_at", columnList = "status, started_at"),
            @Index(name = "idx_combat_candidate_promotion_resolved_at", columnList = "promotion_status, resolved_at"),
            @Index(name = "idx_combat_candidate_game_tile_status", columnList = "game_name, tile_position, status"),
            @Index(name = "idx_combat_candidate_promoted_at", columnList = "promoted_at")
        })
/**
 * Represents a replay-worthy combat that is being tracked, resolved, promoted, or expired.
 */
public class CombatCandidateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "observation_id", nullable = false, unique = true)
    private Long observationId;

    @Column(nullable = false)
    private CombatCandidateStatus status;

    @Column(name = "promotion_status", nullable = false)
    private CombatCandidatePromotionStatus promotionStatus;

    @Column(name = "next_event_sequence", nullable = false)
    private Integer nextEventSequence;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "promoted_at")
    private LocalDateTime promotedAt;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "tile_position", nullable = false)
    private String tilePosition;

    @Column(name = "combat_type", nullable = false)
    private CombatContestType combatType;

    @Column(name = "attacker_faction", nullable = false)
    private String attackerFaction;

    @Column(name = "defender_faction", nullable = false)
    private String defenderFaction;

    @Column(name = "winner_faction")
    private String winnerFaction;

    @Column(name = "loser_faction")
    private String loserFaction;

    @Column(name = "resolution_reason", columnDefinition = "TEXT")
    private String resolutionReason;

    @Column(name = "cancellation_reason", columnDefinition = "TEXT")
    private String cancellationReason;

    @Column(name = "pre_replay_context_text", columnDefinition = "TEXT")
    private String preReplayContextText;

    @Column(name = "initial_render_snapshot_json", columnDefinition = "TEXT")
    private String initialRenderSnapshotJson;

    @Column(name = "promotion_score")
    private Double promotionScore;
}
