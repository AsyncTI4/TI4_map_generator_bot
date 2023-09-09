package ti4.commands.bothelper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.helpers.Constants;

public class ListOldThreads extends BothelperSubcommandData {
    final public static Predicate<ThreadChannel> filter = c -> c.getLatestMessageIdLong() != 0 && !c.isArchived();

    public ListOldThreads(){
        super(Constants.LIST_OLD_THREADS, "List the oldest 'active' threads. Use to help find threads that can be archived.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of threads to list (1 to 1000)").setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        int channelCount = event.getOption(Constants.COUNT).getAsInt();
        if (channelCount < 1 || channelCount > 1000) {
            sendMessage("Please choose a number between 1 and 1000");
            return;
        }
        Guild guild = event.getGuild();
        sendMessage(getOldThreadsMessage(guild, channelCount));
    }

    public static String getOldThreadsMessage(Guild guild, Integer channelCount) {
        StringBuilder sb;
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        threadChannels = threadChannels.stream()
                            .filter(filter)
                            .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
                            .limit(channelCount)
                            .toList();

        sb = new StringBuilder("Least Active Threads:\n");
        for (ThreadChannel threadChannel : threadChannels) {
            OffsetDateTime latestActivityTime = TimeUtil.getTimeCreated(threadChannel.getLatestMessageIdLong());
            Duration duration = Duration.between(latestActivityTime.toLocalDateTime(), OffsetDateTime.now().toLocalDateTime());
            sb.append("> `").append(latestActivityTime.toString()).append(" (").append(duration.toHours()).append(" hours ago)`  ").append(threadChannel.getAsMention()).append(" **").append(threadChannel.getName()).append("** from channel **").append(threadChannel.getParentChannel().getName()).append("**\n");
        }
        return sb.toString();
    }

    public static String getHowOldOldestThreadIs(Guild guild) {
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        threadChannels = threadChannels.stream()
                            .filter(filter)
                            .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
                            .limit(1)
                            .toList();

        String durationText = "";
        for (ThreadChannel threadChannel : threadChannels) {
            OffsetDateTime latestActivityTime = TimeUtil.getTimeCreated(threadChannel.getLatestMessageIdLong());
            Duration duration = Duration.between(latestActivityTime.toLocalDateTime(), OffsetDateTime.now().toLocalDateTime());
            durationText =  duration.toHours() + " hours old";
        }
        return durationText;
    }
}
