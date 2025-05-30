package ti4.commands.ds;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class PathToken extends GameStateSubcommand {

    public PathToken() {
        super(Constants.PATH_TOKEN_COUNT, "Set path token amount", true, true);
        addOptions(new OptionData(OptionType.INTEGER, "count", "Count").setRequired(true));

    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int count = Math.max(event.getOption("count").getAsInt(), 0);
        if (count > 0) {
            getPlayer().setPathTokenCounter(count);
        }
        MessageHelper.sendMessageToChannel(event.getChannel(), "Set path token count to " + count + ".");
    }
}
