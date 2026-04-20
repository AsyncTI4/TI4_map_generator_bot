package ti4.spring.service.contest;

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
        name = "combat_predictor_prediction",
        indexes = {
            @Index(name = "idx_combat_prediction_contest_id", columnList = "contest_id"),
            @Index(name = "idx_combat_prediction_points_user", columnList = "points_awarded, discord_user_id")
        },
        uniqueConstraints = {
            @UniqueConstraint(
                    name = "uk_combat_prediction_contest_user",
                    columnNames = {"contest_id", "discord_user_id"})
        })
public class CombatContestPredictionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "contest_id", nullable = false)
    private Long contestId;

    @Column(name = "discord_user_id", nullable = false)
    private String discordUserId;

    @Column(name = "discord_user_name", nullable = false)
    private String discordUserName;

    @Column(name = "predicted_faction", nullable = false)
    private String predictedFaction;

    @Column(name = "locked_at", nullable = false)
    private LocalDateTime lockedAt;

    @Column(name = "is_correct")
    private Boolean correct;

    @Column(name = "points_awarded")
    private Integer pointsAwarded;
}
