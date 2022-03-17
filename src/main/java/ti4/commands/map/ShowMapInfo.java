package ti4.commands.map;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import net.dv8tion.jda.api.utils.concurrent.Task;
import ti4.commands.Command;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.util.*;

public class ShowMapInfo implements Command {

    public static final String NEW_LINE = "\n";

    @Override
    public String getActionID() {
        return Constants.SHOW_MAP_INFO;
    }

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(getActionID())) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Map with such name does not exists, use /list_maps");
            return false;
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        MapManager mapManager = MapManager.getInstance();
        Map map = mapManager.getMap(mapName);
        StringBuilder sb = new StringBuilder();
        sb.append("Map Info:").append(NEW_LINE);
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
        MessageHelper.replyToMessage(event, sb.toString());
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Show map info")
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name")
                                .setRequired(true))
        );
    }
}
