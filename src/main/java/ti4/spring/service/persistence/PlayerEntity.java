package ti4.spring.service.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
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
@Table(
        name = "player",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_player_game_playing_user",
                        columnNames = {"game_name", "playing_user_id"}))
public class PlayerEntity {

    static final String ID_SEQUENCE = "player_id_seq";
    static final int ID_ALLOCATION_SIZE = 50;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = ID_SEQUENCE)
    @SequenceGenerator(name = ID_SEQUENCE, sequenceName = ID_SEQUENCE, allocationSize = ID_ALLOCATION_SIZE)
    private long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private UserEntity statsUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playing_user_id")
    private UserEntity playingUser;

    @Column(name = "is_playing", nullable = false, columnDefinition = "BOOLEAN DEFAULT TRUE")
    private boolean playing = true;

    @Column(name = "is_npc", nullable = false, columnDefinition = "BOOLEAN DEFAULT FALSE")
    private boolean npc;

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

    @Column(name = "is_replaced")
    private boolean replaced;

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
