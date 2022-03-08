package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.helpers.Constants;
import ti4.map.MapManager;
import ti4.message.MessageHelper;

public class SetMap implements Command {

    @Override
    public String getActionID() {
        return Constants.SET_MAP;
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
        Member member = event.getInteraction().getMember();
        if (member == null) {
            MessageHelper.replyToMessage(event, "Caller ID not found");
            return;
        }
        String userID = event.getInteraction().getMember().getId();

        String mapName = event.getOptions().get(0).getAsString().toLowerCase();
        boolean setMapSuccessful = MapManager.getInstance().setMapForUser(userID, mapName);
        if (!setMapSuccessful) {
            MessageHelper.replyToMessage(event, "Could not assign active map " + mapName);
        } else {
            MessageHelper.replyToMessage(event, "Active Map set: " + mapName);
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(getActionID(), "Set map as active")
                        .addOptions(new OptionData(OptionType.STRING, Constants.MAP_NAME, "Map name to be set as active")
                                .setRequired(true))

        );
    }
}
