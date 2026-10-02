package ti4.spring.service.statistics.overrule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "overrule_choice", uniqueConstraints = @UniqueConstraint(columnNames = {"game_name", "strategy_card"}))
public class OverruleChoiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "game_name", nullable = false)
    private String gameName;

    @Column(name = "strategy_card", nullable = false)
    private String strategyCard;

    @Column(name = "count", nullable = false)
    private long count;

    public OverruleChoiceEntity(String gameName, String strategyCard, long count) {
        this.gameName = gameName;
        this.strategyCard = strategyCard;
        this.count = count;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        OverruleChoiceEntity that = (OverruleChoiceEntity) other;
        return Objects.equals(gameName, that.gameName) && Objects.equals(strategyCard, that.strategyCard);
    }

    @Override
    public int hashCode() {
        return Objects.hash(gameName, strategyCard);
    }
}
