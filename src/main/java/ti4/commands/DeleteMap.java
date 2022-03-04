package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.helpers.Constants;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class DeleteMap implements Command {


    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        if (!event.getName().equals(Constants.DELETE_MAP)) {
            return false;
        }
        String mapName = event.getOptions().get(0).getAsString();
        if (!MapManager.getInstance().getMapList().containsKey(mapName)) {
            MessageHelper.replyToMessage(event, "Map with such name not found");
            return false;
        }
        String confirm = event.getOptions().get(1).getAsString();
        if (!"YES".equals(confirm)){
            MessageHelper.replyToMessage(event, "Need to confirm map deletion");
        }
        return true;
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Member member = event.getInteraction().getMember();
        if (member == null) {
            MessageHelper.replyToMessage(event, "Caller ID not found");
            return;
        }
        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        if (MapSaveLoadManager.deleteMap(mapName)) {
            MapManager.getInstance().deleteMap(mapName);
            MessageHelper.replyToMessage(event, "Map: " + mapName + " deleted.");
        } else {
            MessageHelper.replyToMessage(event, "Map could not be deleted");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(Constants.DELETE_MAP, "Delete selected map")
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name")
                                .setRequired(true))
                        .addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "Type in YES")
                                .setRequired(true))
        );
    }
}
