package ti4.service.statistics;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.statistics.GameStatisticsFilterer;
import ti4.executors.ExecutionLockType;
import ti4.game.Game;
import ti4.game.GameStats;
import ti4.game.GameStats.ActionCardPlay;
import ti4.game.Player;
import ti4.game.helper.GameHelper;
import ti4.game.persistence.ConsumeGameUtility;
import ti4.helpers.ActionCardHelper;
import ti4.image.Mapper;
import ti4.message.MessageHelper;
import ti4.model.ActionCardModel;
import ti4.model.DeckModel;
import ti4.spring.service.statistics.overrule.OverruleStatsService;

@UtilityClass
public class ActionCardStatsService {
    public static final String FULL_DETAILS_OPTION = "full_details";

    // Worth moving forward once we have the volume to spare. Games from the first weeks after this
    // date still carry player-less Overrule placeholders, because legacy code recorded an Overrule
    // play only once its strategy card was chosen - one canceled before that has no play of its own
    // and the placeholder is its only trace. Those age out as older games leave the sample, so once
    // the orphans are gone, a later cutoff buys cleaner data at no real cost.
    private static final LocalDate PLAYER_TRACKING_START_DATE = LocalDate.of(2026, 5, 23);
    private static final String DEFAULT_AC_DECK_ID = "action_cards_te";

    private static final double IMPACT_WIN_RATE_WEIGHT = 0.6;
    private static final double IMPACT_PLAY_RATE_WEIGHT = 0.3;
    private static final double IMPACT_CANCEL_RATE_WEIGHT = 0.1;

    // Bounds on the pseudo-plays estimated below, guarding against a degenerate dataset asking for
    // either no shrinkage at all or so much that every card collapses onto the deck average.
    private static final double MIN_PSEUDO_PLAYS = 5;
    private static final double MAX_PSEUDO_PLAYS = 500;

    // Cards thinner than this are left out of the spread estimate: their rates are mostly noise,
    // and a rate off one or two plays would swamp the correction meant to remove that noise.
    private static final int MIN_PLAYS_TO_MEASURE_SPREAD = 10;

