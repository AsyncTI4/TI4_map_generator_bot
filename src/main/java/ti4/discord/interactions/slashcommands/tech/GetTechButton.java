package ti4.discord.interactions.slashcommands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.discord.interactions.buttons.Buttons;
import ti4.discord.interactions.slashcommands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class GetTechButton extends Subcommand {

    public GetTechButton() {
        super(Constants.BUTTON, "Force the \"add technology\" buttons to display");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", Buttons.GET_A_TECH);
    }
}
