package ti4.spring.service.tournament;

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

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "tournament_game_master",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tournament_id", "user_id"}))
public class TournamentGameMaster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    @Column(name = "tournament_id", nullable = false)
    private long tournamentId;

    @Column(name = "user_id", nullable = false)
    private String userId;

    TournamentGameMaster(long tournamentId, String userId) {
        this.tournamentId = tournamentId;
        this.userId = userId;
    }
}
