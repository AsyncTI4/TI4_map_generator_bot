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
        name = "combat_replay_hacan_subsidy",
        indexes = {@Index(name = "idx_hacan_subsidy_contest", columnList = "contest_id")},
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_hacan_subsidy_contest_bet",
                    columnNames = {"contest_id", "bet_type", "target_faction"})
        })
public class CombatReplayHacanSubsidyEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Convert(converter = CombatSideBetTypeConverter.class)
    @Column(name = "bet_type", nullable = false, columnDefinition = "TEXT")
    private CombatSideBetType betType;

    @Column(name = "target_faction", nullable = false)
    private String targetFaction;

    @Column(name = "vote_count", nullable = false)
    private Integer voteCount;

    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt;
}
