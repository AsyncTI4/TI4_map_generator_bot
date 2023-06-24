package ti4.commands.statistics;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class AverageTurnTime extends StatisticsSubcommandData {

    public AverageTurnTime() {
        super(Constants.AVERAGE_TURN_TIME, "Average turn time accross all games for all players");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = getAverageTurnTimeText();
        MessageHelper.sendMessageToThread(event.getChannel(), "Average Turn Time", text);
    }

    private String getAverageTurnTimeText() {
        HashMap<String, Map> maps = MapManager.getInstance().getMapList();

        HashMap<String, Entry<Integer, Long>> playerTurnTimes = new HashMap<>();

        for (Map map : maps.values()) {
            for (Player player : map.getPlayers().values()) {
                Entry<Integer, Long> playerTurnTime = java.util.Map.entry(player.getNumberTurns(), player.getTotalTurnTime());
                playerTurnTimes.merge(player.getUserID(), playerTurnTime, (oldEntry, newEntry) -> java.util.Map.entry(oldEntry.getKey() + playerTurnTime.getKey(), oldEntry.getValue() + playerTurnTime.getValue()));
            }
        }
        StringBuilder sb = new StringBuilder();

        sb.append("### __**Average Turn Time:**__\n");

        
        LinkedHashMap<User, Long> userAverageTurnLengths = new LinkedHashMap<>();
        
        for (Entry<String, Entry<Integer, Long>> userTurnCountTotalTime : playerTurnTimes.entrySet()) {
            User user = MapGenerator.jda.getUserById(userTurnCountTotalTime.getKey());
            int turnCount = userTurnCountTotalTime.getValue().getKey();
            long totalMillis = userTurnCountTotalTime.getValue().getValue();

            if (user == null || turnCount == 0 || totalMillis == 0) continue;
            
            long averageTurnTime = totalMillis / turnCount;
            
            userAverageTurnLengths.put(user, averageTurnTime);
        }
        
        int index = 1;
        for (Entry<User, Long> userAverageTurnLength : userAverageTurnLengths.entrySet().stream().sorted((o1, o2)->o1.getValue().compareTo(o2.getValue())).collect(Collectors.toList())) {
            User user = userAverageTurnLength.getKey();
            long averageTurnTime = userAverageTurnLength.getValue();

            averageTurnTime = averageTurnTime / 1000; //total seconds (truncates)
            long seconds = averageTurnTime % 60;

            averageTurnTime = averageTurnTime / 60; //total minutes (truncates)
            long minutes = averageTurnTime % 60;
            long hours = averageTurnTime / 60; //total hours (truncates)

            sb.append("`" + index + ". `" + user.getEffectiveName() + ": ");
            sb.append(String.format("%02dh:%02dm:%02ds", hours, minutes, seconds));
            sb.append("\n");
            index++;                 
        }

        return sb.toString();
    }
}
