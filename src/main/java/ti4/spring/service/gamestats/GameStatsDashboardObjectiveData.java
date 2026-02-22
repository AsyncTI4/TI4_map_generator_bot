package ti4.spring.service.gamestats;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "game_stats_dashboard_objective")
class GameStatsDashboardObjectiveData {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_name", nullable = false)
    private GameStatsDashboardPayloadData gameStats;

    @Column(name = "objective_category", nullable = false)
    private String category;

    @Column(name = "objective_name", nullable = false)
    private String objectiveName;

    GameStatsDashboardObjectiveData(GameStatsDashboardPayloadData gameStats, String category, String objectiveName) {
        this.gameStats = gameStats;
        this.category = category;
        this.objectiveName = objectiveName;
    }
}
