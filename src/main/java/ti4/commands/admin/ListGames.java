package ti4.commands.admin;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.HashMap;
import java.util.stream.Collectors;

public class ListGames extends AdminSubcommandData {

    public ListGames() {
        super(Constants.LIST_GAMES, "List all games");
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ENDED_GAMES, "True to show only ended games"));
    }
    

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
        OptionMapping option = event.getOption(Constants.ENDED_GAMES);
        Boolean filterEndedGames = option == null ? null : option.getAsBoolean();
        String mapNameList = "__**Map List:**__\n";
        if (filterEndedGames != null && filterEndedGames) {
            mapNameList += mapList.entrySet().stream()
                    .filter(map -> map.getValue().isHasEnded())
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(map -> getRepresentationText(mapList, map.getKey()))
                    .collect(Collectors.joining("\n"));
        } else if (filterEndedGames != null && !filterEndedGames) {
            mapNameList += mapList.entrySet().stream()
                    .filter(map -> !map.getValue().isHasEnded())
                    .sorted(java.util.Map.Entry.comparingByKey())
                    .map(map -> getRepresentationText(mapList, map.getKey()))
                    .collect(Collectors.joining("\n"));
        } else {
            mapNameList += mapList.keySet().stream()
                    .sorted()
                    .map(mapName -> getRepresentationText(mapList, mapName))
                    .collect(Collectors.joining("\n"));
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), mapNameList);
    }

    private String getRepresentationText(HashMap<String, Map> mapList, String mapName) {
        Map map = mapList.get(mapName);
        StringBuilder representationText = new StringBuilder("> **" + mapName + "**").append(" ");
        for (Player player : map.getPlayers().values()) {
            if (player.getFaction() != null) {
                representationText.append(Helper.getFactionIconFromDiscord(player.getFaction()));
            }
        }
        representationText.append(": ").append(map.getMapStatus());
        representationText.append("        Created: ").append(map.getCreationDate());
        representationText.append("     Last Modified: ").append(Helper.getDateRepresentation(map.getLastModifiedDate()));
        representationText.append("     Has Ended: ").append(map.isHasEnded());
        return representationText.toString();
    }
}
