package ti4.service.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.DeckModel;
import ti4.spring.service.statistics.overrule.OverruleStatsService;

@UtilityClass
public class ActionCardStatsService {
    private static final LocalDate PLAYER_TRACKING_START_DATE = LocalDate.of(2026, 5, 23);
    private static final String DEFAULT_AC_DECK_ID = "action_cards_te";
    private static final double CANCEL_WIN_EQUIVALENT = 0.2;

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showActionCardStats(event));
    }

    private static void showActionCardStats(SlashCommandInteractionEvent event) {
        DeckModel acDeck = Mapper.getDeck(DEFAULT_AC_DECK_ID);

        Map<String, Integer> cancelCounts = new HashMap<>();
        Map<String, Integer> actionCardsPlayedCounts = new HashMap<>();
        Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts = new HashMap<>();
        Map<String, Integer> unattributedPlayCounts = new HashMap<>();
        Set<String> includedGameNames = new HashSet<>();

        // A discarded card that isn't in the selected deck means the game is mislabeled (e.g. it
        // changed decks mid-game), which would pollute the stats with off-deck cards.
        Set<String> deckCardIds = new HashSet<>(acDeck.getCardIDs());

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getStandardCompetitiveGamesFilter()
                        .and(game -> DEFAULT_AC_DECK_ID.equals(game.getAcDeckID()))
                        .and(game -> deckCardIds.containsAll(
                                game.getDiscardActionCards().keySet())),
                game -> accumulateActionCardStats(
                        game,
                        cancelCounts,
                        actionCardsPlayedCounts,
                        playToWinCorrelationCounts,
                        unattributedPlayCounts,
                        includedGameNames),
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(
                event.getChannel(),
                "Action Card Play Statistics",
                buildMessage(
                        acDeck,
                        cancelCounts,
                        actionCardsPlayedCounts,
                        playToWinCorrelationCounts,
                        unattributedPlayCounts,
                        includedGameNames,
                        CommandHelper.hasRole(event, JdaService.developerRoles)));
    }

    private static void accumulateActionCardStats(
            Game game,
            Map<String, Integer> cancelCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, Integer> unattributedPlayCounts,
            Set<String> includedGameNames) {
        includedGameNames.add(game.getName());

        game.getDiscardActionCards()
                .forEach((acID, ignored) -> incrementActionCardPlayCount(actionCardsPlayedCounts, acID));

        List<ActionCardPlay> actionCardPlays = game.getGameStats().getActionCardPlays();
        for (ActionCardPlay actionCardPlay : actionCardPlays) {
            if (actionCardPlay.isCanceled()) {
                cancelCounts.merge(actionCardPlay.getActionCard(), 1, Integer::sum);
            }
        }

        Player winner = game.getWinner().orElse(null);
        if (winner == null) {
            return;
        }
        accumulateActionCardPlayToWinCorrelation(game, winner, playToWinCorrelationCounts, unattributedPlayCounts);
    }

    private static void incrementActionCardPlayCount(
            Map<String, Integer> actionCardsPlayedCounts, String actionCardId) {
        ActionCardModel actionCardModel = Mapper.getActionCard(actionCardId);
        String name = actionCardModel != null ? actionCardModel.getName() : actionCardId;
        actionCardsPlayedCounts.merge(name, 1, Integer::sum);
    }

    private static Map<String, Integer> getCopiesPerName(DeckModel acDeck) {
        Map<String, Integer> copiesPerName = new HashMap<>();
        for (String cardId : acDeck.getCardIDs()) {
            ActionCardModel actionCardModel = Mapper.getActionCard(cardId);
            String name = actionCardModel != null ? actionCardModel.getName() : cardId;
            copiesPerName.merge(name, 1, Integer::sum);
        }
        return copiesPerName;
    }

    // The most played card approximates one play per draw, so it stands in for how often it was
    // drawn. Every card of the same copy count shares that estimate; cards with a different copy
    // count are drawn proportionally more or less often, so the estimate scales with the copies.
    static Map<String, Integer> computeExpectedDraws(
            Map<String, Integer> playCounts, Map<String, Integer> copiesPerName) {
        Map<Integer, Integer> expectedDrawsPerCopyCount = computeExpectedDrawsPerCopyCount(playCounts, copiesPerName);

        Map<String, Integer> expectedDraws = new HashMap<>();
        copiesPerName.forEach((name, copies) -> {
            int draws = expectedDrawsPerCopyCount.getOrDefault(copies, 0);
            if (draws > 0) {
                expectedDraws.put(name, draws);
            }
        });
        return expectedDraws;
    }

    private static Map<Integer, Integer> computeExpectedDrawsPerCopyCount(
            Map<String, Integer> playCounts, Map<String, Integer> copiesPerName) {
        double drawsPerCopy = computeDrawsPerCopy(playCounts, copiesPerName);
        return copiesPerName.values().stream().distinct().collect(Collectors.toMap(copies -> copies, copies ->
                (int) Math.round(drawsPerCopy * copies)));
    }

    // Plays per copy in the deck puts every card on equal footing, so a 4-of leading the deck sets
    // the same per-copy estimate a 1-of would. Scaling that back up by a card's own copy count is
    // what turns one leader's play count into an expected draw count for the whole deck.
    private static double computeDrawsPerCopy(Map<String, Integer> playCounts, Map<String, Integer> copiesPerName) {
        double drawsPerCopy = 0;
        for (Map.Entry<String, Integer> entry : copiesPerName.entrySet()) {
            int copies = entry.getValue();
            if (copies > 0) {
                double playsPerCopy = playCounts.getOrDefault(entry.getKey(), 0) / (double) copies;
                drawsPerCopy = Math.max(drawsPerCopy, playsPerCopy);
            }
        }
        return drawsPerCopy;
    }

    private static String buildMessage(
            DeckModel acDeck,
            Map<String, Integer> cancelCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, Integer> unattributedPlayCounts,
            Set<String> includedGameNames,
            boolean includeDeveloperDebug) {
        Map<String, Integer> copiesPerName = getCopiesPerName(acDeck);
        Map<String, Integer> playedExpectedDraws = computeExpectedDraws(actionCardsPlayedCounts, copiesPerName);
        Map<String, Integer> playsIncludingCanceled = playToWinCorrelationCounts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().getPlaysIncludingCanceled()));
        Map<String, Integer> winCorrelationExpectedDraws = computeExpectedDraws(playsIncludingCanceled, copiesPerName);

        StringBuilder message = new StringBuilder();
        message.append(
                        "\n_6-player, 10-victory-point, non-fog, non-Galactic-Event, non-Scenario games with winners, using deck '")
                .append(acDeck.getName())
                .append("'._\n");
        message.append("\n**Action card plays, expected draws, and Sabotage/Cancels**\n");
        appendExpectedDrawsNote(message, computeExpectedDrawsPerCopyCount(actionCardsPlayedCounts, copiesPerName));
        appendActionCardPlayAndCancelStats(message, actionCardsPlayedCounts, cancelCounts, playedExpectedDraws);
        message.append("\n**Action card play-to-win correlation**\n");
        appendTrackingStartNote(message);
        appendExpectedDrawsNote(message, computeExpectedDrawsPerCopyCount(playsIncludingCanceled, copiesPerName));
        message.append(
                "_The Impact Score compares wins to expected draws. Impact Score Ω raises that score by 1/5th of a win for each cancel of the card._\n");
        appendPlayToWinCorrelationStats(message, playToWinCorrelationCounts, winCorrelationExpectedDraws);
        if (copiesPerName.containsKey(GameStats.OVERRULE)) {
            message.append("\n**Overrule targets**\n");
            appendTrackingStartNote(message);
            appendOverruleStats(message, OverruleStatsService.get().getCountPerStrategyCard(includedGameNames));
        }
        if (includeDeveloperDebug) {
            appendUnattributedPlayDebug(message, unattributedPlayCounts);
        }
        return message.toString();
    }

    // Plays we could not tie to a player, almost all of them cancels the legacy-save migration
    // reconstructed. Canceled ones still count toward plays and Impact Score Ω; uncancelled ones
    // are dropped, since they would otherwise feed a win rate with no winner to compare against.
    private static void appendUnattributedPlayDebug(
            StringBuilder message, Map<String, Integer> unattributedPlayCounts) {
        message.append("\n**Unattributed plays (developer debug)**\n");
        message.append("_Play-to-win correlation plays with no recorded player, per card._\n");
        if (unattributedPlayCounts.isEmpty()) {
            message.append("Every tracked play has a player.\n");
            return;
        }

        unattributedPlayCounts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> message.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append('\n'));
    }

    private static void appendTrackingStartNote(StringBuilder message) {
        message.append("_We started tracking these on ")
                .append(PLAYER_TRACKING_START_DATE)
                .append("._\n");
    }

    private static void appendExpectedDrawsNote(StringBuilder message, Map<Integer, Integer> maxPlaysPerCopyCount) {
        String perCopyCount = maxPlaysPerCopyCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Integer, Integer>comparingByKey().reversed())
                .map(entry -> entry.getValue() + " " + entry.getKey() + "-ofs")
                .collect(Collectors.joining(", "));
        if (perCopyCount.isEmpty()) {
            return;
        }
        message.append("_Expected draws: ").append(perCopyCount).append("._\n");
    }

    private static void appendActionCardPlayAndCancelStats(
            StringBuilder message,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> cancelCounts,
            Map<String, Integer> expectedDrawsPerCard) {
        if (actionCardsPlayedCounts.isEmpty() && cancelCounts.isEmpty()) {
            message.append("No action card play or cancel data matched the selected filters.\n");
            return;
        }

        Set<String> actionCardNames = new HashSet<>(actionCardsPlayedCounts.keySet());
        actionCardNames.addAll(cancelCounts.keySet());

        boolean[] drawsLabelPending = {true};
        boolean[] playsLabelPending = {true};
        actionCardNames.stream()
                .sorted(Comparator.comparingDouble((String actionCardName) -> getCancelRate(
                                actionCardsPlayedCounts.getOrDefault(actionCardName, 0),
                                cancelCounts.getOrDefault(actionCardName, 0)))
                        .reversed()
                        .thenComparing(
                                actionCardName -> actionCardsPlayedCounts.getOrDefault(actionCardName, 0),
                                Comparator.reverseOrder())
                        .thenComparing(actionCardName -> actionCardName))
                .forEach(actionCardName -> {
                    int playCount = actionCardsPlayedCounts.getOrDefault(actionCardName, 0);
                    int cancelCount = cancelCounts.getOrDefault(actionCardName, 0);
                    message.append("- ")
                            .append(actionCardName)
                            .append(": ")
                            .append(playCount)
                            .append(" plays");
                    Integer expectedDraws = expectedDrawsPerCard.get(actionCardName);
                    if (expectedDraws != null && expectedDraws > 0) {
                        message.append(" (").append(formatPercent(playCount / (double) expectedDraws));
                        if (drawsLabelPending[0]) {
                            message.append(" of ~draws");
                            drawsLabelPending[0] = false;
                        }
                        message.append(')');
                    }
                    message.append(", ")
                            .append(cancelCount)
                            .append(" cancels (")
                            .append(formatPercent(getCancelRate(playCount, cancelCount)));
                    if (playsLabelPending[0]) {
                        message.append(" of plays");
                        playsLabelPending[0] = false;
                    }
                    message.append(")\n");
                });
    }

    private static double getCancelRate(int playCount, int cancelCount) {
        return playCount == 0 ? 0 : (double) cancelCount / playCount;
    }

    private static String formatPercent(double rate) {
        return BigDecimal.valueOf(rate * 100)
                        .setScale(2, RoundingMode.HALF_UP)
                        .stripTrailingZeros()
                        .toPlainString()
                + "%";
    }

    private static void appendOverruleStats(StringBuilder message, Map<String, Integer> overruleCounts) {
        if (overruleCounts.isEmpty()) {
            message.append("No Overrule data matched the selected filters.\n");
            return;
        }

        overruleCounts.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, Integer> entry) -> entry.getValue())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> message.append("- ")
                        .append(entry.getKey())
                        .append(": ")
                        .append(entry.getValue())
                        .append('\n'));
    }

    static void accumulateActionCardPlayToWinCorrelation(
            Game game,
            Player winner,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, Integer> unattributedPlayCounts) {
        String winningPlayerId = StringUtils.defaultIfBlank(winner.getStatsTrackedUserID(), winner.getUserID());
        for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
            boolean canceled = actionCardPlay.isCanceled();
            if (StringUtils.isBlank(actionCardPlay.getPlayerId())) {
                unattributedPlayCounts.merge(actionCardPlay.getActionCard(), 1, Integer::sum);
            }
            // A canceled play never reaches win attribution below, so it still counts even when we
            // don't know who played it - the legacy-save migration reconstructs cancels as plays
            // with no player, and dropping those hid most of the cancels this section reports.
            if (!canceled && StringUtils.isBlank(actionCardPlay.getPlayerId())) {
                continue;
            }

            PlayToWinCorrelationCount count = playToWinCorrelationCounts.computeIfAbsent(
                    actionCardPlay.getActionCard(), _ -> new PlayToWinCorrelationCount());
            count.incrementPlaysIncludingCanceled();
            if (canceled) {
                continue;
            }
            count.incrementTotal();
            if (winningPlayerId.equals(actionCardPlay.getPlayerId())) {
                count.incrementWins();
            }
        }
    }

    static void appendPlayToWinCorrelationStats(
            StringBuilder message,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, Integer> expectedDrawsPerCard) {
        if (playToWinCorrelationCounts.isEmpty()) {
            message.append("No eligible action card play-to-win correlation data matched the selected filters.\n");
            return;
        }

        List<Map.Entry<String, PlayToWinCorrelationCount>> sortedEntries =
                playToWinCorrelationCounts.entrySet().stream()
                        .sorted(Comparator.<Map.Entry<String, PlayToWinCorrelationCount>>comparingInt(
                                        entry -> getImpactScore(
                                                                entry.getValue().getWins(),
                                                                expectedDrawsPerCard.get(entry.getKey()))
                                                        != null
                                                ? 0
                                                : 1)
                                .thenComparing(
                                        entry -> {
                                            Double impactScore = getImpactScore(
                                                    entry.getValue().getWins(),
                                                    expectedDrawsPerCard.get(entry.getKey()));
                                            return impactScore != null
                                                    ? impactScore
                                                    : entry.getValue().getWins();
                                        },
                                        Comparator.reverseOrder())
                                .thenComparing(entry -> entry.getValue().getTotal(), Comparator.reverseOrder())
                                .thenComparing(Map.Entry::getKey))
                        .toList();
        for (int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<String, PlayToWinCorrelationCount> entry = sortedEntries.get(i);
            boolean firstEntry = i == 0;
            PlayToWinCorrelationCount count = entry.getValue();
            message.append("- ")
                    .append(entry.getKey())
                    .append(": ")
                    .append(count.getWins())
                    .append(" wins, ")
                    .append(count.getPlaysIncludingCanceled())
                    .append(" plays (")
                    .append(String.format("%.1f%%", count.getWinRateIncludingCanceled() * 100))
                    .append(firstEntry ? " win rate), " : "), ")
                    .append(count.getTotal())
                    .append(" uncancelled plays (")
                    .append(String.format("%.1f%%", count.getWinRate() * 100))
                    .append(firstEntry ? " win rate)" : ")");
            Integer expectedDraws = expectedDrawsPerCard.get(entry.getKey());
            Double impactScore = getImpactScore(count.getWins(), expectedDraws);
            if (impactScore != null) {
                message.append(", ")
                        .append(String.format("%.1f", impactScore))
                        .append(firstEntry ? " Impact Score (wins vs ~draws)" : " Impact Score")
                        .append(", ")
                        .append(String.format("%.1f", getOmegaImpactScore(count, expectedDraws)))
                        .append(firstEntry ? " Impact Score Ω (+0.2 win per cancel)" : " Impact Score Ω");
            }
            message.append('\n');
        }
    }

    private static Double getImpactScore(int wins, Integer expectedDraws) {
        return expectedDraws == null || expectedDraws <= 0 ? null : wins / (double) expectedDraws * 100;
    }

    private static double getOmegaImpactScore(PlayToWinCorrelationCount count, int expectedDraws) {
        return (count.getWins() + CANCEL_WIN_EQUIVALENT * count.getCanceled()) / (double) expectedDraws * 100;
    }

    @Getter
    static class PlayToWinCorrelationCount {
        private int playsIncludingCanceled;
        private int total;
        private int wins;

        void incrementPlaysIncludingCanceled() {
            playsIncludingCanceled++;
        }

        void incrementTotal() {
            total++;
        }

        void incrementWins() {
            wins++;
        }

        int getCanceled() {
            return playsIncludingCanceled - total;
        }

        double getWinRate() {
            return total == 0 ? 0 : (double) wins / total;
        }

        double getWinRateIncludingCanceled() {
            return playsIncludingCanceled == 0 ? 0 : (double) wins / playsIncludingCanceled;
        }
    }
}
