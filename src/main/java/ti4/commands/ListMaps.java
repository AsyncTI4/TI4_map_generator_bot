package ti4.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.util.HashMap;
import java.util.stream.Collectors;

public class ListMaps implements Command {

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(Constants.LIST_MAPS);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        HashMap<String, Map> mapList = MapManager.getInstance().getMapList();
        String mapNameList = "Map List:\n" + mapList.keySet().stream()
                .sorted()
                .collect(Collectors.joining("\n"));
        MessageHelper.replyToMessage(event, mapNameList);
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(Constants.LIST_MAPS, "Shows selected map")

        );
    }
}
