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
        name = "combat_replay_house_ability_use",
        indexes = {@Index(name = "idx_replay_house_ability_use_house", columnList = "house")},
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_replay_house_ability_use_candidate_house",
                    columnNames = {"candidate_id", "house"})
        })
/** Records the single house ability use allowed for one replay candidate. */
public class CombatReplayHouseAbilityUseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "candidate_id", nullable = false)
    private Long candidateId;

    @Enumerated(EnumType.STRING)
    @Column(name = "house", nullable = false)
    private CombatReplayHouse house;

    @Column(name = "favor_cost", nullable = false)
    private Integer favorCost;

    @Column(name = "discord_user_id", nullable = false)
    private String discordUserId;

    @Column(name = "discord_user_name", nullable = false)
    private String discordUserName;

    @Column(name = "used_at", nullable = false)
    private LocalDateTime usedAt;
}
