package ti4.commands2.bothelper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.ThreadArchiveHelper;
import ti4.message.MessageHelper;

class ArchiveOldThreads extends Subcommand {

    public ArchiveOldThreads() {
        super(Constants.ARCHIVE_OLD_THREADS, "Archive a number of the oldest active threads");
        addOptions(new OptionData(OptionType.INTEGER, Constants.THREAD_COUNT, "Number of threads to archive (1 to 1000)").setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        int threadCount = event.getOption(Constants.THREAD_COUNT).getAsInt();
        if (threadCount < 1 || threadCount > 1000) {
            MessageHelper.sendMessageToEventChannel(event, "Please choose a number between 1 and 1000");
            return;
        }
        MessageHelper.sendMessageToEventChannel(event, "Archiving " + threadCount + " threads");
        MessageHelper.sendMessageToEventChannel(event, getOldThreadsMessage(event.getGuild(), threadCount));

        ThreadArchiveHelper.archiveOldThreads(event.getGuild(), threadCount);
    }

    private static String getOldThreadsMessage(Guild guild, Integer channelCount) {
        StringBuilder sb;
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        threadChannels = threadChannels.stream()
                .filter(c -> c.getLatestMessageIdLong() != 0 && !c.isArchived())
                .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
                .limit(channelCount)
                .toList();

        sb = new StringBuilder("Least Active Threads:\n");
        for (ThreadChannel threadChannel : threadChannels) {
            OffsetDateTime latestActivityTime = TimeUtil.getTimeCreated(threadChannel.getLatestMessageIdLong());
            Duration duration = Duration.between(latestActivityTime.toLocalDateTime(), OffsetDateTime.now().toLocalDateTime());
            sb.append("> `").append(latestActivityTime).append(" (").append(duration.toHours()).append(" hours ago)`  ").append(threadChannel.getAsMention()).append(" **")
                    .append(threadChannel.getName()).append("** from channel **").append(threadChannel.getParentChannel().getName()).append("**\n");
        }
        return sb.toString();
    }
}
