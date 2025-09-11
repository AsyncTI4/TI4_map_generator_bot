package ti4.spring.service.tournamentwinner;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Entity
@Table(name = "tournament_winner")
class TournamentWinner {

    TournamentWinner(String userId, String userName, String tourneyName) {
        this.userId = userId;
        this.userName = userName;
        this.tourneyName = tourneyName;
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String userId;
    private String userName;
    private String tourneyName;
}
