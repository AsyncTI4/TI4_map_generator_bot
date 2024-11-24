package ti4.service.statistics.game;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import lombok.experimental.UtilityClass;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.apache.commons.lang3.StringUtils;
import ti4.commands2.statistics.GameStatisticsFilterer;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.GameStatsHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.PublicObjectiveModel;
import ti4.service.statistics.StatisticsPipeline;

@UtilityClass
public class GameStatisticsService {

    public static void queueReply(SlashCommandInteractionEvent event) {
        StatisticsPipeline.queue(new StatisticsPipeline.StatisticsEvent(event, () -> getGameStatistics(event)));
    }

    private void getGameStatistics(SlashCommandInteractionEvent event) {
        String statisticToShow = event.getOption(Constants.GAME_STATISTIC, null, OptionMapping::getAsString);
        GameStatTypes stat = GameStatTypes.fromString(statisticToShow);
        if (stat == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
            return;
        }
        switch (stat) {
            case UNLEASH_THE_NAMES -> AllNamesStatisticsService.sendAllNames(event);
            case PING_LIST -> PingCounterStatisticsService.listPingCounterList(event);
            case HIGHEST_SPENDERS -> SpendToWinCorrelationStatisticsService.calculateSpendToWinCorrelation(event);
            case GAME_LENGTH -> GameLengthStatisticsService.showGameLengths(event, 3650);
            case GAME_LENGTH_4MO -> GameLengthStatisticsService.showGameLengths(event, 120);
            case FACTIONS_PLAYED -> MostPlayedFactionsStatisticsService.showMostPlayedFactions(event);
            case AVERAGE_TURNS -> showAverageTurnsInAGameByFaction(event);
            case COLOURS_PLAYED -> showMostPlayedColour(event);
            case FACTION_WINS -> showMostWinningFactions(event);
            case PHASE_TIMES -> showTimeOfRounds(event);
            case SOS_SCORED -> VictoryPointsScoredStatisticsService.listScoredVictoryPoints(event);
            case FACTION_WIN_PERCENT -> showFactionWinPercent(event);
            case COLOUR_WINS -> MostWinningColorStatisticsService.showMostWinningColor(event);
            case GAME_COUNT -> showGameCount(event);
            case WINNING_PATH -> showWinningPath(event);
            case SUPPORT_WIN_COUNT -> GameStatsHelper.showWinsWithSupport(event);
            default -> MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
        }
    }

