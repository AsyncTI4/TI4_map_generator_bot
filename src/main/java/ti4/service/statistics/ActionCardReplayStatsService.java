package ti4.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import ti4.game.Game;
import ti4.game.GameStats.ActionCardPlay;
import ti4.helpers.StringHelper;

class ActionCardReplayStatsService {

    private final Map<String, ReplayCount> replaysPerCard = new HashMap<>();
    private final List<Set<String>> replayedCardsPerGame = new ArrayList<>();

    void accumulate(Game game) {
        Map<String, Integer> playsPerCard = new HashMap<>();
        for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
            playsPerCard.merge(actionCardPlay.getActionCard(), 1, Integer::sum);
        }
        playsPerCard.forEach((cardName, plays) ->
                replaysPerCard.computeIfAbsent(cardName, _ -> new ReplayCount()).record(plays));
        replayedCardsPerGame.add(playsPerCard.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet()));
    }

    void appendTo(List<String> blocks, Map<String, Integer> copiesPerName) {
        Set<String> oneOfs = copiesPerName.entrySet().stream()
                .filter(entry -> entry.getValue() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());

        StringBuilder heading = new StringBuilder();
        heading.append("### Replays of 1-ofs\n");
        heading.append("_Every play of a 1-of after its first in the same game is a replay, whether the card was pulled"
                + " back out of the discard or drawn again after a reshuffle. Canceled plays count, since the card"
                + " still left the hand. Same sample of games as the Impact Score._\n");

        List<Map.Entry<String, ReplayCount>> oneOfCounts = replaysPerCard.entrySet().stream()
                .filter(entry -> oneOfs.contains(entry.getKey()))
                .toList();
        if (oneOfCounts.isEmpty()) {
            heading.append("No 1-of plays matched the selected filters.\n");
            blocks.add(heading.toString());
            return;
        }

        int totalGames = replayedCardsPerGame.size();
        ReplayCount allOneOfs = new ReplayCount();
        oneOfCounts.forEach(entry -> {
            allOneOfs.plays += entry.getValue().plays;
            allOneOfs.replays += entry.getValue().replays;
        });
        allOneOfs.gamesReplayed = (int) replayedCardsPerGame.stream()
                .filter(replayedCards -> replayedCards.stream().anyMatch(oneOfs::contains))
                .count();
        heading.append("- **All 1-ofs:** ")
                .append(describe(allOneOfs, totalGames, "with a 1-of replayed in"))
                .append('\n');
        blocks.add(heading.toString());

        List<Map.Entry<String, ReplayCount>> replayed = oneOfCounts.stream()
                .filter(entry -> entry.getValue().replays > 0)
                .sorted(Comparator.comparingInt(
                                (Map.Entry<String, ReplayCount> entry) -> entry.getValue().gamesReplayed)
                        .reversed()
                        .thenComparing(entry -> entry.getValue().replays, Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .toList();
        replayed.forEach(entry -> blocks.add(
                "- " + entry.getKey() + ": " + describe(entry.getValue(), totalGames, "replayed in") + '\n'));

        int neverReplayed = oneOfs.size() - replayed.size();
        if (neverReplayed > 0) {
            blocks.add("_" + StringHelper.pluralize(neverReplayed, "other 1-of") + " never replayed._\n");
        }
    }

    private static String describe(ReplayCount count, int totalGames, String gamesLabel) {
        return StringHelper.pluralize(count.replays, "replay")
                + " ("
                + ActionCardStatsService.formatPercent(count.plays == 0 ? 0 : count.replays / (double) count.plays)
                + " of "
                + StringHelper.pluralize(count.plays, "play")
                + "), "
                + gamesLabel
                + " "
                + ActionCardStatsService.formatPercent(totalGames == 0 ? 0 : count.gamesReplayed / (double) totalGames)
                + " of games ("
                + count.gamesReplayed
                + "/"
                + totalGames
                + ")";
    }

    private static class ReplayCount {
        private int plays;
        private int replays;
        private int gamesReplayed;

        void record(int playsThisGame) {
            plays += playsThisGame;
            if (playsThisGame > 1) {
                replays += playsThisGame - 1;
                gamesReplayed++;
            }
        }
    }
}
