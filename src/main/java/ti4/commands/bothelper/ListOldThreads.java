package ti4.commands.bothelper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.helpers.Constants;

public class ListOldThreads extends BothelperSubcommandData {
    public ListOldThreads(){
        super(Constants.LIST_OLD_THREADS, "List the oldest 'active' threads. Use to help find threads that can be archived.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of threads to list (1 to 1000)").setRequired(true));
    }
    
    public void execute(SlashCommandInteractionEvent event) {
        Integer channelCount = event.getOption(Constants.COUNT).getAsInt();
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
                            .filter(c -> c.getLatestMessageIdLong() != 0 && !c.isArchived())
                            .sorted((object1, object2) -> object1.getLatestMessageId().compareTo(object2.getLatestMessageId()))
                            .limit(channelCount)
                            .toList();
        
        sb = new StringBuilder("Least Active Threads:\n");
        for (ThreadChannel threadChannel : threadChannels) {
            OffsetDateTime latestActivityTime = TimeUtil.getTimeCreated(threadChannel.getLatestMessageIdLong());
            Duration duration = Duration.between(latestActivityTime.toLocalDateTime(), OffsetDateTime.now().toLocalDateTime());
            sb.append("> `" + latestActivityTime.toString() + " (" + duration.toHours() + " hours ago)`  " + threadChannel.getAsMention() + " **" + threadChannel.getName() + "** from channel **" + threadChannel.getParentChannel().getName()).append("**\n");
        }
        return sb.toString();
    }

    public static String getHowOldOldestThreadIs(Guild guild) {
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        threadChannels = threadChannels.stream()
                            .filter(c -> c.getLatestMessageIdLong() != 0)
                            .sorted((object1, object2) -> object1.getLatestMessageId().compareTo(object2.getLatestMessageId()))
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
