package ti4.service.async;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import net.dv8tion.jda.api.entities.User;
import org.jetbrains.annotations.NotNull;
import ti4.AsyncTI4DiscordBot;
import ti4.json.PersistenceManager;
import ti4.message.BotLogger;

public class TourneyWinnersService {

    private static final String fileName = "tourneyWinners.json";
    private static List<TournamentWinner> winnerCache = null;

    public static void addTourneyWinner(User user, String tourneyName) {
        List<TournamentWinner> winners = readWinnerList();
        winners.add(new TournamentWinner(user, tourneyName));
        saveWinnerList(winners);
    }

    public static void removeTourneyWinner(User user, String tourneyName) {
        List<TournamentWinner> winners = readWinnerList();
        if (isPlayerWinner(user.getId())) {
            winners.removeIf(
                    w -> w.getId().equals(user.getId()) && w.getTourneyName().equals(tourneyName));
            saveWinnerList(winners);
        }
    }

    public static boolean isPlayerWinner(String userID) {
        return winnerIDs().contains(userID);
    }

    public static List<String> winnerIDs() {
        return readWinnerList().stream().map(TournamentWinner::getId).toList();
    }

    public static String tournamentWinnersOutputString() {
        StringBuilder sb = new StringBuilder("__**All Async TI4 Tournament Winners:**__");
        for (TournamentWinner w : readWinnerList()) {
            User winner = AsyncTI4DiscordBot.jda.getUserById(w.getId());
            String name = winner != null ? winner.getEffectiveName() : w.getName();
            sb.append("\n> ").append(name).append(" won ").append(w.getTourneyName());
        }
        return sb.toString();
    }

    private static List<TournamentWinner> readWinnerList() {
        if (winnerCache == null) {
            try {
                List<TournamentWinner> reserved =
                        PersistenceManager.readListFromJsonFile(fileName, TournamentWinner.class);
                winnerCache = reserved;
            } catch (Exception e) {
                BotLogger.error("Failed to read json data for Reserved Game Cache.", e);
                winnerCache = new ArrayList<>();
            }
            if (winnerCache == null) winnerCache = new ArrayList<>();
        }
        return winnerCache;
    }

    private static void saveWinnerList(List<TournamentWinner> reserved) {
        if (winnerCache == null) winnerCache = new ArrayList<>();
        try {
            PersistenceManager.writeObjectToJsonFile(fileName, reserved);
            winnerCache = reserved;
        } catch (Exception e) {
            BotLogger.error("Failed to write json data for Reserved Game Cache.", e);
        }
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TournamentWinner {
        private String name;
        private String id;
        private String tourneyName;

        public TournamentWinner(@NotNull User user, String tournament) {
            name = user.getEffectiveName();
            id = user.getId();
            tourneyName = tournament;
        }

        public String toString() {
            return name + " (" + id + ")";
        }
    }
}
