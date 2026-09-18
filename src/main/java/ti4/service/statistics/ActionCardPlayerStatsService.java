package ti4.service.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
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
 * rate moves with the number of cards they played, how many cards each faction gets through, and how
 * often each faction plays Overrule and wins with it.
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

    // Counted per game rather than per play: a faction that plays Overrule twice in one game is
    // still one game with it, which is the unit its win rate is measured in.
    private final Map<String, Integer> overruleGamesPerFaction = new HashMap<>();
    private final Map<String, Integer> resolvedOverruleGames = new HashMap<>();
    private final Map<String, Integer> resolvedOverruleWins = new HashMap<>();
    private final Map<String, Integer> noOverruleGames = new HashMap<>();
    private final Map<String, Integer> noOverruleWins = new HashMap<>();

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
        Set<String> playedOverrule = new HashSet<>();
        Set<String> resolvedOverrule = new HashSet<>();
        for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
            // A canceled card was still spent, so it counts the same as one that resolved.
            String playerId = actionCardPlay.getPlayerId();
            // A play by someone not at this table would otherwise invent a seventh player who
            // never wins, dragging every rate below it down.
            if (StringUtils.isBlank(playerId) || !playersById.containsKey(playerId)) {
                continue;
            }
            cardsPlayedPerPlayer.computeIfPresent(playerId, (id, cardsPlayed) -> cardsPlayed + 1);
            if (GameStats.OVERRULE.equals(actionCardPlay.getActionCard())) {
                playedOverrule.add(playerId);
                if (!actionCardPlay.isCanceled()) {
                    resolvedOverrule.add(playerId);
                }
            }
        }

        String winningPlayerId = GameStats.getTrackedPlayerId(winner);
        playersById.forEach((playerId, player) -> {
            int cardsPlayed = cardsPlayedPerPlayer.get(playerId);
            boolean won = playerId.equals(winningPlayerId);
            // Seeded at zero above, so a player who played nothing lands in the first band rather
            // than dropping out of the denominator the rows below it are measured against.
            playersByCardsPlayed
                    .computeIfAbsent(bandOf(cardsPlayed), _ -> new WinRateCount())
                    .record(won);
            totalCardsPlayed += cardsPlayed;
            recordFaction(player.getFaction(), cardsPlayed);
            recordOverrule(
                    player.getFaction(), playedOverrule.contains(playerId), resolvedOverrule.contains(playerId), won);
        });
    }

    private void recordFaction(String faction, int cardsPlayed) {
        if (StringUtils.isBlank(faction)) {
            return;
        }
        FactionStatisticsHelper.incrementFactionsIntValue(gamesPerFaction, faction);
        FactionStatisticsHelper.incrementFactionsIntValue(cardsPlayedPerFaction, faction, cardsPlayed);
    }

    private void recordOverrule(String faction, boolean played, boolean resolved, boolean won) {
        if (StringUtils.isBlank(faction)) {
            return;
        }
        if (played) {
            FactionStatisticsHelper.incrementFactionsIntValue(overruleGamesPerFaction, faction);
        }
        // A game where every Overrule the faction played was canceled belongs to neither side: the
        // card never took effect, yet the faction did draw and spend it, unlike one that never had it.
        if (resolved) {
            recordWinRate(resolvedOverruleGames, resolvedOverruleWins, faction, won);
        } else if (!played) {
            recordWinRate(noOverruleGames, noOverruleWins, faction, won);
        }
    }

    private static void recordWinRate(
            Map<String, Integer> games, Map<String, Integer> wins, String faction, boolean won) {
        FactionStatisticsHelper.incrementFactionsIntValue(games, faction);
        // Merged at zero on a loss, so every faction with a game has a wins entry to read back.
        FactionStatisticsHelper.incrementFactionsIntValue(wins, faction, won ? 1 : 0);
    }

    void appendTo(List<String> blocks) {
        appendWinRateByCardsPlayed(blocks);
        appendCardsPlayedPerFaction(blocks);
        appendOverrulePerFaction(blocks);
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

    private void appendOverrulePerFaction(List<String> blocks) {
        StringBuilder heading = new StringBuilder();
        heading.append("\n**Overrule by faction**\n");
        heading.append("_How many of its games each faction played Overrule in, counting those canceled, then its win"
                + " rate in games where its Overrule resolved against games where it played none. A game where every"
                + " Overrule it played was canceled counts toward neither win rate. Same sample of games as above._\n");
        if (gamesPerFaction.isEmpty()) {
            heading.append("No tracked action card plays matched the selected filters.\n");
            blocks.add(heading.toString());
            return;
        }
        blocks.add(heading.toString());

        boolean[] labelsPending = {true};
        gamesPerFaction.keySet().stream()
                .sorted(Comparator.comparingDouble((String faction) -> getOverrulePlayRate(faction))
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                .forEach(faction -> {
                    blocks.add(renderOverruleFaction(faction, labelsPending[0]));
                    labelsPending[0] = false;
                });
    }

    private String renderOverruleFaction(String faction, boolean spellOutLabels) {
        StringBuilder row = new StringBuilder();
        row.append("- `")
                .append(StringUtils.leftPad(ActionCardStatsService.formatPercent(getOverrulePlayRate(faction)), 6))
                .append(" of ");
        ActionCardStatsService.appendCount(row, gamesPerFaction.getOrDefault(faction, 0), "game");
        row.append("` ")
                .append(FactionStatisticsHelper.getFactionEmoji(faction))
                .append(' ')
                .append(getFactionName(faction))
                .append(": ");
        appendFactionWinRate(row, resolvedOverruleWins, resolvedOverruleGames, faction);
        row.append(spellOutLabels ? " win rate with it resolved, " : " resolved, ");
        appendFactionWinRate(row, noOverruleWins, noOverruleGames, faction);
        row.append(spellOutLabels ? " without playing it\n" : " without\n");
        return row.toString();
    }

    private static void appendFactionWinRate(
            StringBuilder row,
            Map<String, Integer> winsPerFaction,
            Map<String, Integer> sidePerFaction,
            String faction) {
        int games = sidePerFaction.getOrDefault(faction, 0);
        int wins = winsPerFaction.getOrDefault(faction, 0);
        // No games on a side means no rate at all, which a 0% would misread as never winning.
        row.append(games == 0 ? "-" : ActionCardStatsService.formatPercent(wins / (double) games))
                .append(" (")
                .append(wins)
                .append('/')
                .append(games)
                .append(')');
    }

    private double getOverrulePlayRate(String faction) {
        int games = gamesPerFaction.getOrDefault(faction, 0);
        return games == 0 ? 0 : overruleGamesPerFaction.getOrDefault(faction, 0) / (double) games;
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
