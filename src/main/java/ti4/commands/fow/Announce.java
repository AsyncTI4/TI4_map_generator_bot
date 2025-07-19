package ti4.commands.fow;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class Announce extends GameStateSubcommand {

    public Announce() {
        super(Constants.ANNOUNCE, "Send an anonymous message to the main channel", false, true);
        addOptions(new OptionData(OptionType.STRING, Constants.MSG, "Message to send").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToChannel(getGame().getMainGameChannel(),
            "📣 " + event.getOption(Constants.MSG).getAsString());
    }
}