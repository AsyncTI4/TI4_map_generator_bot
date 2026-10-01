package ti4.service.statistics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.StringUtils;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.Player;
import ti4.helpers.StringHelper;

class ActionCardReplayStatsService {

    // A reshuffle sends the whole discard pile back into the deck, so a game that reshuffled ends with
    // most of the cards it played missing from the discard. Without one, the only played cards missing
    // are the few an ability pulled back out and kept, so most of them are still there.
    static final double RESHUFFLE_DISCARD_SHARE = 0.7;

    // Below this the share swings past the line on a single card pulled back out of the discard, and a
    // game this short is nowhere near drawing through the deck anyway.
    static final int MIN_ONE_OFS_FOR_RESHUFFLE_CHECK = 10;

    private final Map<String, String> cardNamesById;
    private final Set<String> oneOfs;
    private final Map<String, ReplayCount> replaysPerCard = new HashMap<>();
    private final List<Set<String>> replayedCardsPerGame = new ArrayList<>();
    private int gamesCheckedForReshuffle;
    private int gamesLikelyReshuffled;
    private int replaysInLikelyReshuffledGames;

    // A replay is credited to the faction that made it. The win split is counted per game, the unit
    // Overrule by faction uses, so a faction replaying twice in one game is still one game with a replay.
    private final Map<String, Integer> gamesPerFaction = new HashMap<>();
    private final Map<String, Integer> oneOfPlaysPerFaction = new HashMap<>();
    private final Map<String, Integer> replaysPerFaction = new HashMap<>();
    private final Map<String, Integer> replayedGames = new HashMap<>();
    private final Map<String, Integer> replayedWins = new HashMap<>();
    private final Map<String, Integer> notReplayedGames = new HashMap<>();
    private final Map<String, Integer> notReplayedWins = new HashMap<>();

    /**
     * @param cardNamesById every card ID in the deck, mapped to its name. Plays are recorded by name
     *     and the discard pile by ID, and a name with one ID is a 1-of.
     */
    ActionCardReplayStatsService(Map<String, String> cardNamesById) {
        this.cardNamesById = cardNamesById;
        Map<String, Integer> copiesPerName = new HashMap<>();
        cardNamesById.values().forEach(name -> copiesPerName.merge(name, 1, Integer::sum));
        oneOfs = copiesPerName.entrySet().stream()
                .filter(entry -> entry.getValue() == 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    void accumulate(Game game, Player winner) {
        String winningPlayerId = GameStats.getTrackedPlayerId(winner);
        // Built the way the player sections build their table, so the faction game counts match theirs.
        Map<String, String> factionsByPlayerId = new HashMap<>();
        for (Player player : game.getRealAndEliminatedPlayers()) {
            String playerId = GameStats.getTrackedPlayerId(player);
            if (StringUtils.isNotBlank(playerId) && StringUtils.isNotBlank(player.getFaction())) {
                factionsByPlayerId.put(playerId, player.getFaction());
            }
        }

        Map<String, Integer> playsPerCard = new HashMap<>();
        Map<String, Integer> oneOfPlaysPerPlayer = new HashMap<>();
        Map<String, Integer> replaysPerPlayer = new HashMap<>();
        for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
            String cardName = actionCardPlay.getActionCard();
            boolean replay = playsPerCard.merge(cardName, 1, Integer::sum) > 1;
            String playerId = actionCardPlay.getPlayerId();
            // Canceled plays count here, as they do for the replay totals: the card still left the hand.
            if (oneOfs.contains(cardName) && factionsByPlayerId.containsKey(playerId)) {
                oneOfPlaysPerPlayer.merge(playerId, 1, Integer::sum);
                if (replay) {
                    replaysPerPlayer.merge(playerId, 1, Integer::sum);
                }
            }
            // Only an uncanceled play with a known player can be weighed against the win - the same
            // plays the Impact Score's win rate counts, so the two halves add back up to it.
            if (actionCardPlay.isCanceled() || StringUtils.isBlank(playerId)) {
                continue;
            }
            ReplayCount count = replaysPerCard.computeIfAbsent(cardName, _ -> new ReplayCount());
            (replay ? count.replayWins : count.firstPlayWins).record(playerId.equals(winningPlayerId));
        }
        playsPerCard.forEach((cardName, plays) ->
                replaysPerCard.computeIfAbsent(cardName, _ -> new ReplayCount()).record(plays));
        replayedCardsPerGame.add(playsPerCard.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet()));
        checkForReshuffle(game, playsPerCard);
        factionsByPlayerId.forEach((playerId, faction) -> recordFaction(
                faction,
                oneOfPlaysPerPlayer.getOrDefault(playerId, 0),
                replaysPerPlayer.getOrDefault(playerId, 0),
                playerId.equals(winningPlayerId)));
    }

