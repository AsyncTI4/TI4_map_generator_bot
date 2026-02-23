package ti4.spring.service.statistics;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import ti4.map.Game;
import ti4.map.Player;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "player")
class PlayerEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "user_id")
    private String userId;

    @Column(name = "stats_tracked_user_id")
    private String statsTrackedUserId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_name", nullable = false)
    private GameEntity game;

    @Column(name = "username")
    private String username;

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

    PlayerEntity(Game game, Player player) {
        setUserId(player.getUserID());
        setUsername(player.getUserName());
        setStatsTrackedUserId(player.getStatsTrackedUserID());

        setFactionName(player.getFaction());

        setScore(player.getTotalVictoryPoints());
        setTotalNumberOfTurns(player.getNumberOfTurns());
        setTotalTurnTime(player.getTotalTurnTime());
        setExpectedHits(player.getExpectedHits());
        setActualHits(player.getActualHits());
        setEliminated(player.isEliminated());
        setWinner(game.getWinners().contains(player));
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (other == null || getClass() != other.getClass()) return false;
        PlayerEntity that = (PlayerEntity) other;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
