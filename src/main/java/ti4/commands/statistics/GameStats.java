package ti4.commands.statistics;

import static org.apache.commons.lang3.StringUtils.isNotBlank;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            case GAME_LENGTH -> showGameLengths(event, null);
            case GAME_LENGTH_4MO -> showGameLengths(event, 120);
            case FACTIONS_PLAYED -> showMostPlayedFactions(event);
            case COLOURS_PLAYED -> showMostPlayedColour(event);
            case FACTION_WINS -> showMostWinningFactions(event);
            case FACTION_WIN_PERCENT -> showFactionWinPercent(event);
            case COLOUR_WINS -> showMostWinningColour(event);
            case GAME_COUNT -> showGameCount(event);
            default -> MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
        }
    }

    /**
     * Represents a simple statistic.
     * Just add a new enum for every statistic and it will handle the autocomplete for you.
     */
    public enum GameStatistics {
        // Add your new statistic here
        UNLEASH_THE_NAMES("Unleash the Names", "Show all the names of the games"),
        GAME_LENGTH("Game Length", "Show game lengths"),
        GAME_LENGTH_4MO("Game Length (past 4 months)", "Show game lengths from the past 4 months"),
        FACTIONS_PLAYED("Plays per Faction", "Show faction play count"),
        COLOURS_PLAYED("Plays per Colour", "Show colour play count"),
        FACTION_WINS("Wins per Faction", "Show the wins per faction"),
        FACTION_WIN_PERCENT("Faction win percent", "Shows each faction's win percent rounded to the nearest integer"),
        COLOUR_WINS("Wins per Colour", "Show the wins per colour"),
        GAME_COUNT("Total game count", "Shows the total game count");
    
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
         * @return the auto-complete name
         */
        public String getAutoCompleteName() {
            return name + ": " + description;
        }

        /**
         * Searches for a given string within the name, description, or string representation of the statistic.
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

    public static void showGameLengths(SlashCommandInteractionEvent event, Integer pastDays) {
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        if (pastDays == null) pastDays = 3650;
        int num = 0;
        int total = 0;
        Map<String, Integer> endedGames = new HashMap<>();
        for (Game activeGame : filteredGames) {
            if (activeGame.isHasEnded() && activeGame.getGameWinner().isPresent() && activeGame.getRealPlayers().size() > 2
                && Helper.getDateDifference(activeGame.getEndedDateString(), Helper.getDateRepresentation(new Date().getTime())) < pastDays) {
                num++;
                int dif = Helper.getDateDifference(activeGame.getCreationDate(), activeGame.getEndedDateString());
                endedGames.put(activeGame.getName() + " ("+activeGame.getRealPlayers().size()+"p, "+activeGame.getVp()+"pt)", dif);
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
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Game Lengths" , longMsg.toString());
    }

    private static void showMostPlayedFactions(GenericInteractionCreateEvent event) {
        Map<String, Integer> factionCount = new HashMap<>();

        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game game : mapList.values()) {
            for (Player player : game.getRealPlayers()) {
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
            .forEach(entry -> 
                sb.append("`")
                    .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                    .append("x` ")
                    .append(entry.getKey().getFactionEmoji()).append(" ")
                    .append(entry.getKey().getFactionNameWithSourceEmoji())
                    .append("\n")
                );
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
            Player winner = GameStatisticFilterer.getWinner(game);
            if (winner == null) {
                continue;
            }
            winnerFactionCount.put(winner.getFaction(),
                1 + winnerFactionCount.getOrDefault(winner.getFaction(), 0));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Wins per Faction:").append("\n");
        winnerFactionCount.entrySet().stream()
            .filter(entry -> Mapper.isValidFaction(entry.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .map(entry -> Map.entry(Mapper.getFaction(entry.getKey()), entry.getValue()))
            .forEach(entry -> 
                sb.append("`")
                    .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                    .append("x` ")
                    .append(entry.getKey().getFactionEmoji()).append(" ")
                    .append(entry.getKey().getFactionNameWithSourceEmoji())
                    .append("\n")
                );
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Wins per Faction", sb.toString());
    }

    private static void showFactionWinPercent(SlashCommandInteractionEvent event) {
        List<Game> filteredGames = GameStatisticFilterer.getFilteredGames(event);
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();
        for (Game game : filteredGames) {
            Player winner = GameStatisticFilterer.getWinner(game);
            if (winner == null) {
                continue;
            }
            String winningFaction = winner.getFaction();
            factionWinCount.put(winningFaction,
                1 + factionWinCount.getOrDefault(winningFaction, 0));

            game.getRealPlayers().forEach(player -> {
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
            .forEach(entry ->
                sb.append("`")
                    .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                    .append("%` (")
                    .append(factionGameCount.getOrDefault(entry.getKey().getAlias(), 0))
                    .append(" games) ")
                    .append(entry.getKey().getFactionEmoji()).append(" ")
                    .append(entry.getKey().getFactionNameWithSourceEmoji())
                    .append("\n")
            );
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
            .forEach(entry -> 
                sb.append("`")
                    .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                    .append("x` ")
                    .append(Emojis.getColorEmojiWithName(entry.getKey()))
                    .append("\n")
                );
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Plays per Colour", sb.toString());
    }

    private static void showMostWinningColour(GenericInteractionCreateEvent event) {
        Map<String, Integer> winnerColorCount = new HashMap<>();
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game game : mapList.values()) {
            Player winner = GameStatisticFilterer.getWinner(game);
            if (winner == null) {
                continue;
            }
            winnerColorCount.put(winner.getColor(),
                1 + winnerColorCount.getOrDefault(winner.getColor(), 0));
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Wins per Colour:").append("\n");
        winnerColorCount.entrySet().stream()
            .filter(e -> Mapper.isValidColor(e.getKey()))
            .sorted(Map.Entry.comparingByValue())
            .forEach(entry -> 
                sb.append("`")
                    .append(StringUtils.leftPad(entry.getValue().toString(), 4))
                    .append("x` ")
                    .append(Emojis.getColorEmojiWithName(entry.getKey()))
                    .append("\n")
                );
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Wins per Colour", sb.toString());
    }
}
