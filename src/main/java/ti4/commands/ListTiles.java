package ti4.commands;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.requests.restaction.CommandListUpdateAction;
import ti4.generator.TilesMapper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListTiles implements Command {

    @Override
    public boolean accept(SlashCommandInteractionEvent event) {
        return event.getName().equals(Constants.LIST_TILES);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String tilesList = TilesMapper.getTilesList();
        MessageHelper.replyToMessage(event, tilesList);
    }
    @SuppressWarnings("ResultOfMethodCallIgnored")
    @Override
    public void registerCommands(CommandListUpdateAction commands) {
        // Moderation commands with required options
        commands.addCommands(
                Commands.slash(Constants.LIST_TILES, "Shows selected map")

        );
    }
}
