package ti4.commands.game;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.jetbrains.annotations.NotNull;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.ArrayList;
import java.util.HashMap;

public class Info extends GameSubcommandData{
    public static final String NEW_LINE = "\n";

    public Info() {
        super(Constants.INFO, "Game information:");
        addOptions(new OptionData(OptionType.STRING, Constants.GAME_NAME, "Map Name").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        MapManager mapManager = MapManager.getInstance();
        StringBuilder sb = getGameInfo(mapName, mapManager);
        MessageHelper.replyToMessage(event, sb.toString());
    }

    public static StringBuilder getGameInfo(String mapName, MapManager mapManager) {
        Map map = mapManager.getMap(mapName);
        StringBuilder sb = new StringBuilder();
        sb.append("Game Info:").append(NEW_LINE);
        sb.append("Map name: " + map.getName()).append(NEW_LINE);

        sb.append("Map owner: " + map.getOwnerName()).append(NEW_LINE);
        sb.append("Map status: " + map.getMapStatus()).append(NEW_LINE);
        sb.append("Players: ").append(NEW_LINE);
        HashMap<String, Player> players = map.getPlayers();
        int index = 1;
        ArrayList<Player> playerNames = new ArrayList<>(players.values());
        for (Player value : playerNames) {
            sb.append(index).append(". ").append(value.getUserName()).append(NEW_LINE);
            index++;
        }
        return sb;
    }
}
