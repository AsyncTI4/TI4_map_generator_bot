package ti4.commands.bothelper;

import java.text.NumberFormat;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;

public class ServerLimitStats extends BothelperSubcommandData {
    public ServerLimitStats(){
        super(Constants.SERVER_LIMIT_STATS, "Server Limit Stats");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

        int memberCount = guild.getMemberCount();
        int memberMax = guild.getMaxMembers();
        int boostCount = guild.getBoostCount();
        int roleCount = guild.getRoles().size(); //250

        //CHANNELS
        List<GuildChannel> channels = guild.getChannels();
        int channelCount = channels.size(); //500
        long pbdChannelCount = channels.stream().filter(c -> c.getName().startsWith("pbd")).count();
        long categoryChannelCount = channels.stream().filter(c -> c.getType() == ChannelType.CATEGORY).count();

        //THREADS
        List<ThreadChannel> threadChannels = guild.getThreadChannels().stream().filter(c -> !c.isArchived()).toList();
        int threadCount = threadChannels.size(); //1000
        List<ThreadChannel> threadChannelsArchived = guild.getThreadChannels().stream().filter(ThreadChannel::isArchived).toList();
        int threadArchivedCount = threadChannelsArchived.size();
        long cardsInfoThreadCount = threadChannels.stream().filter(t -> t.getName().startsWith(Constants.CARDS_INFO_THREAD_PREFIX)).count();
        long botThreadCount = threadChannels.stream().filter(t -> t.getName().contains("-bot-map-updates")).count();
        long roundThreadCount = threadChannels.stream().filter(t -> t.getName().contains("-round-")).count();
        long privateThreadCount = threadChannels.stream().filter(t -> !t.isPublic()).count();
        long publicThreadCount = threadChannels.stream().filter(ThreadChannel::isPublic).count();

        int emojiCount = guild.getEmojis().size();
        int emojiMax = guild.getMaxEmojis();

        StringBuilder sb = new StringBuilder("## Server Limit Statistics:\n");
        sb.append("### Server: ").append(guild.getName()).append("\n");
        sb.append("- ").append(memberCount).append(" / ").append(memberMax).append(getPercentage(memberCount, memberMax)).append(" - members").append("\n");
        sb.append("- ").append(boostCount).append(" - boosts").append("\n");
        sb.append("- ").append(emojiCount).append(" / ").append(emojiMax).append(getPercentage(emojiCount, emojiMax)).append(" - emojis").append("\n");
        sb.append("- ").append(roleCount).append(" / 250").append(getPercentage(roleCount, 250)).append(" - roles").append("\n");
        sb.append("### Channels:\n");
        sb.append("- ").append("**").append(channelCount).append(" / 500").append(getPercentage(channelCount, 500)).append(" - channels**").append("\n");
        sb.append(" - ").append(categoryChannelCount).append("   ").append(getPercentage(categoryChannelCount, channelCount)).append("  categories").append("\n");
        sb.append(" - ").append(pbdChannelCount).append("   ").append(getPercentage(pbdChannelCount, channelCount)).append("  'pbd' channels").append("\n");
        sb.append("### Threads:\n");
        sb.append("- ").append("**").append(threadCount).append(" / 1000").append(getPercentage(threadCount, 1000)).append(" - threads**").append("\n");
        sb.append(" - ").append("   ").append(threadArchivedCount).append(" - loaded archived threads").append("\n");
        sb.append("- ").append(privateThreadCount).append("   ").append(getPercentage(privateThreadCount, threadCount)).append("  private threads").append("\n");
        sb.append(" - ").append(cardsInfoThreadCount).append("   ").append(getPercentage(cardsInfoThreadCount, threadCount)).append("  'Cards Info' threads (/cards_info)").append("\n");
        sb.append("- ").append(publicThreadCount).append("   ").append(getPercentage(publicThreadCount, threadCount)).append("  public threads").append("\n");
        sb.append(" - ").append(botThreadCount).append("   ").append(getPercentage(botThreadCount, threadCount)).append("  '-bot-map-updates' threads").append("\n");
        sb.append(" - ").append(roundThreadCount).append("   ").append(getPercentage(roundThreadCount, threadCount)).append("  '-round-' threads (/sc play and combat)").append("\n");
        sendMessage(sb.toString());
    }

    private String getPercentage(double numerator, double denominator) {
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMinimumFractionDigits(1);
        String formatted = formatPercent.format(denominator == 0 ? 0.0 : (numerator / denominator));
        formatted = " *(" + formatted + ")* ";
        return formatted;
    }
}
