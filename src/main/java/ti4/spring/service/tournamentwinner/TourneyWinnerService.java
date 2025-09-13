package ti4.spring.service.tournamentwinner;

import java.util.List;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Service;
import ti4.AsyncTI4DiscordBot;

@AllArgsConstructor
@Service
public class TourneyWinnerService {

    private TournamentWinnerRepository tournamentWinnerRepository;

    public boolean exists(String userId) {
        return tournamentWinnerRepository.existsByUserId(userId);
    }

    public void add(String userId, String userName, String tourneyName) {
        tournamentWinnerRepository.save(new TournamentWinner(userId, userName, tourneyName));
    }

    public void remove(String userId, String tourneyName) {
        tournamentWinnerRepository.deleteByUserIdAndTourneyName(userId, tourneyName);
    }

    public String allWinnersToString() {
        StringBuilder sb = new StringBuilder("__**All Async TI4 Tournament Winners:**__");
        List<TournamentWinner> winners = tournamentWinnerRepository.findAll();
        for (TournamentWinner winner : winners) {
            User user = AsyncTI4DiscordBot.jda.getUserById(winner.getUserId());
            String name = user != null ? user.getEffectiveName() : winner.getUserName();
            sb.append("\n> ").append(name).append(" won ").append(winner.getTourneyName());
        }
        return sb.toString();
    }
}
