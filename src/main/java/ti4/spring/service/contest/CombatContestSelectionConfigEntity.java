package ti4.spring.service.contest;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "combat_predictor_selection_config")
public class CombatContestSelectionConfigEntity {

    @Id
    @Column(nullable = false)
    private Integer id;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "selection_mode", nullable = false)
    private String selectionMode;

    @Column(name = "lookback_minutes", nullable = false)
    private Integer lookbackMinutes;

    @Column(name = "window_sample_count", nullable = false)
    private Integer windowSampleCount;

    @Column(name = "target_posts_per_hour", nullable = false)
    private Double targetPostsPerHour;

    @Column(name = "target_selection_fraction", nullable = false)
    private Double targetSelectionFraction;

    @Column(name = "score_cutoff", nullable = false)
    private Double scoreCutoff;

    @Column(name = "strength_scale", nullable = false)
    private Double strengthScale;

    @Column(name = "hp_scale", nullable = false)
    private Double hpScale;

    @Column(name = "weaker_strength_weight", nullable = false)
    private Double weakerStrengthWeight;

    @Column(name = "weaker_hp_weight", nullable = false)
    private Double weakerHpWeight;

    @Column(name = "fairness_weight", nullable = false)
    private Double fairnessWeight;

    @Column(name = "cooldown_minutes", nullable = false)
    private Integer cooldownMinutes;

    @Column(name = "minimum_weaker_strength", nullable = false)
    private Double minimumWeakerStrength;

    @Column(name = "minimum_sample_count", nullable = false)
    private Integer minimumSampleCount;
}
