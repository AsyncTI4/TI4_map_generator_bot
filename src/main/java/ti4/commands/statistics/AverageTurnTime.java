package ti4.commands.statistics;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AverageTurnTime extends StatisticsSubcommandData {

    public AverageTurnTime() {
        super(Constants.AVERAGE_TURN_TIME, "Average turn time accross all games for all players");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TOP_LIMIT, "How many players to show (Default = 50)").setRequired(false));
        addOptions(new OptionData(OptionType.INTEGER, Constants.MINIMUM_NUMBER_OF_TURNS, "Minimum number of turns to show (Default = 1)").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getAverageTurnTimeText(event);
        MessageHelper.sendMessageToThread(event.getChannel(), "Average Turn Time", text);
    }

    private String getAverageTurnTimeText(SlashCommandInteractionEvent event) {
        HashMap<String, Map> maps = MapManager.getInstance().getMapList();

        HashMap<String, Entry<Integer, Long>> playerTurnTimes = new HashMap<>();

        for (Map map : maps.values()) {
            for (Player player : map.getPlayers().values()) {
                Entry<Integer, Long> playerTurnTime = java.util.Map.entry(player.getNumberTurns(), player.getTotalTurnTime());
                playerTurnTimes.merge(player.getUserID(), playerTurnTime, (oldEntry, newEntry) -> java.util.Map.entry(oldEntry.getKey() + playerTurnTime.getKey(), oldEntry.getValue() + playerTurnTime.getValue()));
            }
        }
        StringBuilder sb = new StringBuilder();

        sb.append("## __**Average Turn Time:**__\n");
        
        int index = 1;
        Comparator<Entry<String, Entry<Integer, Long>>> comparator = (o1, o2) -> {
            int o1TurnCount = o1.getValue().getKey();
            int o2TurnCount = o2.getValue().getKey();
            long o1total = o1.getValue().getValue();
            long o2total = o2.getValue().getValue();
            if (o1TurnCount == 0 || o2TurnCount == 0) return -1;

            Long total1 = o1total/o1TurnCount;
            Long total2 = o2total/o2TurnCount;
            return total1.compareTo(total2);
        };

        int topLimit = event.getOption(Constants.TOP_LIMIT, 50, OptionMapping::getAsInt);
        int minimumTurnsToShow = event.getOption(Constants.MINIMUM_NUMBER_OF_TURNS, 1, OptionMapping::getAsInt);
        for (Entry<String, Entry<Integer, Long>> userTurnCountTotalTime : playerTurnTimes.entrySet().stream().filter(o -> o.getValue().getValue() != 0 && o.getValue().getKey() > minimumTurnsToShow).sorted(comparator).limit(topLimit).collect(Collectors.toList())) {
            User user = MapGenerator.jda.getUserById(userTurnCountTotalTime.getKey());
            int turnCount = userTurnCountTotalTime.getValue().getKey();
            long totalMillis = userTurnCountTotalTime.getValue().getValue();

            if (user == null || turnCount == 0 || totalMillis == 0) continue;
            
            long averageTurnTime = totalMillis / turnCount;

            sb.append("`").append(Helper.leftpad(String.valueOf(index), 3)).append(". ");
            sb.append(getTimeRepresentation(averageTurnTime));
            sb.append("` ").append(user.getEffectiveName());
            sb.append("   [").append(turnCount).append(" total turns]");
            sb.append("\n");
            index++;     
        }

        return sb.toString();
    }

    private String getTimeRepresentation(long millis) {
        long averageTurnTime = millis / 1000; //total seconds (truncates)
        long seconds = averageTurnTime % 60;
        averageTurnTime = averageTurnTime / 60; //total minutes (truncates)
        long minutes = averageTurnTime % 60;
        long hours = averageTurnTime / 60; //total hours (truncates)

        return String.format("%02dh:%02dm:%02ds", hours, minutes, seconds);
    }
}
