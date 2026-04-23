package ti4.discord.interactions.slashcommands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.message.MessageHelper;

class CCsButton extends Subcommand {

    CCsButton() {
        super("cc", "Adjust Command Tokens");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButton(event.getChannel(), null, Buttons.REDISTRIBUTE_CCs);
    }

    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
