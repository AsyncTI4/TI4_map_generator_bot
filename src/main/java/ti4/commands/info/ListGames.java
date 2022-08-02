package ti4.commands.info;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.stream.Collectors;

public class ListGames implements Command {

    @Override
    public String getActionID() {
        return Constants.LIST_GAMES;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(getActionID());
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
        String mapNameList = "Map List:\n" + mapList.keySet().stream()
                .sorted()
                .map(mapName -> getRepresentationText(mapList, mapName))
                .collect(Collectors.joining("\n"));
        MessageHelper.replyToMessage(event, mapNameList);
    }

    private String getRepresentationText(HashMap<String, Map> mapList, String mapName) {
        Map map = mapList.get(mapName);
        StringBuilder representationText = new StringBuilder(mapName);
        for (Player player : map.getPlayers().values()) {
            if (player.getFaction() != null) {
                representationText.append(Helper.getFactionIconFromDiscord(player.getFaction())).append(" ");
            }
        }
        representationText.append(": ").append(map.getMapStatus());
        representationText.append("        Created:").append(map.getCreationDate());
        representationText.append("     Last Modified: ").append(Helper.getDateRepresentation(map.getLastModifiedDate()));
        return representationText.toString();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Shows selected map")

        );
    }
}
