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

    @Column(name = "side_bet_compatible", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean sideBetCompatible = false;

    @Column(name = "attacker_destroyer_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer attackerDestroyerCount = 0;

    @Column(name = "defender_destroyer_count", nullable = false, columnDefinition = "INTEGER DEFAULT 0")
    private Integer defenderDestroyerCount = 0;

    @Column(name = "attacker_rolled_afb", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean attackerRolledAfb = false;

    @Column(name = "defender_rolled_afb", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean defenderRolledAfb = false;

    @Column(name = "attacker_afb_whiff", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean attackerAfbWhiff = false;

    @Column(name = "defender_afb_whiff", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean defenderAfbWhiff = false;

    @Column(name = "attacker_round_one_whiff", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean attackerRoundOneWhiff = false;

    @Column(name = "defender_round_one_whiff", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean defenderRoundOneWhiff = false;

    @Column(name = "attacker_round_one_slam", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean attackerRoundOneSlam = false;

    @Column(name = "defender_round_one_slam", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean defenderRoundOneSlam = false;

    @Column(name = "attacker_played_morale_boost", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean attackerPlayedMoraleBoost = false;

    @Column(name = "defender_played_morale_boost", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean defenderPlayedMoraleBoost = false;

    @Column(name = "attacker_played_shields_holding", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean attackerPlayedShieldsHolding = false;

    @Column(name = "defender_played_shields_holding", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean defenderPlayedShieldsHolding = false;

    @Column(name = "winner_one_hp_remaining", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private Boolean winnerOneHpRemaining = false;
}
