package ti4.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.function.ToIntFunction;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.Player;
import ti4.image.Mapper;
import ti4.model.FactionModel;

/**
 * The action card report read down the players rather than across the cards: how a player's win
 * rate moves with the number of cards they played, and how many cards each faction gets through.
 *
 * <p>Stateful, unlike the rest of the report, because it accumulates two datasets that no single
 * map expresses - and {@link ActionCardStatsService} already carries as many loose maps through
 * its accumulation as one signature can hold.
 */
class ActionCardPlayerStatsService {

    // Everything at or above this collects into one row. Playing twenty-odd cards in a game is
    // rare enough that an exact row for each would be a column of rates off a handful of players.
    private static final int MAX_EXACT_CARDS_PLAYED = 15;

    private final NavigableMap<Integer, WinRateCount> playersByCardsPlayed = new TreeMap<>();
    private final Map<String, Integer> gamesPerFaction = new HashMap<>();
    private final Map<String, Integer> cardsPlayedPerFaction = new HashMap<>();

    void accumulate(Game game, Player winner) {
        // The 6-player filter counts this same list, so it is the one that gives six players a game.
        Map<String, Player> playersById = new LinkedHashMap<>();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            String playerId = GameStats.getTrackedPlayerId(player);
            if (StringUtils.isNotBlank(playerId)) {
                playersById.put(playerId, player);
            }
        }

