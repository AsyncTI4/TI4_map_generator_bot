package ti4.commands2.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.GlobalSettings;
import ti4.helpers.GlobalSettings.ImplementedSettings;
import ti4.message.BotLogger;

public class GiveTheBotABreather extends Subcommand {

     GiveTheBotABreather() {
        super("give_the_bot_a_breather", "Stop the bot from processing commands for a few seconds.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.SECONDS, "Number of seconds to sleep the bot - default 10"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, false);
        int seconds = event.getOption(Constants.SECONDS, 10, OptionMapping::getAsInt);
        try {
            Thread.sleep(seconds * 1000);
        } catch (InterruptedException e) {
            BotLogger.log("Forced Sleep interrupted", e);
        }
        GlobalSettings.setSetting(ImplementedSettings.READY_TO_RECEIVE_COMMANDS, true);
    }


    
}