    private void recordFaction(String faction, int oneOfPlays, int replays, boolean won) {
        FactionStatisticsHelper.incrementFactionsIntValue(gamesPerFaction, faction);
        FactionStatisticsHelper.incrementFactionsIntValue(oneOfPlaysPerFaction, faction, oneOfPlays);
        FactionStatisticsHelper.incrementFactionsIntValue(replaysPerFaction, faction, replays);
        // Merged at zero on a loss, so every faction with a game on a side has a wins entry to read back.
        if (replays > 0) {
            FactionStatisticsHelper.incrementFactionsIntValue(replayedGames, faction);
            FactionStatisticsHelper.incrementFactionsIntValue(replayedWins, faction, won ? 1 : 0);
        } else {
            FactionStatisticsHelper.incrementFactionsIntValue(notReplayedGames, faction);
            FactionStatisticsHelper.incrementFactionsIntValue(notReplayedWins, faction, won ? 1 : 0);
        }
    }

    // Nothing records a reshuffle as it happens, so it is read off what the game left behind: the
    // share of the 1-ofs it played that are still in the discard pile at the end.
    private void checkForReshuffle(Game game, Map<String, Integer> playsPerCard) {
        Set<String> oneOfsPlayed = playsPerCard.keySet().stream()
                .filter(oneOfs::contains)
                .collect(Collectors.toSet());
        if (oneOfsPlayed.size() < MIN_ONE_OFS_FOR_RESHUFFLE_CHECK) {
            return;
        }
        gamesCheckedForReshuffle++;

        // Purged cards stay in this map under their status, and a reshuffle never takes them, so
        // they read as still in the discard - which is where a reshuffle would have left them.
        Set<String> namesInDiscard = game.getDiscardActionCards().keySet().stream()
                .map(cardId -> cardNamesById.getOrDefault(cardId, cardId))
                .collect(Collectors.toSet());
        long stillInDiscard = oneOfsPlayed.stream().filter(namesInDiscard::contains).count();
        if (stillInDiscard >= RESHUFFLE_DISCARD_SHARE * oneOfsPlayed.size()) {
            return;
        }
        gamesLikelyReshuffled++;
        playsPerCard.forEach((cardName, plays) -> {
            if (oneOfs.contains(cardName) && plays > 1) {
                replaysInLikelyReshuffledGames += plays - 1;
            }
        });
    }