        Map<String, Integer> cardsPlayedPerPlayer = new HashMap<>();
        playersById.keySet().forEach(playerId -> cardsPlayedPerPlayer.put(playerId, 0));
        for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
            // A canceled card was still spent, so it counts the same as one that resolved.
            String playerId = actionCardPlay.getPlayerId();
            if (StringUtils.isBlank(playerId)) {
                continue;
            }
            // A play by someone not at this table would otherwise invent a seventh player who
            // never wins, dragging every rate below it down.
            cardsPlayedPerPlayer.computeIfPresent(playerId, (id, cardsPlayed) -> cardsPlayed + 1);
        }

        String winningPlayerId = GameStats.getTrackedPlayerId(winner);
        playersById.forEach((playerId, player) -> {
            int cardsPlayed = cardsPlayedPerPlayer.get(playerId);
            // Seeded at zero above, so a player who played nothing lands in the 0 row rather than
            // dropping out of the denominator the rows below it are measured against.
            playersByCardsPlayed
                    .computeIfAbsent(Math.min(cardsPlayed, MAX_EXACT_CARDS_PLAYED), _ -> new WinRateCount())
                    .record(playerId.equals(winningPlayerId));
            recordFaction(player.getFaction(), cardsPlayed);
        });
    }

    private void recordFaction(String faction, int cardsPlayed) {
        if (StringUtils.isBlank(faction)) {
            return;
        }
        FactionStatisticsHelper.incrementFactionsIntValue(gamesPerFaction, faction);
        FactionStatisticsHelper.incrementFactionsIntValue(cardsPlayedPerFaction, faction, cardsPlayed);
    }

    void appendTo(List<String> blocks) {
        appendWinRateByCardsPlayed(blocks);
        appendCardsPlayedPerFaction(blocks);
    }

    private void appendWinRateByCardsPlayed(List<String> blocks) {
        StringBuilder heading = new StringBuilder();
        heading.append("\n**Win rate by cards played**\n");
        heading.append("_Only games started after ")
                .append(ActionCardStatsService.PLAYER_TRACKING_START_DATE)
                .append(", when we started tracking who played each card.");
        if (playersByCardsPlayed.isEmpty()) {
            heading.append("_\nNo tracked action card plays matched the selected filters.\n");
            blocks.add(heading.toString());
            return;
        }

        int totalPlayers = totalOf(WinRateCount::getPlayers);
        // Measured rather than assumed. One winner among six should put this at 16.7%, so anything
        // else on screen is the denominator itself telling us it has gone wrong.
        heading.append(" A player won ")
                .append(ActionCardStatsService.formatPercent(totalOf(WinRateCount::getWins) / (double) totalPlayers))
                .append(" of the time across this sample, which is the rate to read these against.")
                // Sabotage goes unemphasised: the note is italicised as a whole, and the usual
                // _Sabotage_ would close those italics early and leave the rest of it upright.
                .append(" A card canceled by a Sabotage still counts - it was spent either way._\n");
        blocks.add(heading.toString());

        boolean[] labelsPending = {true};
        playersByCardsPlayed.forEach((cardsPlayed, count) -> {
            StringBuilder row = new StringBuilder();
            row.append("- ");
            appendCardsPlayed(row, cardsPlayed);
            row.append(": ").append(ActionCardStatsService.formatPercent(count.getWinRate()));
            if (labelsPending[0]) {
                row.append(" win rate");
            }
            row.append(" (").append(count.getWins()).append('/').append(count.getPlayers());
            if (labelsPending[0]) {
                row.append(" players");
            }
            // How common this many cards is, so a striking rate can be read against how rarely
            // anyone gets there.
            row.append("; ").append(ActionCardStatsService.formatPercent(count.getPlayers() / (double) totalPlayers));
            if (labelsPending[0]) {
                row.append(" of all players");
                labelsPending[0] = false;
            }
            row.append(")\n");
            blocks.add(row.toString());
        });
    }

    private static void appendCardsPlayed(StringBuilder row, int cardsPlayed) {
        if (cardsPlayed >= MAX_EXACT_CARDS_PLAYED) {
            row.append(cardsPlayed).append("+ cards");
            return;
        }
        ActionCardStatsService.appendCount(row, cardsPlayed, "card");
    }

    private int totalOf(ToIntFunction<WinRateCount> figure) {
        return playersByCardsPlayed.values().stream().mapToInt(figure).sum();
    }

    private void appendCardsPlayedPerFaction(List<String> blocks) {
        StringBuilder heading = new StringBuilder();
        heading.append("\n**Cards played per faction**\n");
        heading.append("_Average action cards played per game, over the same sample of games as above._\n");
        if (gamesPerFaction.isEmpty()) {
            heading.append("No tracked action card plays matched the selected filters.\n");
            blocks.add(heading.toString());
            return;
        }
        blocks.add(heading.toString());

        gamesPerFaction.keySet().stream()
                .sorted(Comparator.comparingDouble((String faction) -> getAverageCardsPlayed(faction))
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                // Each row is its own block: the list runs to every faction in the sample, which
                // is far past what one Discord message holds.
                .forEach(faction -> blocks.add(renderFaction(faction)));
    }

    private String renderFaction(String faction) {
        StringBuilder row = new StringBuilder();
        // The game count rides along on every row, so a faction with a thin sample shows itself
        // as one without needing a cutoff that would hide it entirely.
        row.append("- `")
                .append(StringUtils.leftPad(String.format("%.2f", getAverageCardsPlayed(faction)), 5))
                .append(" from ");
        ActionCardStatsService.appendCount(row, gamesPerFaction.getOrDefault(faction, 0), "game");
        return row.append("` ")
                .append(FactionStatisticsHelper.getFactionEmoji(faction))
                .append(' ')
                .append(getFactionName(faction))
                .append('\n')
                .toString();
    }

    private static String getFactionName(String faction) {
        FactionModel factionModel = Mapper.getFaction(faction);
        // The combined Obsidian + Firmament tally is a label of its own with no model behind it.
        return factionModel != null ? factionModel.getFactionNameWithSourceEmoji() : faction;
    }

    double getAverageCardsPlayed(String faction) {
        int games = gamesPerFaction.getOrDefault(faction, 0);
        return games == 0 ? 0 : cardsPlayedPerFaction.getOrDefault(faction, 0) / (double) games;
    }

    int getGames(String faction) {
        return gamesPerFaction.getOrDefault(faction, 0);
    }

    int getPlayers(int cardsPlayed) {
        WinRateCount count = playersByCardsPlayed.get(cardsPlayed);
        return count == null ? 0 : count.getPlayers();
    }

    int getWins(int cardsPlayed) {
        WinRateCount count = playersByCardsPlayed.get(cardsPlayed);
        return count == null ? 0 : count.getWins();
    }

    @Getter
    private static class WinRateCount {
        private int players;
        private int wins;

        void record(boolean won) {
            players++;
            if (won) {
                wins++;
            }
        }

        double getWinRate() {
            return players == 0 ? 0 : (double) wins / players;
        }
    }
}
