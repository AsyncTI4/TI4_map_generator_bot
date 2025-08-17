package ti4.commands.bothelper;

import java.text.NumberFormat;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.JdaService;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ServerLimitStats extends Subcommand {

    public ServerLimitStats() {
        super(Constants.SERVER_LIMIT_STATS, "Server Limit Stats");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        boolean isFoWGuild = JdaService.fowServers.contains(event.getGuild());

        int memberCount = guild.getMemberCount();
        int roomForGames;
        int memberMax = guild.getMaxMembers();
        int boostCount = guild.getBoostCount();
        int roleCount = guild.getRoles().size(); // 250
        roomForGames = 250 - roleCount;

        // CHANNELS
        List<GuildChannel> channels = guild.getChannels();
        int channelCount = channels.size(); // 500
        roomForGames = Math.min(roomForGames, (500 - channelCount) / (!isFoWGuild ? 2 : 10));
        long pbdChannelCount = channels.stream()
                .filter(c -> c.getName().startsWith(!isFoWGuild ? "pbd" : "fow"))
                .count();
        long categoryChannelCount = channels.stream()
                .filter(c -> c.getType() == ChannelType.CATEGORY)
                .count();

        // THREADS
        List<ThreadChannel> threadChannels =
                guild.getThreadChannels().stream().filter(c -> !c.isArchived()).toList();
        int threadCount = threadChannels.size(); // 1000
        List<ThreadChannel> threadChannelsArchived = guild.getThreadChannels().stream()
                .filter(ThreadChannel::isArchived)
                .toList();
        int threadArchivedCount = threadChannelsArchived.size();
        long cardsInfoThreadCount = threadChannels.stream()
                .filter(t -> !isFoWGuild
                        ? t.getName().startsWith(Constants.CARDS_INFO_THREAD_PREFIX)
                        : t.getName().contains("-cards-info"))
                .count();
        long botThreadCount = threadChannels.stream()
                .filter(t -> t.getName().contains("-bot-map-updates"))
                .count();
        long roundThreadCount = threadChannels.stream()
                .filter(t -> t.getName().contains("-round-"))
                .count();
        long privateThreadCount =
                threadChannels.stream().filter(t -> !t.isPublic()).count();
        long publicThreadCount =
                threadChannels.stream().filter(ThreadChannel::isPublic).count();

        int emojiCount = guild.getEmojis().size();
        int emojiMax = guild.getMaxEmojis();

        String sb =
                """
            ## Server Limit Statistics:
            ### Server: %s
            - %d / %d%s - members
            - %d - boosts
            - %d / %d%s - emojis
            - %d / 250%s - roles
            - space for **%d** more games
            ### Channels:
            - **%d / 500%s - channels**
              - %d   %s  categories
              - %d   %s  '%s' channels
            ### Threads:
            - **%d / 1000%s - threads**
              - %d - loaded archived threads
            - %d   %s  private threads
              - %d   %s  'Cards Info' threads (/cards_info)
            - %d   %s  public threads
              - %d   %s  '-bot-map-updates' threads
              - %d   %s  '-round-' threads (/sc play and combat)
            """
                        .formatted(
                                guild.getName(),
                                memberCount,
                                memberMax,
                                getPercentage(memberCount, memberMax),
                                boostCount,
                                emojiCount,
                                emojiMax,
                                getPercentage(emojiCount, emojiMax),
                                roleCount,
                                getPercentage(roleCount, 250),
                                roomForGames,
                                channelCount,
                                getPercentage(channelCount, 500),
                                categoryChannelCount,
                                getPercentage(categoryChannelCount, channelCount),
                                pbdChannelCount,
                                getPercentage(pbdChannelCount, channelCount),
                                (!isFoWGuild ? "pdb" : "fow"),
                                threadCount,
                                getPercentage(threadCount, 1000),
                                threadArchivedCount,
                                privateThreadCount,
                                getPercentage(privateThreadCount, threadCount),
                                cardsInfoThreadCount,
                                getPercentage(cardsInfoThreadCount, threadCount),
                                publicThreadCount,
                                getPercentage(publicThreadCount, threadCount),
                                botThreadCount,
                                getPercentage(botThreadCount, threadCount),
                                roundThreadCount,
                                getPercentage(roundThreadCount, threadCount));
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