    // A card should be seen about as often as its copy count, so the bar for trusting its rates
    // scales the same way. Anything under it is still scored, just marked as thin.
    private static final int STABLE_UNCANCELLED_PLAYS_PER_COPY = 100;

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(event, () -> showActionCardStats(event));
    }

    private static void showActionCardStats(SlashCommandInteractionEvent event) {
        DeckModel acDeck = Mapper.getDeck(DEFAULT_AC_DECK_ID);

        Map<String, Integer> cancelCounts = new HashMap<>();
        Map<String, Integer> actionCardsPlayedCounts = new HashMap<>();
        Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts = new HashMap<>();
        Map<String, UnattributedPlays> unattributedPlays = new HashMap<>();
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
                        unattributedPlays,
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
                        unattributedPlays,
                        includedGameNames,
                        event.getOption(FULL_DETAILS_OPTION, false, OptionMapping::getAsBoolean),
                        CommandHelper.hasRole(event, JdaService.developerRoles)));
    }

    private static void accumulateActionCardStats(
            Game game,
            Map<String, Integer> cancelCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, UnattributedPlays> unattributedPlays,
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
        if (winner == null || !startedAfterPlayerTracking(game)) {
            return;
        }
        accumulateActionCardPlayToWinCorrelation(game, winner, playToWinCorrelationCounts, unattributedPlays);
    }

    // Older games recorded plays with no player at all, so they can only contribute cancels - never
    // a win to weigh them against. Mixing them in would rate one era's cancels against another
    // era's wins, so the correlation section stops at the date per-player tracking began.
    static boolean startedAfterPlayerTracking(Game game) {
        LocalDate creationDate = getCreationDate(game);
        // A game we cannot date could be from either era, so leave it out.
        return creationDate != null && creationDate.isAfter(PLAYER_TRACKING_START_DATE);
    }

    private static LocalDate getCreationDate(Game game) {
        if (StringUtils.isBlank(game.getCreationDate())) {
            return null;
        }
        try {
            return GameHelper.getCreationDateAsLocalDate(game);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static void incrementActionCardPlayCount(
            Map<String, Integer> actionCardsPlayedCounts, String actionCardId) {
        ActionCardModel actionCardModel = Mapper.getActionCard(actionCardId);
        String name = actionCardModel != null ? actionCardModel.getName() : actionCardId;
        actionCardsPlayedCounts.merge(name, 1, Integer::sum);
    }

    private static Set<String> getUnsabotageableNames(DeckModel acDeck) {
        Set<String> unsabotageableNames = new HashSet<>();
        for (String cardId : acDeck.getCardIDs()) {
            ActionCardModel actionCardModel = Mapper.getActionCard(cardId);
            if (actionCardModel != null && ActionCardHelper.cannotBeSabotaged(actionCardModel)) {
                unsabotageableNames.add(actionCardModel.getName());
            }
        }
        return unsabotageableNames;
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
    static Map<String, Integer> computeEstimatedDraws(
            Map<String, Integer> playCounts, Map<String, Integer> copiesPerName) {
        Map<Integer, Integer> estimatedDrawsPerCopyCount = computeEstimatedDrawsPerCopyCount(playCounts, copiesPerName);

        Map<String, Integer> estimatedDraws = new HashMap<>();
        copiesPerName.forEach((name, copies) -> {
            int draws = estimatedDrawsPerCopyCount.getOrDefault(copies, 0);
            if (draws > 0) {
                estimatedDraws.put(name, draws);
            }
        });
        return estimatedDraws;
    }

    private static Map<Integer, Integer> computeEstimatedDrawsPerCopyCount(
            Map<String, Integer> playCounts, Map<String, Integer> copiesPerName) {
        double drawsPerCopy = computeDrawsPerCopy(playCounts, copiesPerName);
        return copiesPerName.values().stream().distinct().collect(Collectors.toMap(copies -> copies, copies ->
                (int) Math.round(drawsPerCopy * copies)));
    }

    // Plays per copy in the deck puts every card on equal footing, so a 4-of leading the deck sets
    // the same per-copy estimate a 1-of would. Scaling that back up by a card's own copy count is
    // what turns one leader's play count into an estimated draw count for the whole deck.
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

    private static List<String> buildMessage(
            DeckModel acDeck,
            Map<String, Integer> cancelCounts,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, UnattributedPlays> unattributedPlays,
            Set<String> includedGameNames,
            boolean includeFullDetails,
            boolean includeDeveloperDebug) {
        Map<String, Integer> copiesPerName = getCopiesPerName(acDeck);
        Map<String, Integer> playsIncludingCanceled = playToWinCorrelationCounts.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey, entry -> entry.getValue().getPlaysIncludingCanceled()));
        Map<String, Integer> winCorrelationEstimatedDraws =
                computeEstimatedDraws(playsIncludingCanceled, copiesPerName);

        List<String> blocks = new ArrayList<>();
        StringBuilder message = new StringBuilder();
        message.append(
                        "\n_6-player, 10-victory-point, non-homebrew, non-Galactic-Event, non-Scenario games with winners, using deck '")
                .append(acDeck.getName())
                .append("'._\n");
        Map<String, Integer> playedEstimatedDraws = computeEstimatedDraws(actionCardsPlayedCounts, copiesPerName);
        message.append("\n**Plays vs estimated draws, and cancel rates**\n");
        appendEstimatedDrawsNote(message, computeEstimatedDrawsPerCopyCount(actionCardsPlayedCounts, copiesPerName));
        appendActionCardPlayAndCancelStats(message, actionCardsPlayedCounts, cancelCounts, playedEstimatedDraws);
        blocks.add(message.toString());

        StringBuilder impactScoreNotes = new StringBuilder();
        impactScoreNotes.append("\n**Impact Score**\n");
        impactScoreNotes
                .append("_Only games started after ")
                .append(PLAYER_TRACKING_START_DATE)
                .append(", when we started tracking who played each card._\n");
        appendEstimatedDrawsNote(
                impactScoreNotes, computeEstimatedDrawsPerCopyCount(playsIncludingCanceled, copiesPerName));
        impactScoreNotes.append(
                "_The Impact Score blends win rate (0.6), play rate (0.3) and cancel rate (0.1), each measured against the deck's best. Thin samples are pulled toward the deck average, and a \\* marks a card still too thin to trust. A rank (#) is the card's place against every other card for that figure._\n");
        impactScoreNotes.append(
                "_A card no Sabotage can cancel is scored on win rate (0.67) and play rate (0.33) alone._\n");
        blocks.add(impactScoreNotes.toString());
        appendPlayToWinCorrelationStats(
                blocks,
                playToWinCorrelationCounts,
                winCorrelationEstimatedDraws,
                copiesPerName,
                getUnsabotageableNames(acDeck),
                includeFullDetails);

        if (copiesPerName.containsKey(GameStats.OVERRULE)) {
            StringBuilder overruleTargets = new StringBuilder();
            overruleTargets.append("\n**Overrule targets**\n");
            appendTrackingStartNote(overruleTargets);
            appendOverruleStats(overruleTargets, OverruleStatsService.get().getCountPerStrategyCard(includedGameNames));
            blocks.add(overruleTargets.toString());
        }
        if (includeDeveloperDebug) {
            StringBuilder developerDebug = new StringBuilder();
            appendUnattributedPlayDebug(developerDebug, unattributedPlays);
            blocks.add(developerDebug.toString());
        }
        return blocks;
    }

    // Every game in the correlation section is new enough to record who played each card, so this
    // should always be empty. Anything listed here is a live recording path dropping the player.
    static void appendUnattributedPlayDebug(StringBuilder message, Map<String, UnattributedPlays> unattributedPlays) {
        message.append("\n**Unattributed plays (developer debug)**\n");
        message.append(
                "_Play-to-win correlation plays with no recorded player, per card, with the games they came from._\n");
        if (unattributedPlays.isEmpty()) {
            message.append("Every tracked play has a player.\n");
            return;
        }

        unattributedPlays.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<String, UnattributedPlays> entry) ->
                                entry.getValue().getCount())
                        .reversed()
                        .thenComparing(Map.Entry::getKey))
                .forEach(entry -> {
                    UnattributedPlays plays = entry.getValue();
                    message.append("- ").append(entry.getKey()).append(": ").append(plays.getCount());
                    // Overrule turns up in far too many games to name them all, so it reports the
                    // span of game creation dates instead - enough to tell whether the games still
                    // dropping the player are recent ones or all sit behind the tracking cutoff.
                    if (GameStats.OVERRULE.equals(entry.getKey())) {
                        appendCreationDateSpan(message, plays);
                    } else {
                        message.append(" (")
                                .append(String.join(", ", plays.getGameNames()))
                                .append(')');
                    }
                    message.append('\n');
                });
    }

    private static void appendCreationDateSpan(StringBuilder message, UnattributedPlays plays) {
        if (plays.getFirstCreationDate() == null) {
            return;
        }
        message.append(" (games created ")
                .append(plays.getFirstCreationDate())
                .append(" through ")
                .append(plays.getLastCreationDate())
                .append(", ")
                .append(plays.getGameNames().size())
                .append(" games)");
    }

    private static void appendTrackingStartNote(StringBuilder message) {
        message.append("_We started tracking these on ")
                .append(PLAYER_TRACKING_START_DATE)
                .append("._\n");
    }

    private static void appendEstimatedDrawsNote(StringBuilder message, Map<Integer, Integer> maxPlaysPerCopyCount) {
        String perCopyCount = maxPlaysPerCopyCount.entrySet().stream()
                .filter(entry -> entry.getValue() > 0)
                .sorted(Map.Entry.<Integer, Integer>comparingByKey().reversed())
                .map(entry -> entry.getValue() + " " + entry.getKey() + "-ofs")
                .collect(Collectors.joining(", "));
        if (perCopyCount.isEmpty()) {
            return;
        }
        message.append("_Estimated draws: ").append(perCopyCount).append("._\n");
    }

    private static void appendActionCardPlayAndCancelStats(
            StringBuilder message,
            Map<String, Integer> actionCardsPlayedCounts,
            Map<String, Integer> cancelCounts,
            Map<String, Integer> estimatedDrawsPerCard) {
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
                    message.append("- ").append(actionCardName).append(": ");
                    appendCount(message, playCount, "play");
                    Integer estimatedDraws = estimatedDrawsPerCard.get(actionCardName);
                    if (estimatedDraws != null && estimatedDraws > 0) {
                        message.append(" (").append(formatPercent(playCount / (double) estimatedDraws));
                        if (drawsLabelPending[0]) {
                            message.append(" of ~draws");
                            drawsLabelPending[0] = false;
                        }
                        message.append(')');
                    }
                    message.append(", ");
                    appendCount(message, cancelCount, "cancel");
                    message.append(" (").append(formatPercent(getCancelRate(playCount, cancelCount)));
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
            Map<String, UnattributedPlays> unattributedPlays) {
        String winningPlayerId = StringUtils.defaultIfBlank(winner.getStatsTrackedUserID(), winner.getUserID());
        LocalDate creationDate = getCreationDate(game);
        for (ActionCardPlay actionCardPlay : game.getGameStats().getActionCardPlays()) {
            boolean canceled = actionCardPlay.isCanceled();
            if (StringUtils.isBlank(actionCardPlay.getPlayerId())) {
                unattributedPlays
                        .computeIfAbsent(actionCardPlay.getActionCard(), _ -> new UnattributedPlays())
                        .record(game.getName(), creationDate);
            }

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

    // Each card is one block: Discord reads the bullets nested under a card as a list of their own
    // if they arrive without the line naming it, so a card has to be kept whole in one message.
    static void appendPlayToWinCorrelationStats(
            List<String> blocks,
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, Integer> estimatedDrawsPerCard,
            Map<String, Integer> copiesPerName,
            Set<String> unsabotageableCards,
            boolean includeFullDetails) {
        if (playToWinCorrelationCounts.isEmpty()) {
            blocks.add("No eligible action card play data matched the selected filters.\n");
            return;
        }

        Map<String, Double> impactScores =
                computeImpactScores(playToWinCorrelationCounts, estimatedDrawsPerCard, unsabotageableCards);
        List<Map.Entry<String, PlayToWinCorrelationCount>> sortedEntries =
                playToWinCorrelationCounts.entrySet().stream()
                        .sorted(Comparator.<Map.Entry<String, PlayToWinCorrelationCount>>comparingInt(
                                        entry -> impactScores.containsKey(entry.getKey()) ? 0 : 1)
                                .thenComparing(
                                        entry -> impactScores.getOrDefault(entry.getKey(), (double)
                                                entry.getValue().getWins()),
                                        Comparator.reverseOrder())
                                .thenComparing(entry -> entry.getValue().getTotal(), Comparator.reverseOrder())
                                .thenComparing(Map.Entry::getKey))
                        .toList();
        Set<String> cardNames = playToWinCorrelationCounts.keySet();
        Map<String, Integer> playRateRanks = rankDescending(
                cardNames, name -> getPlayRate(playToWinCorrelationCounts.get(name), estimatedDrawsPerCard.get(name)));
        Map<String, Integer> uncancelledWinRateRanks = rankDescending(
                cardNames, name -> playToWinCorrelationCounts.get(name).getWinRate());
        Map<String, Integer> cancelRateRanks = rankDescending(
                cardNames, name -> playToWinCorrelationCounts.get(name).getCancelRate());

        for (int i = 0; i < sortedEntries.size(); i++) {
            Map.Entry<String, PlayToWinCorrelationCount> entry = sortedEntries.get(i);
            // The list is sorted by Impact Score, so only the rates need a rank spelled out. The
            // leading card names each rate in full and every card below it abbreviates.
            boolean firstEntry = i == 0;
            String winRateLabel = firstEntry ? "uncancelled play win rate" : "win";
            String playRateLabel = firstEntry ? "plays vs estimated draw rate" : "play";
            String cancelRateLabel = firstEntry ? "cancel rate" : "cancel";
            String cardName = entry.getKey();
            PlayToWinCorrelationCount count = entry.getValue();

            StringBuilder message = new StringBuilder();
            message.append("- **").append(cardName).append(":**\n");
            Double impactScore = impactScores.get(cardName);
            if (impactScore != null) {
                message.append("  - ").append(String.format("%.1f", impactScore));
                if (isUnstable(count, copiesPerName.getOrDefault(cardName, 1))) {
                    // Escaped so Discord prints the asterisk instead of reading it as italics.
                    message.append("\\*");
                }
                message.append(" Impact Score\n");
            }

            // Only uncancelled plays can produce a win, so a win rate over plays that include
            // cancels would divide by games the card never got to affect.
            appendRate(message, count.getWinRate(), winRateLabel, uncancelledWinRateRanks.get(cardName));
            Double playRate = getPlayRate(count, estimatedDrawsPerCard.get(cardName));
            if (playRate != null) {
                appendRate(message, playRate, playRateLabel, playRateRanks.get(cardName));
            }
            appendRate(message, count.getCancelRate(), cancelRateLabel, cancelRateRanks.get(cardName));
            if (includeFullDetails) {
                // The rates above are what the score is built from; these are the raw counts they
                // were divided out of, for anyone who wants to see the sample behind a figure.
                message.append("  - ");
                appendCount(message, count.getWins(), "win");
                message.append(", ");
                appendCount(message, count.getPlaysIncludingCanceled(), "play");
                message.append(", ");
                appendCount(message, count.getCanceled(), "cancel");
                message.append("\n");
            }
            blocks.add(message.toString());
        }
    }

    private static void appendRate(StringBuilder message, double rate, String label, Integer rank) {
        message.append("  - ")
                .append(formatRate(rate))
                .append(' ')
                .append(label)
                .append(" (#")
                .append(rank)
                .append(")\n");
    }

    // Discord shows these counts down to the single play, so "1 cancels" is on screen often
    // enough to be worth a plural that agrees with the number in front of it.
    private static void appendCount(StringBuilder message, int count, String singular) {
        message.append(count).append(" ").append(singular);
        if (count != 1) {
            message.append("s");
        }
    }

    private static String formatRate(double rate) {
        return String.format("%.1f%%", rate * 100);
    }

    // Standard competition ranking: cards tied on a figure share the better rank. Cards the figure
    // does not apply to are left unranked rather than crowding the bottom of it.
    private static Map<String, Integer> rankDescending(Set<String> cardNames, Function<String, Double> figure) {
        List<String> ranked = cardNames.stream()
                .filter(cardName -> figure.apply(cardName) != null)
                .sorted(Comparator.comparing(figure, Comparator.reverseOrder()))
                .toList();

        Map<String, Integer> ranks = new HashMap<>();
        Double previousValue = null;
        int previousRank = 0;
        for (int i = 0; i < ranked.size(); i++) {
            String cardName = ranked.get(i);
            Double value = figure.apply(cardName);
            int rank = value.equals(previousValue) ? previousRank : i + 1;
            ranks.put(cardName, rank);
            previousValue = value;
            previousRank = rank;
        }
        return ranks;
    }

    // A card's impact is how well it wins, how much of the deck's draws it accounts for, and how
    // badly opponents want it gone - weighted in that order. Each rate is measured against the
    // deck's best, so a card that leads all three scores 100.
    static Map<String, Double> computeImpactScores(
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            Map<String, Integer> estimatedDrawsPerCard,
            Set<String> unsabotageableCards) {
        // Win rates and cancel rates spread very differently - most cards win at roughly the deck
        // average while cancels pile onto a handful of cards - so each gets its own shrinkage.
        ShrinkageModel winRateModel = buildShrinkageModel(
                playToWinCorrelationCounts, PlayToWinCorrelationCount::getWins, count -> count.getTotal());
        ShrinkageModel cancelRateModel = buildShrinkageModel(
                playToWinCorrelationCounts,
                PlayToWinCorrelationCount::getCanceled,
                PlayToWinCorrelationCount::getPlaysIncludingCanceled);

        Map<String, Double> winRates = new HashMap<>();
        Map<String, Double> playRates = new HashMap<>();
        Map<String, Double> cancelRates = new HashMap<>();
        playToWinCorrelationCounts.forEach((cardName, count) -> {
            Double playRate = getPlayRate(count, estimatedDrawsPerCard.get(cardName));
            if (playRate == null) {
                // Without estimated draws there is no play rate, so the card cannot be scored at all.
                return;
            }
            winRates.put(cardName, winRateModel.shrink(count.getWins(), count.getTotal()));
            playRates.put(cardName, playRate);
            cancelRates.put(cardName, cancelRateModel.shrink(count.getCanceled(), count.getPlaysIncludingCanceled()));
        });

        double bestWinRate = getMax(winRates);
        double bestPlayRate = getMax(playRates);
        double bestCancelRate = getMax(cancelRates);

        Map<String, Double> impactScores = new HashMap<>();
        winRates.forEach((cardName, winRate) -> {
            double win = anchor(winRate, bestWinRate);
            double play = anchor(playRates.get(cardName), bestPlayRate);
            if (unsabotageableCards.contains(cardName)) {
                double scale = 1 / (IMPACT_WIN_RATE_WEIGHT + IMPACT_PLAY_RATE_WEIGHT);
                impactScores.put(
                        cardName,
                        (IMPACT_WIN_RATE_WEIGHT * scale * win + IMPACT_PLAY_RATE_WEIGHT * scale * play) * 100);
                return;
            }
            impactScores.put(
                    cardName,
                    (IMPACT_WIN_RATE_WEIGHT * win
                                    + IMPACT_PLAY_RATE_WEIGHT * play
                                    + IMPACT_CANCEL_RATE_WEIGHT * anchor(cancelRates.get(cardName), bestCancelRate))
                            * 100);
        });
        return impactScores;
    }

    // How far a card's own record should move it off the deck average. Both halves are measured
    // from the data: the average the deck actually produced, and how many plays a card must show
    // before its record outweighs that average.
    private record ShrinkageModel(double prior, double pseudoPlays) {

        // Too few plays to tell a good card from a lucky one, so lean on the deck average until the
        // card earns the right to speak for itself. A card with no plays lands exactly on the prior.
        double shrink(int numerator, int denominator) {
            return (numerator + pseudoPlays * prior) / (denominator + pseudoPlays);
        }
    }

    private static ShrinkageModel buildShrinkageModel(
            Map<String, PlayToWinCorrelationCount> playToWinCorrelationCounts,
            ToIntFunction<PlayToWinCorrelationCount> numerator,
            ToIntFunction<PlayToWinCorrelationCount> denominator) {
        int totalNumerator = 0;
        int totalDenominator = 0;
        List<double[]> samples = new ArrayList<>();
        for (PlayToWinCorrelationCount count : playToWinCorrelationCounts.values()) {
            int cardNumerator = numerator.applyAsInt(count);
            int cardDenominator = denominator.applyAsInt(count);
            totalNumerator += cardNumerator;
            totalDenominator += cardDenominator;
            if (cardDenominator >= MIN_PLAYS_TO_MEASURE_SPREAD) {
                samples.add(new double[] {cardNumerator / (double) cardDenominator, cardDenominator});
            }
        }

        // The prior weights by evidence, so a card played a thousand times pulls the deck average
        // harder than one played twice - that is the rate a card is assumed to have before it plays.
        double prior = totalDenominator == 0 ? 0 : (double) totalNumerator / totalDenominator;
        return new ShrinkageModel(prior, estimatePseudoPlays(samples));
    }

    // Method of moments on a beta-binomial. How far apart card rates sit once the spread that
    // coin-flip luck alone would produce is taken back out: rates that really are far apart mean a
    // card's own record says a lot and needs little shrinking, rates that cluster mean the reverse.
    // Unlike the prior this centres on the unweighted mean, since it is measuring how much cards
    // differ from each other rather than what the average play produces.
    static double estimatePseudoPlays(List<double[]> samples) {
        if (samples.size() < 2) {
            return MAX_PSEUDO_PLAYS;
        }

        double meanRate =
                samples.stream().mapToDouble(sample -> sample[0]).average().orElse(0);
        double binomialVariance = meanRate * (1 - meanRate);
        double observedVariance = samples.stream()
                        .mapToDouble(sample -> (sample[0] - meanRate) * (sample[0] - meanRate))
                        .sum()
                / (samples.size() - 1);
        double samplingNoise = binomialVariance
                * samples.stream()
                        .mapToDouble(sample -> 1 / sample[1])
                        .average()
                        .orElse(0);

        double spread = observedVariance - samplingNoise;
        if (spread <= 0) {
            // Cards vary no more than luck alone explains, so lean as hard as possible on the average.
            return MAX_PSEUDO_PLAYS;
        }
        return Math.clamp(binomialVariance / spread - 1, MIN_PSEUDO_PLAYS, MAX_PSEUDO_PLAYS);
    }

    private static double getMax(Map<String, Double> rates) {
        return rates.values().stream().mapToDouble(Double::doubleValue).max().orElse(0);
    }

    private static double anchor(double rate, double best) {
        return best <= 0 ? 0 : Math.min(rate / best, 1);
    }

    private static boolean isUnstable(PlayToWinCorrelationCount count, int copies) {
        return count.getTotal() < STABLE_UNCANCELLED_PLAYS_PER_COPY * copies;
    }

    private static Double getPlayRate(PlayToWinCorrelationCount count, Integer estimatedDraws) {
        return estimatedDraws == null || estimatedDraws <= 0
                ? null
                : count.getPlaysIncludingCanceled() / (double) estimatedDraws;
    }

    // Where a card's player-less plays came from: the games to look at, and the window they were
    // created in, so a card can be traced back to either the legacy migration or a live code path.
    @Getter
    static class UnattributedPlays {
        private int count;
        private final Set<String> gameNames = new TreeSet<>();
        private LocalDate firstCreationDate;
        private LocalDate lastCreationDate;

        void record(String gameName, LocalDate creationDate) {
            count++;
            gameNames.add(gameName);
            if (creationDate == null) {
                return;
            }
            if (firstCreationDate == null || creationDate.isBefore(firstCreationDate)) {
                firstCreationDate = creationDate;
            }
            if (lastCreationDate == null || creationDate.isAfter(lastCreationDate)) {
                lastCreationDate = creationDate;
            }
        }
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

        double getCancelRate() {
            return playsIncludingCanceled == 0 ? 0 : (double) getCanceled() / playsIncludingCanceled;
        }
    }
}
