package ti4.spring.service.statistics.overrule;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * @deprecated legacy count-based row that stored one row per (game, strategy card) with no player
 *     attribution. Kept alive so {@link OverruleChoiceMigrationRunner} can fan the rows out into
 *     {@link OverrulePlayEntity} on startup. Delete this class, {@link OverruleChoiceRepository},
 *     and {@link OverruleChoiceMigrationRunner} once the migration has run and the {@code
 *     overrule_choice} table has been dropped from every environment.
 */
@Deprecated
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
}
