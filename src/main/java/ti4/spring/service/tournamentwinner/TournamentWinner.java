package ti4.spring.service.tournamentwinner;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tournament_winner")
class TournamentWinner {

    @Id
    private String userId;

    private String userName;
    private String tourneyName;
}
