package ti4.spring.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
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
@Table(name = "player", uniqueConstraints = @UniqueConstraint(columnNames = {"game_name", "user_id"}))
public class PlayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_name", nullable = false)
    private GameEntity game;

    @Column(name = "faction_name")
    private String factionName;

    @Column(name = "score")
    private int score;

    @Column(name = "total_number_of_turns")
    private int totalNumberOfTurns;

    @Column(name = "total_turn_time")
    private long totalTurnTime;

    @Column(name = "expected_hits")
    private double expectedHits;

    @Column(name = "actual_hits")
    private int actualHits;

    @Column(name = "is_eliminated")
    private boolean eliminated;

    @Column(name = "is_winner")
    private boolean winner;

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PlayerEntity that = (PlayerEntity) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
