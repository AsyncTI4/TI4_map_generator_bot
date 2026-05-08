package ti4.spring.service.tournamentwinner;

import java.util.List;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;
import ti4.service.persistence.SqlitePersistenceGate;

@AllArgsConstructor
@Service
public class TourneyWinnerService {

    private TournamentWinnerRepository tournamentWinnerRepository;

    public boolean exists(String userId) {
        if (SqlitePersistenceGate.isDisabled()) return false;
        return tournamentWinnerRepository.existsByUserId(userId);
    }

    public void add(String userId, String userName, String tourneyName) {
        if (SqlitePersistenceGate.isDisabled()) return;
        tournamentWinnerRepository.save(new TournamentWinner(userId, userName, tourneyName));
    }

    public void remove(String userId, String tourneyName) {
        if (SqlitePersistenceGate.isDisabled()) return;
        tournamentWinnerRepository.deleteByUserIdAndTourneyName(userId, tourneyName);
    }

    public String allWinnersToString() {
        if (SqlitePersistenceGate.isDisabled())
            return "Tournament winner data is unavailable while SQLite persistence is disabled.";
        StringBuilder sb = new StringBuilder("__**All Async TI4 Tournament Winners:**__");
        List<TournamentWinner> winners = tournamentWinnerRepository.findAll();
        for (TournamentWinner winner : winners) {
            User user = JdaService.jda.getUserById(winner.getUserId());
            String name = user != null ? user.getEffectiveName() : winner.getUserName();
            sb.append("\n> ").append(name).append(" won ").append(winner.getTourneyName());
        }
        return sb.toString();
    }
}
