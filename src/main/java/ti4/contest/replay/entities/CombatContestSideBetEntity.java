package ti4.contest.replay.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.persistence.CombatSideBetTypeConverter;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "combat_contest_side_bets",
        indexes = {
            @Index(name = "idx_side_bet_contest_user", columnList = "contest_id, discord_user_id"),
            @Index(name = "idx_side_bet_resolved", columnList = "resolved_at")
        })
public class CombatContestSideBetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "discord_user_id", nullable = false)
    private String discordUserId;

    @Column(name = "discord_user_name", nullable = false)
    private String discordUserName;

    @Convert(converter = CombatSideBetTypeConverter.class)
    @Column(name = "bet_type", nullable = false, columnDefinition = "TEXT")
    private CombatSideBetType betType;

    @Column(name = "target_faction", nullable = false)
    private String targetFaction;

    @Column(name = "offered_profit_points", nullable = false)
    private Integer offeredProfitPoints;

    @Column(name = "placed_at", nullable = false)
    private LocalDateTime placedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
