package ti4.commands.bothelper;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.MapGenerator;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ArchiveOldThreads extends BothelperSubcommandData {
    public ArchiveOldThreads(){
        super(Constants.ARCHIVE_OLD_THREADS, "Archive a number of the oldest active threads");
        addOptions(new OptionData(OptionType.INTEGER, Constants.THREAD_COUNT, "Number of threads to archive (1 to 100)").setRequired(true));
    }
    
    public void execute(SlashCommandInteractionEvent event) {
        Integer threadCount = event.getOption(Constants.THREAD_COUNT).getAsInt();
        if (threadCount < 1 || threadCount > 100) {
            MessageHelper.replyToMessage(event, "Please choose a number between 1 and 100");
            return;
        }
        String mention = event.getGuild().getMemberById("150809002974904321").getAsMention(); //@HolyTispoon
        MessageHelper.replyToMessage(event, "Archiving " + threadCount + " threads - " + mention);
        MessageHelper.sendMessageToChannel(event.getChannel(), ListOldChannels.getOldThreadsMessage(event.getGuild(), threadCount));
        archiveOldThreads(event.getGuild(), threadCount);
    }

    public static void archiveOldThreads(Guild guild, Integer threadCount) {
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        threadChannels = threadChannels.stream()
            .filter(c -> c.getLatestMessageIdLong() != 0)
            .sorted((object1, object2) -> object1.getLatestMessageId().compareTo(object2.getLatestMessageId()))
            .limit(threadCount)
            .toList();
    
        for (ThreadChannel threadChannel : threadChannels) {
            threadChannel.getManager().setArchived(true).queue();
        }
    }
}
