package ti4.commands.help;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import org.jetbrains.annotations.NotNull;
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

public class ListGames extends HelpSubcommandData {

    public ListGames() {
        super(Constants.LIST_GAMES, "List all games");
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
        representationText.append("        Created: ").append(map.getCreationDate());
        representationText.append("     Last Modified: ").append(Helper.getDateRepresentation(map.getLastModifiedDate()));
        representationText.append("     Has Ended: ").append(map.isHasEnded());
        return representationText.toString();
    }
}
