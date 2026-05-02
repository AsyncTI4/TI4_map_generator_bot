package ti4.contest.replay.entities;

import jakarta.persistence.Column;
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

@Data
@NoArgsConstructor
@Entity
@Table(name = "combat_replay_hacan_market_compact_decision", indexes = @Index(name = "idx_hacan_market_compact_decision_contest", columnList = "contest_id"), uniqueConstraints = @UniqueConstraint(name = "uk_hacan_market_compact_decision_contest", columnNames = "contest_id"))
public class CombatReplayHacanMarketCompactDecisionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "decided_at", nullable = false)
    private LocalDateTime decidedAt;
}