    private static void showAverageTurnsInAGameByFaction(SlashCommandInteractionEvent event) {
        Map<String, Double> factionCount = new HashMap<>();
        Map<String, Double> factionTurnCount = new HashMap<>();

        List<Game> filteredGames = GameStatisticsFilterer.getGamesFilter(event);
        for (Game game : filteredGames) {
            for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
                String faction = player.getFaction();
                double turnCount = player.getNumberTurns() - game.getDiscardAgendas().size() - game.getRound();
                System.out.println(player.getNumberTurns());
                if (turnCount < 10 || turnCount > 200) {
                    continue;
                }
                factionCount.put(faction,
                    1 + factionCount.getOrDefault(faction, 0.0));
                factionTurnCount.put(faction,
                    turnCount + factionTurnCount.getOrDefault(faction, 0.0));
                factionCount.put("allFactions",
                    1 + factionCount.getOrDefault("allFactions", 0.0));
                factionTurnCount.put("allFactions",
                    turnCount + factionTurnCount.getOrDefault("allFactions", 0.0));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Average Turns per Faction:").append("\n");
        sb.append("All Factions Combined:").append(String.format("%.2f", factionTurnCount.get("allFactions") / factionCount.get("allFactions"))).append("\n");
        factionCount.entrySet().stream()
            .filter(entry -> Mapper.isValidFaction(entry.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(String.format("%.2f", (factionTurnCount.get(entry.getKey().getAlias()) / entry.getValue())), 4))
                .append(" turns from ").append(entry.getValue()).append(" games`")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Average Turns per Faction", sb.toString());
    }

    private static void showGameCount(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Game count: " + GameStatisticsFilterer.getGamesFilter(event).size());
    }

    private static void showMostWinningFactions(SlashCommandInteractionEvent event) {
        Map<String, Integer> winnerFactionCount = new HashMap<>();
        List<Game> filteredGames = GameStatisticsFilterer.getGamesFilter(event);
        for (Game game : filteredGames) {
            Optional<Player> winner = game.getWinner();
            if (winner.isEmpty()) {
                continue;
            }
            String winningFaction = winner.get().getFaction();
            winnerFactionCount.put(winningFaction,
                1 + winnerFactionCount.getOrDefault(winningFaction, 0));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Wins per Faction:").append("\n");
        winnerFactionCount.entrySet().stream()
            .filter(entry -> Mapper.isValidFaction(entry.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("x` ")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Wins per Faction", sb.toString());
    }

    private static void showFactionWinPercent(SlashCommandInteractionEvent event) {
        List<Game> filteredGames = GameStatisticsFilterer.getGamesFilter(event);
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();
        Map<String, Integer> factionWinsWithRelics = new HashMap<>();
        for (Game game : filteredGames) {
            Optional<Player> winner = game.getWinner();
            if (winner.isEmpty()) {
                continue;
            }
            boolean emphidia = false;
            String winningFaction = winner.get().getFaction();
            factionWinCount.put(winningFaction,
                1 + factionWinCount.getOrDefault(winningFaction, 0));
            for (Map.Entry<String, List<String>> scoredPOEntry : game.getScoredPublicObjectives().entrySet()) {
                if (scoredPOEntry.getValue().contains(winner.get().getUserID())) {
                    String poID = scoredPOEntry.getKey();
                    if (poID.toLowerCase().contains("emphidia")) {
                        emphidia = true;
                    }
                }
            }
            if (winner.get().getRelics().contains("shard") || winner.get().getRelics().contains("obsidian") || emphidia) {
                factionWinsWithRelics.put(winningFaction,
                    1 + factionWinsWithRelics.getOrDefault(winningFaction, 0));
                factionWinsWithRelics.put("allWinners",
                    1 + factionWinsWithRelics.getOrDefault("allWinners", 0));
            }
            factionWinCount.put("allWinners",
                1 + factionWinCount.getOrDefault("allWinners", 0));
            game.getRealAndEliminatedAndDummyPlayers().forEach(player -> {
                String faction = player.getFaction();
                factionGameCount.put(faction,
                    1 + factionGameCount.getOrDefault(faction, 0));
            });
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Faction Win Percent:").append("\n");

        Mapper.getFactions().stream()
            .map(faction -> {
                double winCount = factionWinCount.getOrDefault(faction.getAlias(), 0);
                double gameCount = factionGameCount.getOrDefault(faction.getAlias(), 0);
                return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * winCount / gameCount));
            })
            .filter(entry -> factionGameCount.containsKey(entry.getKey().getAlias()))
            .sorted(Map.Entry.<FactionModel, Long>comparingByValue().reversed())
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("%` (")
                .append(factionGameCount.getOrDefault(entry.getKey().getAlias(), 0))
                .append(" games) ")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Faction Win Percent", sb.toString());

        StringBuilder sb2 = new StringBuilder();
        sb2.append("Winning Faction Relic Holding Percent:").append("\n");

        Mapper.getFactions().stream()
            .map(faction -> {
                double winCount = factionWinsWithRelics.getOrDefault(faction.getAlias(), 0);
                double gameCount = factionWinCount.getOrDefault(faction.getAlias(), 0);
                return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * winCount / gameCount));
            })
            .filter(entry -> factionGameCount.containsKey(entry.getKey().getAlias()))
            .sorted(Map.Entry.<FactionModel, Long>comparingByValue().reversed())
            .forEach(entry -> sb2.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("%` (")
                .append(factionWinCount.getOrDefault(entry.getKey().getAlias(), 0))
                .append(" games) ")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));

        sb2.append("All winners: ").append(factionWinsWithRelics.getOrDefault("allWinners", 0)).append(" wins with relics out of ").append(factionWinCount.getOrDefault("allWinners", 0)).append(" total wins");
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Winning Faction Relic Holding Percent", sb2.toString());
    }

    private static void showMostPlayedColour(SlashCommandInteractionEvent event) {
        Map<String, Integer> colorCount = new HashMap<>();
        List<Game> filteredGames = GameStatisticsFilterer.getGamesFilter(event);
        for (Game game : filteredGames) {
            for (Player player : game.getRealPlayers()) {
                String color = player.getColor();
                colorCount.put(color,
                    1 + colorCount.getOrDefault(color, 0));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Plays per Colour:").append("\n");
        colorCount.entrySet().stream()
            .filter(e -> Mapper.isValidColor(e.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("x` ")
                .append(Emojis.getColorEmojiWithName(entry.getKey()))
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Plays per Colour", sb.toString());
    }

    private static String convertMillisecondsToDays(float milliseconds) {
        // Constants for time conversion
        final float millisecondsInADay = 24 * 60 * 60 * 1000; // milliseconds in a day

        // Convert milliseconds to days
        float days = milliseconds / millisecondsInADay;

        // Format to 2 decimal points
        DecimalFormat df = new DecimalFormat("#.00");
        return df.format(days);
    }

    private static void showTimeOfRounds(SlashCommandInteractionEvent event) {
        Map<String, Long> timeCount = new HashMap<>();
        Map<String, Integer> amountCount = new HashMap<>();
        List<Game> filteredGames = GameStatisticsFilterer.getGamesFilter(event);
        for (Game game : filteredGames) {
            for (int x = 1; x < game.getRound() + 1; x++) {
                long time1;
                long time2;
                String key1 = "";
                String key2 = "";

                String name = "Round " + x + " Strategy And Action Phases";
                key1 = "startTimeOfRound" + x + "Strategy";
                key2 = "startTimeOfRound" + x + "StatusScoring";
                if (!game.getStoredValue(key1).isEmpty() && !game.getStoredValue(key2).isEmpty()) {
                    amountCount.put(name, 1 + amountCount.getOrDefault(name, 0));
                    time1 = Long.parseLong(game.getStoredValue(key1));
                    time2 = Long.parseLong(game.getStoredValue(key2));
                    timeCount.put(name, time2 - time1 + timeCount.getOrDefault(name, 0L));
                }

                name = "Round " + x + " Status Phase";
                key1 = "startTimeOfRound" + x + "StatusScoring";
                key2 = "startTimeOfRound" + x + "Agenda1";
                if (!game.getStoredValue(key1).isEmpty() && !game.getStoredValue(key2).isEmpty()) {
                    amountCount.put(name, 1 + amountCount.getOrDefault(name, 0));
                    time1 = Long.parseLong(game.getStoredValue(key1));
                    time2 = Long.parseLong(game.getStoredValue(key2));
                    timeCount.put(name, time2 - time1 + timeCount.getOrDefault(name, 0L));
                }

                name = "Round " + x + " Agenda  1";
                key1 = "startTimeOfRound" + x + "Agenda1";
                key2 = "startTimeOfRound" + x + "Agenda2";
                if (!game.getStoredValue(key1).isEmpty() && !game.getStoredValue(key2).isEmpty()) {
                    amountCount.put(name, 1 + amountCount.getOrDefault(name, 0));
                    time1 = Long.parseLong(game.getStoredValue(key1));
                    time2 = Long.parseLong(game.getStoredValue(key2));
                    timeCount.put(name, time2 - time1 + timeCount.getOrDefault(name, 0L));
                }

                name = "Round " + x + " Agenda  2";
                key1 = "startTimeOfRound" + x + "Agenda2";
                key2 = "startTimeOfRound" + (x + 1) + "Strategy";
                if (!game.getStoredValue(key1).isEmpty() && !game.getStoredValue(key2).isEmpty()) {
                    amountCount.put(name, 1 + amountCount.getOrDefault(name, 0));
                    time1 = Long.parseLong(game.getStoredValue(key1));
                    time2 = Long.parseLong(game.getStoredValue(key2));
                    timeCount.put(name, time2 - time1 + timeCount.getOrDefault(name, 0L));
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Time Per Phase:").append("\n");
        timeCount.forEach((key, value) -> sb.append(key).append(": ")
            .append(StringUtils.leftPad(convertMillisecondsToDays((float) value / amountCount.get(key)), 4)).append(" days (based on ").append(amountCount.get(key)).append(" games)")
            .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Time per Phase", sb.toString());
    }

    private static void showWinningPath(SlashCommandInteractionEvent event) {
        List<Game> filteredGames = GameStatisticsFilterer.getGamesFilter(event);
        Map<String, Integer> winningPathCount = getAllWinningPathCounts(filteredGames);
        int gamesWithWinnerCount = winningPathCount.values().stream().reduce(0, Integer::sum);
        AtomicInteger atomicInteger = new AtomicInteger();
        StringBuilder sb = new StringBuilder();
        sb.append("__**Winning Paths Count:**__").append("\n");
        winningPathCount.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry -> sb.append(atomicInteger.incrementAndGet())
                .append(". `")
                .append(entry.getValue().toString())
                .append(" (")
                .append(Math.round(100 * entry.getValue() / (double) gamesWithWinnerCount))
                .append("%)` ")
                .append(entry.getKey())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Winning Paths", sb.toString());
    }

    private static Map<String, Integer> getAllWinningPathCounts(List<Game> games) {
        Map<String, Integer> winningPathCount = new HashMap<>();
        for (Game game : games) {
            game.getWinner().ifPresent(winner -> {
                String path = getWinningPath(game, winner);
                winningPathCount.put(path,
                    1 + winningPathCount.getOrDefault(path, 0));
            });
        }
        return winningPathCount;
    }

    private static String getWinningPath(Game game, Player winner) {
        int stage1Count = getPublicVictoryPoints(game, winner.getUserID(), 1);
        int stage2Count = getPublicVictoryPoints(game, winner.getUserID(), 2);
        int secretCount = winner.getSecretVictoryPoints();
        int supportCount = winner.getSupportForTheThroneVictoryPoints();
        String others = getOtherVictoryPoints(game, winner.getUserID());
        return stage1Count + " stage 1s, " +
            stage2Count + " stage 2s, " +
            secretCount + " secrets, " +
            supportCount + " supports" +
            (others.isEmpty() ? "" : ", " + others);
    }

    private static int getPublicVictoryPoints(Game game, String userId, int stage) {
        Map<String, List<String>> scoredPOs = game.getScoredPublicObjectives();
        int vpCount = 0;
        for (Map.Entry<String, List<String>> scoredPublic : scoredPOs.entrySet()) {
            if (scoredPublic.getValue().contains(userId)) {
                String poID = scoredPublic.getKey();
                PublicObjectiveModel po = Mapper.getPublicObjective(poID);
                if (po != null && po.getPoints() == stage) {
                    vpCount += 1;
                }
            }
        }
        return vpCount;
    }

    private static String getOtherVictoryPoints(Game game, String userId) {
        Map<String, List<String>> scoredPOs = game.getScoredPublicObjectives();
        Map<String, Integer> otherVictoryPoints = new HashMap<>();
        for (Map.Entry<String, List<String>> scoredPOEntry : scoredPOs.entrySet()) {
            if (scoredPOEntry.getValue().contains(userId)) {
                String poID = scoredPOEntry.getKey();
                PublicObjectiveModel po = Mapper.getPublicObjective(poID);
                if (po == null) {
                    int frequency = Collections.frequency(scoredPOEntry.getValue(), userId);
                    otherVictoryPoints.put(normalizeOtherVictoryPoints(poID), frequency);
                }
            }
        }
        return otherVictoryPoints.keySet().stream()
            .sorted(Comparator.reverseOrder())
            .map(key -> otherVictoryPoints.get(key) + " " + key)
            .collect(Collectors.joining(", "));
    }

    private static String normalizeOtherVictoryPoints(String otherVictoryPoint) {
        otherVictoryPoint = otherVictoryPoint.toLowerCase().replaceAll("[^a-z]", "");
        if (otherVictoryPoint.contains("seed")) {
            otherVictoryPoint = "seed";
        } else if (otherVictoryPoint.contains("mutiny")) {
            otherVictoryPoint = "mutiny";
        } else if (otherVictoryPoint.contains("shard")) {
            otherVictoryPoint = "shard";
        } else if (otherVictoryPoint.contains("custodian")) {
            otherVictoryPoint = "custodian/imperial";
        } else if (otherVictoryPoint.contains("imperial")) {
            otherVictoryPoint = "imperial rider";
        } else if (otherVictoryPoint.contains("censure")) {
            otherVictoryPoint = "censure";
        } else if (otherVictoryPoint.contains("crown") || otherVictoryPoint.contains("emph")) {
            otherVictoryPoint = "crown";
        } else {
            otherVictoryPoint = "other (probably Classified Document Leaks)";
        }
        return otherVictoryPoint;
    }
}
