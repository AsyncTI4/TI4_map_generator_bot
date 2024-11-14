package ti4.commands.statistics;

import java.text.DecimalFormat;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.apache.commons.lang3.StringUtils;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.bothelper.ListSlashCommandsUsed;
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

import static org.apache.commons.lang3.StringUtils.isNotBlank;

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
            case PING_LIST -> listPingCounterList(event);
            case HIGHEST_SPENDERS -> calculateSpendToWinCorrellation(event);
            case GAME_LENGTH -> showGameLengths(event, null);
            case GAME_LENGTH_4MO -> showGameLengths(event, 120);
            case FACTIONS_PLAYED -> showMostPlayedFactions(event);
            case AVERAGE_TURNS -> showAverageTurnsInAGameByFaction(event);
            case COLOURS_PLAYED -> showMostPlayedColour(event);
            case FACTION_WINS -> showMostWinningFactions(event);
            case PHASE_TIMES -> showTimeOfRounds(event);
            case SOS_SCORED -> listScoredSOsPulledRelicsRevealedPOs(event);
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
        UNLEASH_THE_NAMES("Unleash the Names", "Show all the names of the games"), AVERAGE_TURNS("Average Turn Amount", "Show the average turns for a faction in a game"), PING_LIST("Ping List", "List of how many times people have been pinged"), HIGHEST_SPENDERS("List Highest Spenders", "Show stats for spending on CCs/plastics that bot has"), GAME_LENGTH("Game Length", "Show game lengths"), GAME_LENGTH_4MO("Game Length (past 4 months)",
            "Show game lengths from the past 4 months"), FACTIONS_PLAYED("Plays per Faction", "Show faction play count"), COLOURS_PLAYED("Plays per Colour",
                "Show colour play count"), FACTION_WINS("Wins per Faction",
                    "Show the wins per faction"), SOS_SCORED("Times an SO has been scored", "Show the amount of times each SO has been scored"), FACTION_WIN_PERCENT("Faction win percent", "Shows each faction's win percent rounded to the nearest integer"), COLOUR_WINS("Wins per Colour", "Show the wins per colour"),
        // UNFINISHED_GAMES("Unfinished games", "Show the games where at least 1 BP was scored but no winner was declared"),
        WINNING_PATH("Winners Path to Victory", "Shows a count of each game's path to victory"), PHASE_TIMES("Phase Times", "Shows how long each phase lasted, in days"), SUPPORT_WIN_COUNT("Wins with SftT", "Shows a count of wins that occurred with SftT"), GAME_COUNT("Total game count", "Shows the total game count");

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

    public static void listPingCounterList(SlashCommandInteractionEvent event) {
        Game reference = GameManager.getGame("finreference");
        Map<String, Integer> pings = new HashMap<>();
        for (String pingsFor : reference.getMessagesThatICheckedForAllReacts().keySet()) {

            if (pingsFor.contains("pingsFor")) {
                String userID = pingsFor.replace("pingsFor", "");
                User user = AsyncTI4DiscordBot.jda.getUserById(Long.parseLong(userID));
                if (user == null) {
                    continue;
                }
                pings.put(userID, Integer.parseInt(reference.getMessagesThatICheckedForAllReacts().get(pingsFor)));

            }
        }

        Map<String, Integer> topThousand = pings.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3000)
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        int index = 1;
        StringBuilder sb = new StringBuilder("List of times the player has hit the autoping threshold(aka the bots most wanted list)\n");
        for (String ket : topThousand.keySet()) {
            User user = AsyncTI4DiscordBot.jda.getUserById(Long.parseLong(ket));
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(user.getEffectiveName()).append(": ");
            sb.append(topThousand.get(ket)).append(" pings");
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Ping Counts", sb.toString());
    }

    public static void listScoredSOsPulledRelicsRevealedPOs(SlashCommandInteractionEvent event) {
        Map<String, Integer> sos = new HashMap<>();
        Map<String, Integer> publics = new HashMap<>();
        Map<String, Integer> relics = new HashMap<>();

        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        for (Game game : filteredGames) {
            for (Player player : game.getRealPlayers()) {
                for (String so : player.getSecretsScored().keySet()) {
                    if (Mapper.getSecretObjective(so) != null) {
                        String secret = Mapper.getSecretObjective(so).getName();
                        if (sos.containsKey(secret)) {
                            sos.put(secret, sos.get(secret) + 1);
                        } else {
                            sos.put(secret, 1);
                        }
                    }
                }
            }
            for (String po : game.getRevealedPublicObjectives().keySet()) {
                if (Mapper.getPublicObjective(po) != null) {
                    String publicO = Mapper.getPublicObjective(po).getName();
                    if (publics.containsKey(publicO)) {
                        publics.put(publicO, publics.get(publicO) + 1);
                    } else {
                        publics.put(publicO, 1);
                    }
                }
            }

            List<String> relicsNames = Mapper.getDecks().get(game.getRelicDeckID()).getNewShuffledDeck();
            for (String relic : relicsNames) {
                if (!game.getAllRelics().contains(relic)) {
                    String relicName = Mapper.getRelic(relic).getName();
                    if (relics.containsKey(relicName)) {
                        relics.put(relicName, relics.get(relicName) + 1);
                    } else {
                        relics.put(relicName, 1);
                    }
                }
            }
        }

        Map<String, Integer> topThousand = sos.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3000)
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        int index = 1;
        StringBuilder sb = new StringBuilder("List of times a particular secret has been scored\n");
        for (String ket : topThousand.keySet()) {

            sb.append("`").append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(ket).append(": ");
            sb.append(topThousand.get(ket));
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Secret Score Counts", sb.toString());

        topThousand = publics.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3000)
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        index = 1;
        sb = new StringBuilder("List of times a particular public has been revealed \n");
        for (String ket : topThousand.keySet()) {

            sb.append("`").append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(ket).append(": ");
            sb.append(topThousand.get(ket));
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Public Objectives Revealed", sb.toString());

        topThousand = relics.entrySet().stream().sorted(Collections.reverseOrder(Map.Entry.comparingByValue())).limit(3000)
            .collect(Collectors.toMap(
                Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
        index = 1;
        sb = new StringBuilder("List of times a particular relic has been drawn \n");
        for (String ket : topThousand.keySet()) {

            sb.append("`").append(Helper.leftpad(String.valueOf(index), 4)).append(". ");
            sb.append("` ").append(ket).append(": ");
            sb.append(topThousand.get(ket));
            sb.append("\n");
            index++;
        }
        MessageHelper.sendMessageToThread(event.getChannel(), "Relics Drawn Count", sb.toString());
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

        List<Game> games = GameManager.getGameNameToGame().values().stream()
            .filter(allFilterPredicates)
            .sorted(mapSort)
            .toList();
        return !games.isEmpty();
    }

    public static int numberOfPlayersUnfinishedGames(String userID) {
        Predicate<Game> ignoreSpectateFilter = game -> game.getRealPlayerIDs().contains(userID);
        Predicate<Game> endedGamesFilter = game -> game.isHasEnded() && game.getWinner().isEmpty() && game.getHighestScore() > 0;
        Predicate<Game> allFilterPredicates = endedGamesFilter.and(ignoreSpectateFilter);

        Comparator<Game> mapSort = Comparator.comparing(Game::getGameNameForSorting);

        List<Game> games = GameManager.getGameNameToGame().values().stream()
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

        List<Game> games = GameManager.getGameNameToGame().values().stream()
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
                && Helper.getDateDifference(game.getEndedDateString(), Helper.getDateRepresentation(System.currentTimeMillis())) < pastDays) {
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
        Map<String, Integer> custodians = new HashMap<>();
        Map<String, Game> mapList = GameManager.getGameNameToGame();
        for (Game game : mapList.values()) {
            for (Player player : game.getRealAndEliminatedAndDummyPlayers()) {
                String faction = player.getFaction();
                factionCount.put(faction,
                    1 + factionCount.getOrDefault(faction, 0));
                if (game.getCustodiansTaker() != null && game.getCustodiansTaker().equalsIgnoreCase(faction)) {
                    if (custodians.containsKey(faction)) {
                        custodians.put(faction, custodians.get(faction) + 1);
                    } else {
                        custodians.put(faction, 1);
                    }
                }
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
                .append(entry.getKey().getFactionNameWithSourceEmoji()).append(" (Took Custodians a total of  ").append(custodians.getOrDefault(entry.getKey().getAlias(), 0)).append(" times, or ").append((float) custodians.getOrDefault(entry.getKey().getAlias(), 0) / entry.getValue()).append(")")
                .append("\n"));
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Plays per Faction", sb.toString());
    }

    private static void showAverageTurnsInAGameByFaction(SlashCommandInteractionEvent event) {
        Map<String, Double> factionCount = new HashMap<>();
        Map<String, Double> factionTurnCount = new HashMap<>();

        List<Game> mapList = GameStatisticFilterer.getFilteredGames(event);
        for (Game game : mapList) {
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

    public static String convertMillisecondsToDays(float milliseconds) {
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
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
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

    private static void showMostWinningColour(GenericInteractionCreateEvent event) {
        Map<String, Integer> winnerColorCount = new HashMap<>();
        Map<String, Game> mapList = GameManager.getGameNameToGame();
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
