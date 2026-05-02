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
        name = "combat_replay_house_ability_vote",
        indexes = {@Index(name = "idx_house_ability_vote_candidate_house", columnList = "candidate_id, house")},
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_house_ability_vote_candidate_house_user",
                    columnNames = {"candidate_id", "house", "discord_user_id"})
        })
public class CombatReplayHouseAbilityVoteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "house", nullable = false)
    private CombatReplayHouse house;

    @Column(name = "option_key", nullable = false)
    private String optionKey;

    @Column(name = "discord_user_id", nullable = false)
    private String discordUserId;

    @Column(name = "discord_user_name", nullable = false)
    private String discordUserName;

    @Column(name = "voted_at", nullable = false)
    private LocalDateTime votedAt;
}
