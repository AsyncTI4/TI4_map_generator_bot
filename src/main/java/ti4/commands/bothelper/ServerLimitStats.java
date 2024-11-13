package ti4.commands.bothelper;

import java.text.NumberFormat;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ServerLimitStats extends Subcommand {

    public ServerLimitStats() {
        super(Constants.SERVER_LIMIT_STATS, "Server Limit Stats");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();

        int memberCount = guild.getMemberCount();
        int roomForGames;
        int memberMax = guild.getMaxMembers();
        int boostCount = guild.getBoostCount();
        int roleCount = guild.getRoles().size(); //250
        roomForGames = 250 - roleCount;

        //CHANNELS
        List<GuildChannel> channels = guild.getChannels();
        int channelCount = channels.size(); //500
        roomForGames = Math.min(roomForGames, (500 - channelCount) / 2);
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

        String sb = "## Server Limit Statistics:\n" + "### Server: " + guild.getName() + "\n" +
            "- " + memberCount + " / " + memberMax + getPercentage(memberCount, memberMax) + " - members" + "\n" +
            "- " + boostCount + " - boosts" + "\n" +
            "- " + emojiCount + " / " + emojiMax + getPercentage(emojiCount, emojiMax) + " - emojis" + "\n" +
            "- " + roleCount + " / 250" + getPercentage(roleCount, 250) + " - roles" + "\n" +
            "- space for **" + roomForGames + "** more games\n" +
            "### Channels:\n" +
            "- " + "**" + channelCount + " / 500" + getPercentage(channelCount, 500) + " - channels**" + "\n" +
            " - " + categoryChannelCount + "   " + getPercentage(categoryChannelCount, channelCount) + "  categories" + "\n" +
            " - " + pbdChannelCount + "   " + getPercentage(pbdChannelCount, channelCount) + "  'pbd' channels" + "\n" +
            "### Threads:\n" +
            "- " + "**" + threadCount + " / 1000" + getPercentage(threadCount, 1000) + " - threads**" + "\n" +
            " - " + "   " + threadArchivedCount + " - loaded archived threads" + "\n" +
            "- " + privateThreadCount + "   " + getPercentage(privateThreadCount, threadCount) + "  private threads" + "\n" +
            " - " + cardsInfoThreadCount + "   " + getPercentage(cardsInfoThreadCount, threadCount) + "  'Cards Info' threads (/cards_info)" + "\n" +
            "- " + publicThreadCount + "   " + getPercentage(publicThreadCount, threadCount) + "  public threads" + "\n" +
            " - " + botThreadCount + "   " + getPercentage(botThreadCount, threadCount) + "  '-bot-map-updates' threads" + "\n" +
            " - " + roundThreadCount + "   " + getPercentage(roundThreadCount, threadCount) + "  '-round-' threads (/sc play and combat)" + "\n";
        MessageHelper.sendMessageToEventChannel(event, sb);
    }

    private String getPercentage(double numerator, double denominator) {
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMinimumFractionDigits(1);
        String formatted = formatPercent.format(denominator == 0 ? 0.0 : (numerator / denominator));
        formatted = " *(" + formatted + ")* ";
        return formatted;
    }
}
