package ti4.contest.replay.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import ti4.contest.replay.core.CombatSideBetType;
import ti4.contest.replay.persistence.CombatSideBetTypeConverter;

@Data
@NoArgsConstructor
@Entity
@Table(
        name = "combat_replay_hacan_subsidy_vote",
        indexes = {
            @Index(name = "idx_hacan_subsidy_vote_contest", columnList = "contest_id"),
            @Index(name = "idx_hacan_subsidy_vote_user", columnList = "contest_id, discord_user_id")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_hacan_subsidy_vote_user_bet",
                    columnNames = {"contest_id", "discord_user_id", "bet_type", "target_faction"})
        })
public class CombatReplayHacanSubsidyVoteEntity {

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

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;
}
