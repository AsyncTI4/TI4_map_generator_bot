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

    // Rows are pooled three counts wide. A row per exact count splits the sample thin enough that
    // neighboring rows swing on a few players each, which hides the trend running through them.
    private static final int BAND_SIZE = 3;

    // The last band is open-ended: past here the sample thins out fast, and how far past hardly
    // matters next to the fact that the player got through most of a hand.
    private static final int LAST_BAND_START = 15;

    private final NavigableMap<Integer, WinRateCount> playersByCardsPlayed = new TreeMap<>();
    private final Map<String, Integer> gamesPerFaction = new HashMap<>();
    private final Map<String, Integer> cardsPlayedPerFaction = new HashMap<>();

    // Kept apart from the rows above, which collapse everything at the cap into one key and so
    // cannot be summed back into a true total.
    private int totalCardsPlayed;

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
            // Seeded at zero above, so a player who played nothing lands in the first band rather
            // than dropping out of the denominator the rows below it are measured against.
            playersByCardsPlayed
                    .computeIfAbsent(bandOf(cardsPlayed), _ -> new WinRateCount())
                    .record(playerId.equals(winningPlayerId));
            totalCardsPlayed += cardsPlayed;
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

        heading.append(", counting those canceled._\n")
                .append("- The average win rate is ")
                .append(ActionCardStatsService.formatPercent(totalOf(WinRateCount::getWins) / (double) totalPlayers))
                .append(". The average number of action cards played is ")
                .append(String.format("%.2f", totalCardsPlayed / (double) totalPlayers))
                .append(".\n");
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

    // The band a count falls in, named by where it starts, so the rows sort in the order they read.
    private static int bandOf(int cardsPlayed) {
        return cardsPlayed >= LAST_BAND_START ? LAST_BAND_START : cardsPlayed / BAND_SIZE * BAND_SIZE;
    }

    private static void appendCardsPlayed(StringBuilder row, int band) {
        if (band >= LAST_BAND_START) {
            row.append(band).append("+ cards");
            return;
        }
        row.append(band).append('-').append(band + BAND_SIZE - 1).append(" cards");
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

    private double getAverageCardsPlayed(String faction) {
        int games = gamesPerFaction.getOrDefault(faction, 0);
        return games == 0 ? 0 : cardsPlayedPerFaction.getOrDefault(faction, 0) / (double) games;
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
