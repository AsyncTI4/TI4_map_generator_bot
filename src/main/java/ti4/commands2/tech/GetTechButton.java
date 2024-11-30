package ti4.commands2.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.buttons.Buttons;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class GetTechButton extends Subcommand {

    public GetTechButton() {
        super(Constants.BUTTON, "Force the add tech button to display");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", Buttons.GET_A_TECH);
    }
}
