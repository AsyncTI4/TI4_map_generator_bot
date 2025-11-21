package ti4.commands.bothelper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.ThreadArchiveHelper;
import ti4.message.MessageHelper;

class ArchiveOldThreads extends Subcommand {

    public ArchiveOldThreads() {
        super(Constants.ARCHIVE_OLD_THREADS, "Archive a number of the oldest active threads");
        addOptions(
                new OptionData(OptionType.INTEGER, Constants.THREAD_COUNT, "Number of threads to archive (1 to 1000)")
                        .setRequired(true),
                new OptionData(
                                OptionType.BOOLEAN,
                                "dry_run",
                                "If true, will only list the threads without archiving them. Default is true.")
                        .setRequired(false));
    }

    public void execute(SlashCommandInteractionEvent event) {
        int threadCount = event.getOption(Constants.THREAD_COUNT, 1, OptionMapping::getAsInt);
        if (threadCount < 1 || threadCount > 1000) {
            MessageHelper.sendMessageToEventChannel(event, "Please choose a number between 1 and 1000");
            return;
        }

        boolean dryRun = event.getOption("dry_run", true, OptionMapping::getAsBoolean);
        if (dryRun) {
            List<ThreadChannel> threadChannels =
                    ThreadArchiveHelper.archiveOldThreads(event.getGuild(), threadCount, true);
            MessageHelper.sendMessageToEventChannel(event, getOldThreadsMessage(threadChannels));
            MessageHelper.sendMessageToEventChannel(event, "Dry run complete. No threads were archived.");
            return;
        }

        List<ThreadChannel> threadChannels = ThreadArchiveHelper.archiveOldThreads(event.getGuild(), threadCount);
        MessageHelper.sendMessageToEventChannel(event, getOldThreadsMessage(threadChannels));
        MessageHelper.sendMessageToEventChannel(event, "Archiving all " + threadCount + " threads shown above.");
    }

    private static String getOldThreadsMessage(List<ThreadChannel> threadChannels) {
        StringBuilder sb = new StringBuilder("## __Least Active Threads:__\n");
        for (ThreadChannel threadChannel : threadChannels) {
            OffsetDateTime latestActivityTime = TimeUtil.getTimeCreated(threadChannel.getLatestMessageIdLong());
            Duration duration = Duration.between(
                    latestActivityTime.toLocalDateTime(), OffsetDateTime.now().toLocalDateTime());
            sb.append("- `")
                    .append(latestActivityTime)
                    .append(" (")
                    .append(duration.toHours())
                    .append(" hours ago)`  ")
                    .append(threadChannel.getAsMention())
                    .append(" **")
                    .append(threadChannel.getName())
                    .append("** from channel **")
                    .append(threadChannel.getParentChannel().getName())
                    .append("**\n");
        }
        return sb.toString();
    }
}
