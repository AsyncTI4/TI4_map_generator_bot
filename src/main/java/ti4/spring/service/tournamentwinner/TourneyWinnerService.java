package ti4.spring.service.tournamentwinner;

import java.util.List;
import lombok.AllArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Service;
import ti4.discord.JdaService;
import ti4.game.persistence.DatabasePersistenceGate;
import ti4.spring.context.SpringContext;

@AllArgsConstructor
@Service
public class TourneyWinnerService {

    private TournamentWinnerRepository tournamentWinnerRepository;
    private TournamentWinnerTitleService tournamentWinnerTitleService;

    public static TourneyWinnerService getBean() {
        return SpringContext.getBean(TourneyWinnerService.class);
    }

    public boolean exists(String userId) {
        if (DatabasePersistenceGate.isDisabled()) return false;
        return tournamentWinnerRepository.existsByUserId(userId);
    }

    public void add(String userId, String userName, String tourneyName) {
        if (DatabasePersistenceGate.isDisabled()) return;
        tournamentWinnerRepository.save(new TournamentWinner(userId, userName, tourneyName));
        tournamentWinnerTitleService.grant(userId, userName, tourneyName);
    }

    public void remove(String userId, String tourneyName) {
        if (DatabasePersistenceGate.isDisabled()) return;
        tournamentWinnerRepository.deleteByUserIdAndTourneyName(userId, tourneyName);
        tournamentWinnerTitleService.revoke(userId, tourneyName);
    }

    public int grantMissingWinnerTitles() {
        if (DatabasePersistenceGate.isDisabled()) return 0;
        int granted = 0;
        for (TournamentWinner winner : tournamentWinnerRepository.findAll()) {
            if (tournamentWinnerTitleService.grant(winner.getUserId(), winner.getUserName(), winner.getTourneyName())) {
                granted++;
            }
        }
        return granted;
    }

    public String allWinnersToString() {
        if (DatabasePersistenceGate.isDisabled())
            return "Tournament winner data is temporarily unavailable while database maintenance is in progress.";
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
