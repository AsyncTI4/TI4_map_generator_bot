package ti4.commands.statistics;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.GameManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class MedianTurnTime extends StatisticsSubcommandData {

    public MedianTurnTime() {
        super(Constants.MEDIAN_TURN_TIME, "Median turn time accross all games for all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MINIMUM_NUMBER_OF_TURNS, "Minimum number of turns to show (Default = 1)").setRequired(false));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IGNORE_ENDED_GAMES, "True to exclude ended games from the calculation (default = false)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getAverageTurnTimeText(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Median Turn Time", text);
    }

    private String getAverageTurnTimeText(SlashCommandInteractionEvent event) {
        Map<String, Game> maps = GameManager.getInstance().getGameNameToGame();

        Map<String, Integer> playerTurnCount = new HashMap<>();

        Map<String, Set<Long>> playerAverageTurnTimes = new HashMap<>();

        boolean ignoreEndedGames = event.getOption(Constants.IGNORE_ENDED_GAMES, false, OptionMapping::getAsBoolean);
        Predicate<Game> endedGamesFilter = ignoreEndedGames ? m -> !m.isHasEnded() : m -> true;

        for (Game game : maps.values().stream().filter(endedGamesFilter).toList()) {
            for (Player player : game.getPlayers().values()) {
                Entry<Integer, Long> playerTurnTime = Map.entry(player.getNumberTurns(), player.getTotalTurnTime());
                if (playerTurnTime.getKey() == 0) continue;
                Long averageTurnTime = playerTurnTime.getValue() / playerTurnTime.getKey();
                playerAverageTurnTimes.compute(player.getUserID(), (key, value) -> {
                    if (value == null) value = new HashSet<>();
                    value.add(averageTurnTime);
                    return value;
                });
                playerTurnCount.merge(player.getUserID(), playerTurnTime.getKey(), Integer::sum);
            }
        }

        Map<String, Long> playerMedianTurnTimes = playerAverageTurnTimes.entrySet().stream().map(e -> Map.entry(e.getKey(), Helper.median(e.getValue().stream().sorted().toList()))).collect(Collectors.toMap(Entry::getKey, Entry::getValue, (oldEntry, newEntry) -> oldEntry, HashMap::new));
        StringBuilder sb = new StringBuilder();

        sb.append("## __**Median Turn Time:**__\n");
        
        int index = 1;
        int minimumTurnsToShow = event.getOption(Constants.MINIMUM_NUMBER_OF_TURNS, 1, OptionMapping::getAsInt);

        int topLimit = event.getOption(Constants.TOP_LIMIT, 50, OptionMapping::getAsInt);
        for (Entry<String, Long> userMedianTurnTime : playerMedianTurnTimes.entrySet().stream()
            .filter(o -> o.getValue() != 0 && playerTurnCount.get(o.getKey()) >= minimumTurnsToShow)
            .sorted(Entry.comparingByValue()).limit(topLimit).toList()) {
            User user = AsyncTI4DiscordBot.jda.getUserById(userMedianTurnTime.getKey());
            long totalMillis = userMedianTurnTime.getValue();
            int turnCount = playerTurnCount.get(userMedianTurnTime.getKey());

            if (user == null || turnCount == 0 || totalMillis == 0) continue;
            
            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            sb.append(Helper.getTimeRepresentationToSeconds(userMedianTurnTime.getValue()));
            sb.append("` ").append(user.getEffectiveName());
            sb.append("   [").append(turnCount).append(" total turns]");
            sb.append("\n");
            index++;     
        }

        return sb.toString();
    }
}
