package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class Tech extends PlayerSubcommandData{
    public Tech() {
        super(Constants.TECH, "Player Tech info");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(event.getChannel(), "Player Tech info received");
    }
}
