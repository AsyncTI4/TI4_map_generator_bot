package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.map.MapSaveLoadManager;
import ti4.message.MessageHelper;

public class SaveMaps implements Command {

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(Constants.SAVE_MAPS);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getInteraction().getMember();
        if (member != null && member.getId().equals(MapGenerator.userID)) {
            MapSaveLoadManager.saveMaps();
            MessageHelper.replyToMessage(event, "Saved");
        } else {
            MessageHelper.replyToMessage(event, "Not Authorized save attempt");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(Constants.SAVE_MAPS, "Save all maps")
        );
    }
}
