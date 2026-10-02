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
@Table(
        name = "combat_double_or_bust",
        indexes = @Index(name = "idx_double_or_bust_contest_user", columnList = "contest_id, discord_user_id"),
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_double_or_bust_contest_user",
                        columnNames = {"contest_id", "discord_user_id"}))
public class CombatDoubleOrBustEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "discord_user_id", nullable = false)
    private String discordUserId;

    @Column(name = "discord_user_name", nullable = false)
    private String discordUserName;

    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
