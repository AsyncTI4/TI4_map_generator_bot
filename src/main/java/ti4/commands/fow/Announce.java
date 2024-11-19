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
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.ANON, "True to send anonomously (default = True)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Player could not be found");
            return;
        }

        String message = event.getOption(Constants.MSG, null, OptionMapping::getAsString);
        boolean anon = event.getOption(Constants.ANON, true, OptionMapping::getAsBoolean);

        StringBuilder sb = new StringBuilder();
        if (anon) {
            sb.append("[REDACTED]");
        } else {
            sb.append(player.getRepresentation());
        }
        sb.append(" announces: ").append(message);

        MessageChannel channel = game.getMainGameChannel() == null ? event.getChannel() : game.getMainGameChannel();
        MessageHelper.sendMessageToChannel(channel, sb.toString());
    }

    @Override
    public void reply(SlashCommandInteractionEvent event) {
    }
}