    void appendTo(List<String> blocks) {
        StringBuilder heading = new StringBuilder();
        heading.append("### Replays of 1-ofs\n");
        heading.append("_Every play of a 1-of after its first in the same game is a replay, whether the card was pulled"
                + " back out of the discard or drawn again after a reshuffle. Canceled plays count, since the card"
                + " still left the hand. Same sample of games as the Impact Score._\n");
        heading.append("_First-play and replay win rates count uncanceled plays only, the same plays as the Impact"
                + " Score's win rate, so the two add back up to it._\n");
        heading.append("_A reshuffle sends the discard pile back into the deck, so a game that ended with under ")
                .append(ActionCardStatsService.formatPercent(RESHUFFLE_DISCARD_SHARE))
                .append(" of the 1-ofs it played still in the discard most likely reshuffled. Games with fewer than ")
                .append(MIN_ONE_OFS_FOR_RESHUFFLE_CHECK)
                .append(" different 1-ofs played are too short to tell._\n");

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
            allOneOfs.firstPlayWins.add(entry.getValue().firstPlayWins);
            allOneOfs.replayWins.add(entry.getValue().replayWins);
        });
        allOneOfs.gamesReplayed = (int) replayedCardsPerGame.stream()
                .filter(replayedCards -> replayedCards.stream().anyMatch(oneOfs::contains))
                .count();
        heading.append("- **All 1-ofs:** ")
                .append(describe(allOneOfs, totalGames, "with a 1-of replayed in"))
                .append('\n');
        double reshuffledShare =
                gamesCheckedForReshuffle == 0 ? 0 : gamesLikelyReshuffled / (double) gamesCheckedForReshuffle;
        heading.append("- **Games that likely reshuffled:** ")
                .append(gamesLikelyReshuffled)
                .append(" of ")
                .append(gamesCheckedForReshuffle)
                .append(" checked (")
                .append(ActionCardStatsService.formatPercent(reshuffledShare))
                .append("), holding ")
                .append(StringHelper.pluralize(replaysInLikelyReshuffledGames, "replay"))
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

        appendFactionsTo(blocks);
    }

    private void appendFactionsTo(List<String> blocks) {
        StringBuilder heading = new StringBuilder();
        heading.append("### Replays by faction\n");
        heading.append("_Average 1-of replays per game for each faction, credited to whoever made the replay, then its"
                + " win rate in games where it replayed at least one 1-of against games where it didn't. Same sample"
                + " of games as above._\n");
        if (gamesPerFaction.isEmpty()) {
            heading.append("No tracked action card plays matched the selected filters.\n");
            blocks.add(heading.toString());
            return;
        }
        blocks.add(heading.toString());

        boolean[] labelsPending = {true};
        gamesPerFaction.keySet().stream()
                .sorted(Comparator.comparingDouble(this::getReplaysPerGame)
                        .reversed()
                        .thenComparing(Comparator.naturalOrder()))
                // Each row is its own block: the list runs to every faction in the sample, which is far
                // past what one Discord message holds.
                .forEach(faction -> {
                    blocks.add(renderFaction(faction, labelsPending[0]));
                    labelsPending[0] = false;
                });
    }

    private String renderFaction(String faction, boolean spellOutLabels) {
        int replays = replaysPerFaction.getOrDefault(faction, 0);
        int oneOfPlays = oneOfPlaysPerFaction.getOrDefault(faction, 0);
        StringBuilder row = new StringBuilder();
        row.append("- `")
                .append(StringUtils.leftPad(String.format("%.2f", getReplaysPerGame(faction)), 5))
                .append(" from ");
        ActionCardStatsService.appendCount(row, gamesPerFaction.getOrDefault(faction, 0), "game");
        row.append("` ")
                .append(FactionStatisticsHelper.getFactionEmoji(faction))
                .append(' ')
                .append(ActionCardPlayerStatsService.getFactionName(faction))
                .append(": ")
                .append(StringHelper.pluralize(replays, "replay"))
                .append(" (")
                .append(ActionCardStatsService.formatPercent(oneOfPlays == 0 ? 0 : replays / (double) oneOfPlays))
                .append(spellOutLabels ? " of its " : " of ")
                .append(StringHelper.pluralize(oneOfPlays, "1-of play"))
                .append("), ");
        ActionCardPlayerStatsService.appendFactionWinRate(row, replayedWins, replayedGames, faction);
        row.append(spellOutLabels ? " win rate in games it replayed a 1-of, " : " replayed, ");
        ActionCardPlayerStatsService.appendFactionWinRate(row, notReplayedWins, notReplayedGames, faction);
        row.append(spellOutLabels ? " when it didn't\n" : " not\n");
        return row.toString();
    }

    private double getReplaysPerGame(String faction) {
        int games = gamesPerFaction.getOrDefault(faction, 0);
        return games == 0 ? 0 : replaysPerFaction.getOrDefault(faction, 0) / (double) games;
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
                + "); first plays won "
                + describeWins(count.firstPlayWins)
                + ", replays won "
                + describeWins(count.replayWins);
    }

    // Written the way the Overrule-by-faction rows write a rate with nothing under it.
    private static String describeWins(WinCount count) {
        return (count.plays == 0 ? "-" : ActionCardStatsService.formatPercent(count.wins / (double) count.plays))
                + " ("
                + count.wins
                + "/"
                + count.plays
                + ")";
    }

    private static class ReplayCount {
        private int plays;
        private int replays;
        private int gamesReplayed;
        private final WinCount firstPlayWins = new WinCount();
        private final WinCount replayWins = new WinCount();

        void record(int playsThisGame) {
            plays += playsThisGame;
            if (playsThisGame > 1) {
                replays += playsThisGame - 1;
                gamesReplayed++;
            }
        }
    }

    private static class WinCount {
        private int plays;
        private int wins;

        void record(boolean won) {
            plays++;
            if (won) {
                wins++;
            }
        }

        void add(WinCount other) {
            plays += other.plays;
            wins += other.wins;
        }
    }
}
