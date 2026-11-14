package ti4.service.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import lombok.experimental.UtilityClass;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.persistence.GameManager;
import ti4.game.persistence.ManagedGame;
import ti4.spring.service.tournament.Tournament;
import ti4.spring.service.tournament.TournamentEntry;
import ti4.spring.service.tournament.TournamentService;

@UtilityClass
public class TournamentResults {

    public record Standing(String userId, int victoryPoints) {}

    public record GameResult(
            int group, String gameName, boolean ended, List<String> winners, List<Standing> standings) {}

    public record Advancement(
            List<String> winners, List<String> wildcards, List<String> tiedAtCut, List<String> unfinishedGames) {

        public List<String> advancing() {
            List<String> all = new ArrayList<>(winners);
            all.addAll(wildcards);
            return all;
        }
    }

    public record Progress(int finished, int total) {}

    public static Progress progress(List<TournamentEntry> entries) {
        List<String> games = gameNames(entries);
        int finished = (int) games.stream()
                .filter(GameManager::isValid)
                .map(GameManager::getManagedGame)
                .filter(ManagedGame::isHasEnded)
                .count();
        return new Progress(finished, games.size());
    }

    public static List<GameResult> results(long tournamentId) {
        List<GameResult> results = new ArrayList<>();
        Map<Integer, List<TournamentEntry>> groups =
                TournamentService.groups(TournamentService.getBean().entries(tournamentId));
        groups.forEach((group, members) -> {
            String gameName = members.getFirst().getGameName();
            if (gameName == null || !GameManager.isValid(gameName)) return;
            results.add(
                    result(group, gameName, GameManager.getManagedGame(gameName).getGame()));
        });
        return results;
    }

    static GameResult result(int group, String gameName, Game game) {
        List<Standing> standings = game.getRealAndEliminatedPlayers().stream()
                .map(player -> new Standing(player.getUserID(), player.getTotalVictoryPoints()))
                .sorted(Comparator.comparingInt(Standing::victoryPoints).reversed())
                .toList();
        boolean ended = game.isHasEnded();
        List<String> winners = ended
                ? game.getWinnersOnceEnded().stream().map(Player::getUserID).toList()
                : List.of();
        return new GameResult(group, gameName, ended, winners, standings);
    }

    public static Advancement advance(List<GameResult> results, int advancingCount) {
        Set<String> winners = new LinkedHashSet<>();
        List<String> unfinished = new ArrayList<>();
        for (GameResult result : results) {
            if (result.ended()) winners.addAll(result.winners());
            else unfinished.add(result.gameName());
        }
        List<Standing> runnersUp = results.stream()
                .filter(GameResult::ended)
                .flatMap(result -> result.standings().stream())
                .filter(standing -> !winners.contains(standing.userId()))
                .sorted(Comparator.comparingInt(Standing::victoryPoints).reversed())
                .toList();
        int open = Math.max(0, advancingCount - winners.size());
        List<String> wildcards =
                runnersUp.stream().limit(open).map(Standing::userId).toList();
        List<String> tiedAtCut = new ArrayList<>();
        if (open > 0 && runnersUp.size() > open) {
            int cutPoints = runnersUp.get(open - 1).victoryPoints();
            if (runnersUp.get(open).victoryPoints() == cutPoints) {
                runnersUp.stream()
                        .filter(standing -> standing.victoryPoints() == cutPoints)
                        .map(Standing::userId)
                        .forEach(tiedAtCut::add);
            }
        }
        return new Advancement(new ArrayList<>(winners), wildcards, tiedAtCut, unfinished);
    }

    public static String text(List<GameResult> results) {
        List<String> lines = new ArrayList<>();
        for (GameResult result : results) {
            String standings = String.join(
                    ", ",
                    result.standings().stream()
                            .map(standing ->
                                    TournamentDiscord.userName(standing.userId()) + " " + standing.victoryPoints())
                            .toList());
            String status = result.ended()
                    ? "won by " + String.join(" & ", TournamentDiscord.userNames(result.winners()))
                    : "still running";
            lines.add("Group " + String.format("%02d", result.group()) + " [" + result.gameName() + "] " + status + ": "
                    + standings);
        }
        return String.join("\n", lines);
    }

    public static TournamentGrouper.Constraints constraints(Tournament tournament) {
        Long previous = tournament.getPreviousRoundId();
        if (previous == null) return TournamentGrouper.Constraints.none();
        Set<String> keepApart = new LinkedHashSet<>();
        if (tournament.isSpreadPreviousWinners()) {
            results(previous).stream().filter(GameResult::ended).forEach(result -> keepApart.addAll(result.winners()));
        }
        Map<String, Integer> previousGroups = new HashMap<>();
        if (tournament.isAvoidRematches()) {
            TournamentService.groups(TournamentService.getBean().entries(previous))
                    .forEach(
                            (group, members) -> members.forEach(entry -> previousGroups.put(entry.getUserId(), group)));
        }
        return new TournamentGrouper.Constraints(keepApart, previousGroups);
    }

    public static List<String> gameNames(List<TournamentEntry> entries) {
        return entries.stream()
                .map(TournamentEntry::getGameName)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
    }
}
