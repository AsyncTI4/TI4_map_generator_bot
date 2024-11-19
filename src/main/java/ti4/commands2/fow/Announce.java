package ti4.commands2.fow;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class Announce extends GameStateSubcommand {

    public Announce() {
        super(Constants.ANNOUNCE, "Send a message to the main channel", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.MSG, "Message to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ANON, "Send anonymously").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var player = getPlayer();
        String msg = event.getOption(Constants.MSG).getAsString();
        String message;
        OptionMapping anon = event.getOption(Constants.ANON);
        if (anon == null) {
            message = player.getRepresentation() + " announces: " + msg;
        } else {
            String anonY = anon.getAsString();

            if (anonY.compareToIgnoreCase("y") == 0) {
                message = "[REDACTED] announces: " + msg;
            } else {
                message = player.getRepresentation() + " announces: " + msg;
            }
        }

        var game = getGame();
        MessageChannel mainGameChannel = game.getMainGameChannel() == null ? event.getChannel() : game.getMainGameChannel();
        MessageHelper.sendMessageToChannel(mainGameChannel, message);
    }
}