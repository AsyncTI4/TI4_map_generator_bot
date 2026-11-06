package ti4.spring.service.techsummary;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "game_tech_summary")
class TechSummaryEntity {

    @Id
    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "round_number", nullable = false)
    private int round;

    @Column(name = "faction_summaries_json", nullable = false, columnDefinition = "TEXT")
    private String factionSummariesJson;

    TechSummaryEntity(String gameName, int round, String factionSummariesJson) {
        this.gameName = gameName;
        this.round = round;
        this.factionSummariesJson = factionSummariesJson;
    }
}
