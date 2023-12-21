package ti4.commands.statistics;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;

import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
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

public class OtherStats extends StatisticsSubcommandData {

    public OtherStats() {
        super(Constants.OTHER, "Other Various Statistics");
        addOptions(new OptionData(OptionType.STRING, Constants.STATISTIC, "Choose a stat to show").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String statisticToShow = event.getOption(Constants.STATISTIC, null, OptionMapping::getAsString);
        SimpleStatistics stat = SimpleStatistics.fromString(statisticToShow);
        if (stat == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
            return;
        }
        switch (stat) {
            case UNLEASH_THE_NAMES -> sendAllNames(event);
            case UNLEASH_THE_NAMES_DS -> sendAllNames(event, true, false);
            case UNLEASH_THE_NAMES_ABSOL -> sendAllNames(event, false, true);
            case GAME_LENGTH -> showGameLengths(event, null);
            case GAME_LENGTH_4MO -> showGameLengths(event, 120);
            case FACTIONS_PLAYED -> showMostPlayedFactions(event);
            case COLOURS_PLAYED -> showMostPlayedColour(event);
            case FACTION_WINS -> showMostWinningFactions(event);
            case FACTION_WIN_PERCENT -> showFactionWinPercent(event);
            case COLOUR_WINS -> showMostWinningColour(event);
            default -> MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown Statistic: " + statisticToShow);
        }
    }

    /**
     * Represents a simple statistic.
     * Just add a new enum for every statistic and it will handle the autocomplete for you.
     */
    public enum SimpleStatistics {
        // Add your new statistic here
        UNLEASH_THE_NAMES("Unleash the Names", "Show all the names of the games"),
        UNLEASH_THE_NAMES_DS("Unleash the Names DS", "Show all the names of the DS games"),
        UNLEASH_THE_NAMES_ABSOL("Unleash the Names Absol", "Show all the names of Absol games"),
        GAME_LENGTH("Game Length", "Show game lengths"),
        GAME_LENGTH_4MO("Game Length (past 4 months)", "Show game lengths from the past 4 months"),
        FACTIONS_PLAYED("Plays per Faction", "Show faction play count"),
        COLOURS_PLAYED("Plays per Colour", "Show colour play count"),
        FACTION_WINS("Wins per Faction", "Show the wins per faction"),
        FACTION_WIN_PERCENT("Faction win percent", "Shows each faction's win percent rounded to the nearest integer"),
        COLOUR_WINS("Wins per Colour", "Show the wins per colour");
    
        private final String name;
        private final String description;
    
        SimpleStatistics(String name, String description) {
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
        public static SimpleStatistics fromString(String id) {
            for (SimpleStatistics stat : values()) {
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

    // Add new statistic methods here
    public static void sendAllNames(GenericInteractionCreateEvent event) {
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        StringBuilder names = new StringBuilder();
        int num = 0;

        for (Game activeGame : mapList.values()) {
            if (activeGame.getCustomName() != null && !activeGame.getCustomName().isEmpty()) {
                num++;
                names.append(num).append(". ").append(activeGame.getCustomName())
                    .append(" (").append(activeGame.getName()).append(")\n");
            }
        }
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Game Names", names.toString());
    }

    public static void sendAllNames(GenericInteractionCreateEvent event, boolean ds, boolean absol) {
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        StringBuilder names = new StringBuilder();
        int num = 0;

        for (Game activeGame : mapList.values()) {
            if ((ds && activeGame.isDiscordantStarsMode()) || (absol && activeGame.isAbsolMode())) {
                num++;
                names.append(num).append(". ").append(activeGame.getCustomName())
                    .append(" (").append(activeGame.getName()).append(")\n");
            }
        }
        MessageHelper.sendMessageToThread((MessageChannelUnion) event.getMessageChannel(), "Game Names", names.toString());
    }

    public static void showGameLengths(GenericInteractionCreateEvent event, Integer pastDays) {
        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        if (pastDays == null) pastDays = 3650;
        int num = 0;
        int total = 0;
        Map<String, Integer> endedGames = new HashMap<>();
        for (Game activeGame : mapList.values()) {
            if (activeGame.isHasEnded() && activeGame.getGameWinner().isPresent() && activeGame.getRealPlayers().size() > 2 && (Helper.getDateDifference(activeGame.getEndedDateString(), Helper.getDateRepresentation(new Date().getTime())) < pastDays || pastDays > 120)) {
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
            for (Player player : game.getPlayers().values()) {
                String color = player.getColor();
                String faction = player.getFaction();
                if (faction != null && color != null && !faction.isEmpty() && !"null".equals(faction)) {
                    factionCount.putIfAbsent(faction, 1);
                    factionCount.computeIfPresent(faction, (key, integer) -> integer + 1);
                }
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

    private static void showMostWinningFactions(GenericInteractionCreateEvent event) {
        Map<String, Integer> winnerFactionCount = new HashMap<>();

        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game game : mapList.values()) {
            int vp = game.getVp();
            boolean findWinner = true;
            for (Player player : game.getPlayers().values()) {
                int vpScore = player.getTotalVictoryPoints();
                if (vp <= vpScore) {
                    String faction = player.getFaction();
                    winnerFactionCount.put(faction, 1 + winnerFactionCount.getOrDefault(faction, 0));

                    findWinner = false;
                }
            }
            if (findWinner) {
                Date date = new Date(game.getLastModifiedDate());
                Date currentDate = new Date();
                long time_difference = currentDate.getTime() - date.getTime();
                // Calculate time difference in days
                long days_difference = (time_difference / (1000 * 60 * 60 * 24)) % 365;
                if (days_difference > 30) {
                    int maxVP = game.getPlayers().values().stream().map(Player::getTotalVictoryPoints).max(Integer::compareTo).orElse(0);
                    if (game.getPlayers().values().stream().map(Player::getTotalVictoryPoints).filter(value -> value.equals(maxVP)).count() == 1) {
                        game.getPlayers().values().stream()
                            .filter(player -> player.getTotalVictoryPoints() == maxVP)
                            .findFirst()
                            .ifPresent(player -> {
                                String faction = player.getFaction();
                                winnerFactionCount.put(faction,
                                    1 + winnerFactionCount.getOrDefault(faction, 0));
                            });
                    }
                }
            }
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

    private static void showFactionWinPercent(GenericInteractionCreateEvent event) {
        Map<String, Integer> factionWinCount = new HashMap<>();
        Map<String, Integer> factionGameCount = new HashMap<>();
        for (Game game : GameManager.getInstance().getGameNameToGame().values()) {
            Player winner = getWinner(game);
            if (winner == null) {
                continue;
            }
            String winningFaction = winner.getFaction();
            factionWinCount.put(winningFaction,
                1 + factionWinCount.getOrDefault(winningFaction, 0));

            game.getPlayers().values().forEach(player -> {
                String faction = player.getFaction();
                factionGameCount.put(faction,
                    1 + factionGameCount.getOrDefault(faction, 0));
            });
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Faction Win Percent:").append("\n");

        Mapper.getFactions().stream()
            .filter(factionModel -> factionModel.getSource().isPok())
            .map(faction -> {
                double winCount = factionWinCount.getOrDefault(faction.getAlias(), 0);
                double gameCount = factionGameCount.getOrDefault(faction.getAlias(), 0);
                return Map.entry(faction, gameCount == 0 ? 0 : Math.round(100 * winCount / gameCount));
            })
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

    private static Player getWinner(Game game) {
        Player winner = null;
        for (Player player : game.getPlayers().values()) {
            if (game.getVp() <= player.getTotalVictoryPoints()) {
                if (winner == null) {
                    winner = player;
                } else if (isNotEmpty(player.getSCs()) && isNotEmpty(winner.getSCs())) {
                    winner = getLowestInitiativePlayer(player, winner);
                } else {
                    return null;
                }
            }
        }
        return winner;
    }

    private static Player getLowestInitiativePlayer(Player player1, Player player2) {
        if (Collections.min(player1.getSCs()) < Collections.min(player2.getSCs())) {
            return player1;
        }
        return player2;
    }

    private static void showMostPlayedColour(GenericInteractionCreateEvent event) {
        Map<String, Integer> colorCount = new HashMap<>();

        Map<String, Game> mapList = GameManager.getInstance().getGameNameToGame();
        for (Game game : mapList.values()) {
            for (Player player : game.getPlayers().values()) {
                String color = player.getColor();
                String faction = player.getFaction();
                if (faction != null && color != null && !faction.isEmpty() && !"null".equals(faction)) {
                    colorCount.put(color,
                        1 + colorCount.getOrDefault(color, 0));
                }
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
            int vp = game.getVp();
            boolean findWinner = true;
            for (Player player : game.getPlayers().values()) {
                int vpScore = player.getTotalVictoryPoints();
                if (vp <= vpScore) {
                    String color = player.getColor();

                    winnerColorCount.put(color, 1 + winnerColorCount.getOrDefault(color, 0));

                    findWinner = false;
                }
            }
            if (findWinner) {
                Date date = new Date(game.getLastModifiedDate());
                Date currentDate = new Date();
                long time_difference = currentDate.getTime() - date.getTime();
                // Calculate time difference in days
                long days_difference = (time_difference / (1000 * 60 * 60 * 24)) % 365;
                if (days_difference > 30) {
                    int maxVP = game.getPlayers().values().stream().map(Player::getTotalVictoryPoints).max(Integer::compareTo).orElse(0);
                    if (game.getPlayers().values().stream().map(Player::getTotalVictoryPoints).filter(value -> value.equals(maxVP)).count() == 1) {
                        game.getPlayers().values().stream()
                            .filter(player -> player.getTotalVictoryPoints() == maxVP)
                            .findFirst()
                            .ifPresent(player -> {
                                String color = player.getColor();
                                winnerColorCount.put(color, 1 + winnerColorCount.getOrDefault(color, 0));
                            });
                    }
                }
            }
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
