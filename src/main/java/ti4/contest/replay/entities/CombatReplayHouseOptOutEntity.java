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
        name = "combat_replay_house_opt_out",
        indexes = @Index(name = "idx_replay_house_opt_out_house", columnList = "house"),
        uniqueConstraints = @UniqueConstraint(name = "uk_replay_house_opt_out_user", columnNames = "discord_user_id"))
/**
 * Stores the previous Lazax house for users who opted out, so opting back in restores the same delegation.
 */
public class CombatReplayHouseOptOutEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "discord_user_id", nullable = false)
    private String discordUserId;

    @Column(name = "discord_user_name", nullable = false)
    private String discordUserName;

    @Enumerated(EnumType.STRING)
    @Column(name = "house", nullable = false)
    private CombatReplayHouse house;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    @Column(name = "opted_out_at", nullable = false)
    private LocalDateTime optedOutAt;
}
