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
@Table(name = "combat_replay_hacan_trade_convoys", indexes = @Index(name = "idx_hacan_trade_convoys_contest", columnList = "contest_id"), uniqueConstraints = @UniqueConstraint(name = "uk_hacan_trade_convoys_contest", columnNames = "contest_id"))
public class CombatReplayHacanTradeConvoysEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_house")
    private CombatReplayHouse targetHouse;

    @Column(name = "favor_cost", nullable = false)
    private Integer favorCost;

    @Column(name = "prediction_bonus", nullable = false)
    private Integer predictionBonus;

    @Column(name = "vote_count", nullable = false)
    private Integer voteCount;

    @Column(name = "selected_at", nullable = false)
    private LocalDateTime selectedAt;
}
