package ti4.spring.service.statistics.overrule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "overrule_play")
public class OverrulePlayEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    // Nullable so legacy plays that never recorded a playerId (e.g. migrated placeholders) still fit.
    @Column(name = "player_id")
    private String playerId;

    @Column(name = "strategy_card", nullable = false)
    private String strategyCard;

    public OverrulePlayEntity(String gameName, String playerId, String strategyCard) {
        this.gameName = gameName;
        this.playerId = playerId;
        this.strategyCard = strategyCard;
    }
}
