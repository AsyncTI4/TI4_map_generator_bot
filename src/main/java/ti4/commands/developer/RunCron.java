package ti4.commands.developer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.cron.CronManager;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class RunCron extends Subcommand {

    RunCron() {
        super(Constants.RUN_CRON, "Run a cron manually.");
        addOptions(new OptionData(OptionType.STRING, Constants.CRON_NAME, "The cron to run")
                .setRequired(true)
                .setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String cronName = event.getOption(Constants.CRON_NAME).getAsString();
        boolean found = CronManager.runCron(cronName);
        if (found) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Running cron " + cronName + ".");
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Unknown cron: " + cronName + ".");
        }
    }
}
