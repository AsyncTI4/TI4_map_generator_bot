package ti4.commands.statistics;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.commands.bothelper.ListSlashCommandsUsed;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.FactionModel;
import ti4.model.PublicObjectiveModel;

public class GameStats extends StatisticsSubcommandData {

    private static final String PLAYER_COUNT_FILTER = "player_count";
    private static final String VICTORY_POINT_GOAL_FILTER = "victory_point_goal";
    private static final String GAME_TYPE_FILTER = "game_type";
    private static final String FOG_FILTER = "is_fog";
    private static final String HOMEBREW_FILTER = "has_homebrew";
    private static final String HAS_WINNER_FILTER = "has_winner";

    public GameStats() {
        super(Constants.GAMES, "Game Statistics");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_STATISTIC, "Choose a stat to show").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.INTEGER, PLAYER_COUNT_FILTER, "Filter by player count, e.g. 3-8"));
        addOptions(new OptionData(OptionType.INTEGER, VICTORY_POINT_GOAL_FILTER, "Filter by victory point goal, e.g. 10-14"));
        addOptions(new OptionData(OptionType.STRING, GAME_TYPE_FILTER, "Filter by game type, e.g. base, pok, absol, ds, action_deck_2, little_omega"));
        addOptions(new OptionData(OptionType.BOOLEAN, FOG_FILTER, "Filter by if the game is a fog game"));
        addOptions(new OptionData(OptionType.BOOLEAN, HOMEBREW_FILTER, "Filter by if the game has any homebrew"));
        addOptions(new OptionData(OptionType.BOOLEAN, HAS_WINNER_FILTER, "Filter by if the game has a winner"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION, "Faction That You Want Tech History Of").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String statisticToShow = event.getOption(Constants.GAME_STATISTIC, null, OptionMapping::getAsString);
        GameStatistics stat = GameStatistics.fromString(statisticToShow);
        if (stat == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
            return;
        }
        switch (stat) {
            case UNLEASH_THE_NAMES -> sendAllNames(event);
            case HIGHEST_SPENDERS -> calculateSpendToWinCorrellation(event);
            case GAME_LENGTH -> showGameLengths(event, null);
            case GAME_LENGTH_4MO -> showGameLengths(event, 120);
            case FACTIONS_PLAYED -> showMostPlayedFactions(event);
            case COLOURS_PLAYED -> showMostPlayedColour(event);
            case FACTION_WINS -> showMostWinningFactions(event);
            //case UNFINISHED_GAMES -> findHowManyUnfinishedGamesAreDueToNewPlayers(event);
            case FACTION_WIN_PERCENT -> showFactionWinPercent(event);
            case COLOUR_WINS -> showMostWinningColour(event);
            case GAME_COUNT -> showGameCount(event);
            case WINNING_PATH -> showWinningPath(event);
            case SUPPORT_WIN_COUNT -> showWinsWithSupport(event);

            // case WINNING_PATH_NAMES
            default -> MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
        }
    }

    /**
     * Represents a simple statistic.
     * Just add a new enum for every statistic and it will handle the autocomplete for you.
     */
    public enum GameStatistics {
        // Add your new statistic here
        UNLEASH_THE_NAMES("Unleash the Names", "Show all the names of the games"), HIGHEST_SPENDERS("List Highest Spenders", "Show stats for spending on CCs/plastics that bot has"), GAME_LENGTH("Game Length", "Show game lengths"), GAME_LENGTH_4MO("Game Length (past 4 months)", "Show game lengths from the past 4 months"), FACTIONS_PLAYED("Plays per Faction", "Show faction play count"), COLOURS_PLAYED("Plays per Colour", "Show colour play count"), FACTION_WINS("Wins per Faction",
            "Show the wins per faction"), FACTION_WIN_PERCENT("Faction win percent", "Shows each faction's win percent rounded to the nearest integer"), COLOUR_WINS("Wins per Colour", "Show the wins per colour"),
        // UNFINISHED_GAMES("Unfinished games", "Show the games where at least 1 pt was scored but no winner was declared"),
        WINNING_PATH("Winners Path to Victory", "Shows a count of each game's path to victory"), SUPPORT_WIN_COUNT("Wins with SftT", "Shows a count of wins that occurred with SftT"), GAME_COUNT("Total game count", "Shows the total game count");

        private final String name;
        private final String description;

        GameStatistics(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String toString() {
            return super.toString().toLowerCase();
        }

        /**
         * Converts a string identifier to the corresponding SimpleStatistics enum value.
         * 
         * @param id the string identifier
         * @return the SimpleStatistics enum value, or null if not found
         */
        public static GameStatistics fromString(String id) {
            for (GameStatistics stat : values()) {
                if (id.equals(stat.toString())) {
                    return stat;
                }
            }
            return null;
        }

        /**
         * Gets the name and description of the statistic for auto-complete suggestions.
         * 
         * @return the auto-complete name
         */
        public String getAutoCompleteName() {
            return name + ": " + description;
        }

        /**
         * Searches for a given string within the name, description, or string representation of the statistic.
         * 
         * @param searchString the string to search for
         * @return true if the string is found, false otherwise
         */
        public boolean search(String searchString) {
            return name.toLowerCase().contains(searchString) || description.toLowerCase().contains(searchString) || toString().contains(searchString);
        }
    }

    public static void sendAllNames(SlashCommandInteractionEvent event) {
        StringBuilder names = new StringBuilder();
        int num = 0;
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        for (Game game : filteredGames) {
            num++;
            names.append(num).append(". ").append(game.getName());
            if (isNotBlank(game.getCustomName())) {
                names.append(" (").append(game.getCustomName()).append(")");
            }
            names.append("\n");
        }
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Game Names", names.toString());
    }

    public static void calculateSpendToWinCorrellation(SlashCommandInteractionEvent event) {
        StringBuilder names = new StringBuilder();
        int num = 0;
        int gamesWhereHighestWon = 0;
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        for (Game game : filteredGames) {
            if (game.getWinner().isEmpty()) {
                continue;
            }

            int highest = 0;
            Player winner = game.getWinner().get();
            Player highestP = null;
            for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
                if (player.getTotalExpenses() > highest) {
                    highestP = player;
                    highest = player.getTotalExpenses();
                }
                if (player.getTotalExpenses() < 20) {
                    highestP = null;
                    break;
                }
            }
            if (highestP != null) {
                num++;
                names.append(num).append(". ").append(game.getName());
                names.append(" - Winner was ").append(winner.getFactionEmoji()).append(" (").append("Highest was ").append(highestP.getFactionEmoji()).append(" at ").append(highestP.getTotalExpenses()).append(")");
                names.append("\n");
                if (highestP == winner) {
                    gamesWhereHighestWon++;
                }
            }
        }
        names.append("Total games where highest spender won was ").append(gamesWhereHighestWon).append(" out of ").append(num);
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Game Expenses", names.toString());
    }

    public static boolean hasPlayerFinishedAGame(Player player) {
        String userID = player.getUserID();

        Predicate<Game> ignoreSpectateFilter = game -> game.getRealPlayerIDs().contains(userID);
        Predicate<Game> endedGamesFilter = game -> game.getWinner().isPresent();
        Predicate<Game> allFilterPredicates = endedGamesFilter.and(ignoreSpectateFilter);

        Comparator<Game> mapSort = Comparator.comparing(Game::getGameNameForSorting);

        List<Game> games = GameManager.getInstance().getGameNameToGame().values().stream()
            .filter(allFilterPredicates)
            .sorted(mapSort)
            .toList();
        return games.size() > 0;
    }

    public static int numberOfPlayersUnfinishedGames(String userID) {
        Predicate<Game> ignoreSpectateFilter = game -> game.getRealPlayerIDs().contains(userID);
        Predicate<Game> endedGamesFilter = game -> game.isHasEnded() && game.getWinner().isEmpty() && game.getHighestScore() > 0;
        Predicate<Game> allFilterPredicates = endedGamesFilter.and(ignoreSpectateFilter);

        Comparator<Game> mapSort = Comparator.comparing(Game::getGameNameForSorting);

        List<Game> games = GameManager.getInstance().getGameNameToGame().values().stream()
            .filter(allFilterPredicates)
            .sorted(mapSort)
            .toList();
        return games.size();
    }

    public static void findHowManyUnfinishedGamesAreDueToNewPlayers(SlashCommandInteractionEvent event) {
        StringBuilder names = new StringBuilder();
        int num = 0;
        Predicate<Game> allFilterPredicates = game1 -> game1.isHasEnded() && game1.getWinner().isEmpty() && game1.getHighestScore() > 0;

        Comparator<Game> mapSort = Comparator.comparing(Game::getGameNameForSorting);

        List<Game> games = GameManager.getInstance().getGameNameToGame().values().stream()
            .filter(allFilterPredicates)
            .sorted(mapSort)
            .toList();
        for (Game game : games) {
            num++;
            names.append(num).append(". ").append(game.getName());
            if (isNotBlank(game.getCustomName())) {
                names.append(" (").append(game.getCustomName()).append(")");
            }
            for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
                if (!hasPlayerFinishedAGame(player)) {
                    names.append(" ").append(player.getUserName()).append(" had not finished any games and had ").append(numberOfPlayersUnfinishedGames(player.getUserID())).append(" unfinished games. ");
                }
            }
            names.append("\n");
        }
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Game Names", names.toString());
    }

    public static void showGameLengths(SlashCommandInteractionEvent event, Integer pastDays) {
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        if (pastDays == null) pastDays = 3650;
        int num = 0;
        int total = 0;
        Map<String, Integer> endedGames = new HashMap<>();
        for (Game game : filteredGames) {
            if (game.isHasEnded() && game.getWinner().isPresent() && game.getPlayerCountForMap() > 2
                && Helper.getDateDifference(game.getEndedDateString(), Helper.getDateRepresentation(new Date().getTime())) < pastDays) {
                num++;
                int dif = Helper.getDateDifference(game.getCreationDate(), game.getEndedDateString());
                endedGames.put(game.getName() + " (" + game.getPlayerCountForMap() + "p, " + game.getVp() + "pt)", dif);
                total = total + dif;
            }
        }
        StringBuilder longMsg = new StringBuilder("The number of games that finished in the last " + pastDays + " days is " + num + ". They are listed below based on the number of days it took to complete\n");
        Map<String, Integer> sortedMapAsc = ListSlashCommandsUsed.sortByValue(endedGames, false);
        int num2 = 0;
        for (String command : sortedMapAsc.keySet()) {
            num2++;
            longMsg.append(num2).append(". ").append(command).append(": ")
                .append(sortedMapAsc.get(command)).append(" \n");
        }
        longMsg.append("\n The average completion time of these games is: ").append(total / num).append("\n");
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Game Lengths", longMsg.toString());
    }

    private static void showMostPlayedFactions(GenericInteractionCreateEvent event) {
        Map<String, Integer> factionCount = new HashMap<>();

        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game game : mapList.values()) {
            for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
                String faction = player.getFaction();
                factionCount.put(faction,
                    1 + factionCount.getOrDefault(faction, 0));
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Plays per Faction:").append("\n");
        factionCount.entrySet().stream()
            .filter(entry -> Mapper.isValidFaction(entry.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("x` ")
                .append(entry.getKey().getFactionEmoji()).append(" ")
                .append(entry.getKey().getFactionNameWithSourceEmoji())
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Plays per Faction", sb.toString());
    }

    private static void showGameCount(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getMessageChannel(),
            "Game count: " + GameStatisticFilterer.getFilteredGames(event).size());
    }

    private static void showMostWinningFactions(SlashCommandInteractionEvent event) {
        Map<String, Integer> winnerFactionCount = new HashMap<>();
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
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
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();
        for (Game game : filteredGames) {
            Optional<Player> winner = game.getWinner();
            if (winner.isEmpty()) {
                continue;
            }
            String winningFaction = winner.get().getFaction();
            factionWinCount.put(winningFaction,
                1 + factionWinCount.getOrDefault(winningFaction, 0));

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
    }

    private static void showMostPlayedColour(SlashCommandInteractionEvent event) {
        Map<String, Integer> colorCount = new HashMap<>();
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
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

    private static void showMostWinningColour(GenericInteractionCreateEvent event) {
        Map<String, Integer> winnerColorCount = new HashMap<>();
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game game : mapList.values()) {
            Optional<Player> winner = game.getWinner();
            if (winner.isEmpty()) {
                continue;
            }
            String winningColor = winner.get().getColor();
            winnerColorCount.put(winningColor,
                1 + winnerColorCount.getOrDefault(winningColor, 0));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Wins per Colour:").append("\n");
        winnerColorCount.entrySet().stream()
            .filter(e -> Mapper.isValidColor(e.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .forEach(entry -> sb.append("`")
                .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                .append("x` ")
                .append(Emojis.getColorEmojiWithName(entry.getKey()))
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Wins per Colour", sb.toString());
    }

    public static void showWinningPath(SlashCommandInteractionEvent event) {
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        Map<String, Integer> winningPathCount = getAllWinningPathCounts(filteredGames);
        int gamesWithWinnerCount = winningPathCount.values().stream().reduce(0, Integer::sum);
        AtomicInteger atomicInteger = new AtomicInteger(0);
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

    public static Map<String, Integer> getAllWinningPathCounts(List<Game> games) {
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

    public static String getWinningPath(Game game, Player winner) {
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

    public static void showWinsWithSupport(SlashCommandInteractionEvent event) {
        Map<Integer, Integer> supportWinCount = new HashMap<>();
        AtomicInteger gameWithWinnerCount = new AtomicInteger();
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        for (Game game : filteredGames) {
            game.getWinner().ifPresent(winner -> {
                gameWithWinnerCount.getAndIncrement();
                int supportCount = winner.getSupportForTheThroneVictoryPoints();
                supportWinCount.put(supportCount,
                    1 + supportWinCount.getOrDefault(supportCount, 0));
            });
        }
        AtomicInteger atomicInteger = new AtomicInteger(0);
        StringBuilder sb = new StringBuilder();
        sb.append("__**Winning Paths With SftT Count:**__").append("\n");
        supportWinCount.entrySet().stream()
            .sorted(Map.Entry.<Integer, Integer>comparingByValue().reversed())
            .forEach(entry -> sb.append(atomicInteger.getAndIncrement() + 1)
                .append(". `")
                .append(entry.getValue().toString())
                .append(" (")
                .append(Math.round(100 * entry.getValue() / (double) gameWithWinnerCount.get()))
                .append("%)` ")
                .append(entry.getKey())
                .append(" SftT wins")
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "SftT wins", sb.toString());
    }

}
