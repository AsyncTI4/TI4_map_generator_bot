package ti4.commands;

import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class Shutdown implements Command {

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(Constants.SHUTDOWN);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Member member = event.getInteraction().getMember();
        if (member != null && member.getId().equals(MapGenerator.userID)) {
            MessageHelper.replyToMessage(event, "Shutdown accepted");
            MapGenerator.jda.shutdownNow();
        } else {
            MessageHelper.replyToMessage(event, "Not Authorized shutdown attempt");
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash("shutdown", "Shows selected map")

        );
    }
}
