package ti4.commands.bothelper;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.ThreadArchiveHelper;
import ti4.message.MessageHelper;

class ArchiveOldThreads extends Subcommand {

    ArchiveOldThreads() {
        super(Constants.ARCHIVE_OLD_THREADS, "Archive a number of the oldest active threads");
        addOptions(
                new OptionData(OptionType.INTEGER, Constants.THREAD_COUNT, "Number of threads to archive (1 to 1000)")
                        .setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        int threadCount = event.getOption(Constants.THREAD_COUNT, 1, OptionMapping::getAsInt);
        if (threadCount < 1 || threadCount > 1000) {
            MessageHelper.sendMessageToEventChannel(event, "Please choose a number between 1 and 1000");
            return;
        }

        ThreadArchiveHelper.archiveOldThreads(event.getGuild(), threadCount);
    }
}
