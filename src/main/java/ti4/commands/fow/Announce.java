package ti4.commands.fow;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class Announce extends FOWSubcommandData {

    public Announce() {
        super(Constants.ANNOUNCE, "Send a message to the main channel");
        addOptions(new OptionData(OptionType.STRING, Constants.MSG, "Message to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.ANON, "Send anonymously").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }

        OptionMapping ms = event.getOption(Constants.MSG);
        OptionMapping anon = event.getOption(Constants.ANON);
        if (ms != null) {
            String msg = ms.getAsString();
            String message;
            if (anon != null) {
                String anonY = anon.getAsString();

                if (anonY.compareToIgnoreCase("y") == 0) {
                    message = "[REDACTED] announces: " + msg;
                } else {
                    message = player.getRepresentation() + " announces: " + msg;
                }
            } else {
                message = player.getRepresentation() + " announces: " + msg;
            }

            MessageChannel mainGameChannel = game.getMainGameChannel() == null ? event.getChannel() : game.getMainGameChannel();
            MessageHelper.sendMessageToChannel(mainGameChannel, message);
        }
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
    }
}