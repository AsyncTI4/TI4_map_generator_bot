package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.buttons.Buttons;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class TechButton extends TechSubcommandData {
    public TechButton() {
        super(Constants.BUTTON, "Force the add technology button to display");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannelWithButton(event.getMessageChannel(), "", Buttons.GET_A_TECH);
    }
}
