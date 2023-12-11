package ti4.commands.tech;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class TechButton extends TechSubcommandData {
    public TechButton() {
        super(Constants.BUTTON, "Force the add tech button to display");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Button getTech = Button.success("acquireATech", "Get a Tech");
        MessageHelper.sendMessageToChannelWithButtons(event.getMessageChannel(), "", getTech);
    }
}
