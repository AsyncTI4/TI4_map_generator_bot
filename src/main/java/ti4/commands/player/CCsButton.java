package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.buttons.Buttons;
import ti4.commands.Subcommand;
import ti4.message.MessageHelper;

class CCsButton extends Subcommand {

    public CCsButton() {
        super("cc", "Adjust Command Tokens");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(), null, Buttons.REDISTRIBUTE_CCs);
    }
}
