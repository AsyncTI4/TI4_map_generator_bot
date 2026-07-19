package ti4.service.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.Player;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.DeckModel;

@UtilityClass
public class ActionCardStatsService {
    private static final LocalDate PLAYER_TRACKING_START_DATE = LocalDate.of(2026, 5, 23);
    private static final String DEFAULT_AC_DECK_ID = "action_cards_pok";

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showActionCardStats(event));
    }

    private static void showActionCardStats(SlashCommandInteractionEvent event) {
        String acDeckId = event.getOption(Constants.AC_DECK, DEFAULT_AC_DECK_ID, OptionMapping::getAsString);
        DeckModel acDeck = Mapper.getDeck(acDeckId);
        if (acDeck == null || acDeck.getType() != DeckModel.DeckType.ACTION_CARD) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "'" + acDeckId + "' is not an action card deck, please retry.");
            return;
        }

        Map<String, Integer> cancelCounts = new HashMap<>();
        Map<String, Integer> actionCardsPlayedCounts = new HashMap<>();
        Map<String, Integer> overruleCounts = new HashMap<>();
        Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts = new HashMap<>();

        // A discarded card that isn't in the selected deck means the game is mislabeled (e.g. it
        // changed decks mid-game), which would pollute the stats with off-deck cards.
        Set<String> deckCardIds = new HashSet<>(acDeck.getCardIDs());

        ConsumeGameUtility.consumeAllGames(
                GameStatisticsFilterer.getStandardCompetitiveGamesFilter()
                        .and(game -> acDeckId.equals(game.getAcDeckID()))
                        .and(game -> deckCardIds.containsAll(
                                game.getDiscardActionCards().keySet())),
                game -> accumulateActionCardStats(
                        game, cancelCounts, actionCardsPlayedCounts, overruleCounts, playToWinCorrelationCounts),
                ExecutionLockType.READ);

        MessageHelper.sendMessageToThread(
                event.getChannel(),
                "Action Card Play Statistics",
                buildMessage(
                        acDeck, cancelCounts, actionCardsPlayedCounts, overruleCounts, playToWinCorrelationCounts));
    }

    private static void accumulateActionCardStats(
            Game game,
            Map<String, Integer> cancelCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> overruleCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
        game.getGameStats()
                .getCountPerTarget(GameStats.SABOTAGE)
                .forEach((acName, count) -> cancelCounts.merge(acName, count, Integer::sum));

        game.getGameStats()
                .getCountPerTarget(GameStats.OVERRULE)
                .forEach((scName, count) -> overruleCounts.merge(scName, count, Integer::sum));

        game.getDiscardActionCards()
                .forEach((acID, ignored) -> incrementActionCardPlayCount(actionCardsPlayedCounts, acID));

        List<ActionCardPlay> actionCardPlays = game.getGameStats().getActionCardPlays();
        Player winner = game.getWinner().orElse(null);
        if (actionCardPlays.isEmpty() || winner == null) {
            return;
        }
        Set<ActionCardPlay> sabotagedPlays = findSabotagedPlays(actionCardPlays);
        accumulateActionCardPlayToWinCorrelation(game, winner, sabotagedPlays, playToWinCorrelationCounts);
    }

    // A Sabotage is recorded after the play it cancels, with the canceled card's name as its
    // target, so each Sabotage matches the nearest earlier not-yet-matched play of that card.
    private static Set<ActionCardPlay> findSabotagedPlays(List<ActionCardPlay> actionCardPlays) {
        Set<ActionCardPlay> sabotagedPlays = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < actionCardPlays.size(); i++) {
            ActionCardPlay sabotage = actionCardPlays.get(i);
            if (!GameStats.SABOTAGE.equals(sabotage.getActionCard()) || sabotage.getTarget() == null) {
                continue;
            }
            for (int j = i - 1; j >= 0; j--) {
                ActionCardPlay candidate = actionCardPlays.get(j);
                if (sabotage.getTarget().equals(candidate.getActionCard()) && sabotagedPlays.add(candidate)) {
                    break;
                }
            }
        }
        return sabotagedPlays;
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

    // Cards with the same number of copies in the deck have about the same draw rate, so the most
    // played card of a copy class approximates one play per draw; its play count stands in for how
    // many times each card of that class was drawn. The deck composition only classifies cards.
    static Map<String, Integer> computeExpectedDraws(
            Map<String, Integer> playCounts, Map<String, Integer> copiesPerName) {
        Map<Integer, Integer> maxPlaysPerCopyCount = computeMaxPlaysPerCopyCount(playCounts, copiesPerName);

        Map<String, Integer> expectedDraws = new HashMap<>();
        copiesPerName.forEach((name, copies) -> {
            int classMax = maxPlaysPerCopyCount.getOrDefault(copies, 0);
            if (classMax > 0) {
                expectedDraws.put(name, classMax);
            }
        });
        return expectedDraws;
    }

    private static Map<Integer, Integer> computeMaxPlaysPerCopyCount(
            Map<String, Integer> playCounts, Map<String, Integer> copiesPerName) {
        Map<Integer, Integer> maxPlaysPerCopyCount = new HashMap<>();
        copiesPerName.forEach(
                (name, copies) -> maxPlaysPerCopyCount.merge(copies, playCounts.getOrDefault(name, 0), Integer::max));
        return maxPlaysPerCopyCount;
    }

    private static String buildMessage(
            DeckModel acDeck,
            Map<String, Integer> cancelCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> overruleCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
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
        appendExpectedDrawsNote(message, computeMaxPlaysPerCopyCount(actionCardsPlayedCounts, copiesPerName));
        appendActionCardPlayAndCancelStats(message, actionCardsPlayedCounts, cancelCounts, playedExpectedDraws);
        message.append("\n**Action card play-to-win correlation**\n");
        appendTrackingStartNote(message);
        appendExpectedDrawsNote(message, computeMaxPlaysPerCopyCount(playsIncludingCanceled, copiesPerName));
        message.append("_The Impact Score compares wins to expected draws._\n");
        appendPlayToWinCorrelationStats(message, playToWinCorrelationCounts, winCorrelationExpectedDraws);
        if (copiesPerName.containsKey(GameStats.OVERRULE)) {
            message.append("\n**Overrule targets**\n");
            appendTrackingStartNote(message);
            appendOverruleStats(message, overruleCounts);
        }
        return message.toString();
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

    private static void accumulateActionCardPlayToWinCorrelation(
            Game game,
            Player winner,
            Set<ActionCardPlay> sabotagedPlays,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts) {
        String winningPlayerId = StringUtils.defaultIfBlank(winner.getStatsTrackedUserID(), winner.getUserID());
        for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
            if (StringUtils.isBlank(actionCardPlay.getPlayerId())) {
                continue;
            }

            PlayToWinCorrelationCount count = playToWinCorrelationCounts.computeIfAbsent(
                    actionCardPlay.getActionCard(), _ -> new PlayToWinCorrelationCount());
            count.incrementPlaysIncludingCanceled();
            if (sabotagedPlays.contains(actionCardPlay)) {
                continue;
            }
            count.incrementTotal();
            if (winningPlayerId.equals(actionCardPlay.getPlayerId())) {
                count.incrementWins();
            }
        }
    }

    private static void appendPlayToWinCorrelationStats(
            StringBuilder message,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, Integer> expectedDrawsPerCard) {
        if (playToWinCorrelationCounts.isEmpty()) {
            message.append("No eligible action card play-to-win correlation data matched the selected filters.\n");
            return;
        }

        playToWinCorrelationCounts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, PlayToWinCorrelationCount>>comparingInt(entry ->
                                getImpactScore(entry.getValue().getWins(), expectedDrawsPerCard.get(entry.getKey()))
                                                != null
                                        ? 0
                                        : 1)
                        .thenComparing(
                                entry -> {
                                    Double impactScore = getImpactScore(
                                            entry.getValue().getWins(), expectedDrawsPerCard.get(entry.getKey()));
                                    return impactScore != null
                                            ? impactScore
                                            : entry.getValue().getWins();
                                },
                                Comparator.reverseOrder())
                        .thenComparing(entry -> entry.getValue().getTotal(), Comparator.reverseOrder())
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> {
                    PlayToWinCorrelationCount count = entry.getValue();
                    message.append("- ")
                            .append(entry.getKey())
                            .append(": ")
                            .append(count.getWins())
                            .append(" wins, ")
                            .append(count.getTotal())
                            .append(" plays (")
                            .append(String.format("%.1f%%", count.getWinRate() * 100))
                            .append(")");
                    Integer expectedDraws = expectedDrawsPerCard.get(entry.getKey());
                    Double impactScore = getImpactScore(count.getWins(), expectedDraws);
                    if (impactScore != null) {
                        message.append(", ")
                                .append(String.format("%.1f", impactScore))
                                .append(" Impact Score (wins vs ~draws)");
                    }
                    if (expectedDraws != null) {
                        Double uncancelledImpactScore =
                                getImpactScore(count.getWins(), expectedDraws - count.getCanceledPlays());
                        if (uncancelledImpactScore != null) {
                            message.append(", ")
                                    .append(String.format("%.1f", uncancelledImpactScore))
                                    .append(" Uncancelled Impact Score");
                        }
                    }
                    message.append('\n');
                });
    }

    private static Double getImpactScore(int wins, Integer expectedDraws) {
        return expectedDraws == null || expectedDraws <= 0 ? null : wins / (double) expectedDraws * 100;
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

        double getWinRate() {
            return total == 0 ? 0 : (double) wins / total;
        }

        int getCanceledPlays() {
            return playsIncludingCanceled - total;
        }
    }
}
