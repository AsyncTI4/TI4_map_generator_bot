package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.executors.CircuitBreaker;
import ti4.message.MessageHelper;

class GiveTheBotABreather extends Subcommand {

    GiveTheBotABreather() {
        super("give_the_bot_a_breather", "Stop the bot from processing commands for a few seconds.");
        addOptions(new OptionData(
                OptionType.INTEGER, Constants.SECONDS, "Number of seconds to sleep the bot - default 10"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int seconds = event.getOption(Constants.SECONDS, 10, OptionMapping::getAsInt);
        CircuitBreaker.openForSeconds(seconds);
        MessageHelper.sendMessageToChannel(
                event.getChannel(), "Circuit breaker opened for " + seconds + " seconds.");
    }
}
