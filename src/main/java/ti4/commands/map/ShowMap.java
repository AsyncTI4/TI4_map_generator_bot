package ti4.commands.map;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.commands.Command;
import ti4.generator.GenerateMap;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.StringTokenizer;

public class ShowMap implements Command {

    @Override
    public String getActionID() {
        return Constants.SHOW_MAP;
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
        Map map = MapManager.getInstance().getMap(mapName);
        File file = GenerateMap.getInstance().saveImage(map);
        MessageHelper.replyToMessage(event, file);
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Shows selected map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name to be shown") // USER type allows to include members of the server or other users by id
                                .setRequired(true)) // This command requires a parameter

        );
    }
}
