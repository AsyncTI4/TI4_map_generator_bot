package ti4.spring.service.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "matchmaking_rating",
        indexes = @Index(name = "idx_matchmaking_rating_user", columnList = "user_id"),
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_matchmaking_rating_user_tigl",
                        columnNames = {"user_id", "is_tigl_only"}))
public class MatchmakingRatingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "username")
    private String username;

    @Column(name = "is_tigl_only", nullable = false)
    private boolean tiglOnly;

    @Column(name = "mean_rating", nullable = false)
    private double meanRating;

    @Column(name = "conservative_rating", nullable = false)
    private double conservativeRating;

    @Column(name = "sigma", nullable = false)
    private double sigma;

    @Column(name = "calibration_percent", nullable = false)
    private double calibrationPercent;

    @Column(name = "last_game_ended_epoch_milliseconds", nullable = false)
    private long lastGameEndedEpochMilliseconds;

    @Column(name = "recent_mean_delta")
    private Double recentMeanDelta;

    @Column(name = "recent_conservative_delta")
    private Double recentConservativeDelta;

    @Column(name = "calculated_epoch_milliseconds", nullable = false)
    private long calculatedEpochMilliseconds;
}
