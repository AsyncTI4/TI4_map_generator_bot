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
        name = "combat_predictor_contest",
        indexes = {
            @Index(name = "idx_combat_contest_posted_at", columnList = "posted_at"),
            @Index(name = "idx_combat_contest_game_status", columnList = "game_name, status"),
            @Index(
                    name = "idx_combat_contest_lookup",
                    columnList = "game_name, tile_position, combat_type, status, posted_at")
        })
public class CombatContestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private CombatContestStatus status;

    @Column(name = "combat_type", nullable = false)
    private CombatContestType combatType;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "tile_position", nullable = false)
    private String tilePosition;

    @Column(name = "tile_representation", nullable = false, columnDefinition = "TEXT")
    private String tileRepresentation;

    @Column(name = "attacker_faction", nullable = false)
    private String attackerFaction;

    @Column(name = "defender_faction", nullable = false)
    private String defenderFaction;

    @Column(name = "attacker_color", nullable = false)
    private String attackerColor;

    @Column(name = "defender_color", nullable = false)
    private String defenderColor;

    @Column(name = "public_channel_id")
    private Long publicChannelId;

    @Column(name = "public_message_id")
    private Long publicMessageId;

    @Column(name = "public_thread_id")
    private Long publicThreadId;

    @Column(name = "source_combat_thread_id")
    private Long sourceCombatThreadId;

    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "leaderboard_posted_at")
    private LocalDateTime leaderboardPostedAt;

    @Column(name = "winner_faction")
    private String winnerFaction;

    @Column(name = "loser_faction")
    private String loserFaction;

    @Column(name = "initial_summary_text", nullable = false, columnDefinition = "TEXT")
    private String initialSummaryText;

    @Column(name = "active_player_summary", columnDefinition = "TEXT")
    private String activePlayerSummary;

    @Column(name = "initial_strength_attacker", nullable = false)
    private Double initialStrengthAttacker;

    @Column(name = "initial_strength_defender", nullable = false)
    private Double initialStrengthDefender;

    @Column(name = "initial_hp_attacker")
    private Double initialHpAttacker;

    @Column(name = "initial_hp_defender")
    private Double initialHpDefender;

    @Column(name = "dice_rolled")
    private Boolean diceRolled;

    @Column(name = "attacker_hit_assigned_round")
    private Integer attackerHitAssignedRound;

    @Column(name = "defender_hit_assigned_round")
    private Integer defenderHitAssignedRound;

    @Column(name = "last_posted_hit_image_round")
    private Integer lastPostedHitImageRound;
}
