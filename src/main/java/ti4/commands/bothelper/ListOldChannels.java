package ti4.commands.bothelper;

import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListOldChannels extends BothelperSubcommandData {
    public ListOldChannels(){
        super(Constants.LIST_OLD_CHANNELS, "List the oldest 'active' channels. Use to help find dead games to free up channels.");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of channels to list (1 to 100)").setRequired(true));
    }
    
    public void execute(SlashCommandInteractionEvent event) {
        Integer channelCount = event.getOption(Constants.COUNT).getAsInt();
        if (channelCount < 1 || channelCount > 100) {
            MessageHelper.replyToMessage(event, "Please choose a number between 1 and 100");
            return;
        }
        Guild guild = event.getGuild();
        MessageHelper.replyToMessage(event, getOldChannelsMessage(guild, channelCount));
        MessageHelper.sendMessageToChannel(event, getOldThreadsMessage(guild, channelCount));
    }

    public static String getOldChannelsMessage(Guild guild, Integer channelCount) {
        List<TextChannel> channels = guild.getTextChannels();
        channels = channels.stream()
                            .filter(c -> c.getLatestMessageIdLong() != 0)
                            .sorted((object1, object2) -> object1.getLatestMessageId().compareTo(object2.getLatestMessageId()))
                            .limit(channelCount)
                            .toList();
        
        StringBuilder sb = new StringBuilder("Least Active Channels:\n");
        for (TextChannel channel : channels) {
            sb.append("> `" + TimeUtil.getTimeCreated(channel.getLatestMessageIdLong()).toString() + "`  " + channel.getAsMention()).append("\n");
        }
        return sb.toString();
    }

    public static String getOldThreadsMessage(Guild guild, Integer channelCount) {
        StringBuilder sb;
        List<ThreadChannel> threadChannels = guild.getThreadChannels();
        threadChannels = threadChannels.stream()
                            .filter(c -> c.getLatestMessageIdLong() != 0)
                            .sorted((object1, object2) -> object1.getLatestMessageId().compareTo(object2.getLatestMessageId()))
                            .limit(channelCount)
                            .toList();
        
        sb = new StringBuilder("Least Active Threads:\n");
        for (ThreadChannel threadChannel : threadChannels) {
            sb.append("> `" + TimeUtil.getTimeCreated(threadChannel.getLatestMessageIdLong()).toString() + "`  " + threadChannel.getAsMention() + " **" + threadChannel.getName() + "** from channel **" + threadChannel.getParentChannel().getName()).append("**\n");
        }
        return sb.toString();
    }
}
