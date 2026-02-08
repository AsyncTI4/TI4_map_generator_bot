package ti4.commands.bothelper;

import java.text.NumberFormat;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.game.CreateGameService;
import ti4.spring.jda.JdaService;

class ServerLimitStats extends Subcommand {

    public ServerLimitStats() {
        super(Constants.SERVER_LIMIT_STATS, "Server Limit Stats");
    }

    public void execute(SlashCommandInteractionEvent event) {
        Guild guild = event.getGuild();
        boolean isFoWGuild = JdaService.fowServers.contains(event.getGuild());

        int memberCount = guild.getMemberCount();
        int memberMax = guild.getMaxMembers();
        int boostCount = guild.getBoostCount();
        int roleCount = guild.getRoles().size(); // 250
        int emojiCount = guild.getEmojis().size();
        int emojiMax = guild.getMaxEmojis();

        // CHANNELS
        List<GuildChannel> channels = guild.getChannels();
        int channelCount = channels.size(); // 500
        int roomForGames = (int) Math.min(
                250.0f - roleCount,
                (500.0f - channelCount) / (!isFoWGuild ? CreateGameService.getChannelCountRequiredForEachGame() : 10));
        long pbdChannelCount = channels.stream()
                .filter(c -> c.getName().startsWith(!isFoWGuild ? "pbd" : "fow"))
                .count();
        long categoryChannelCount = channels.stream()
                .filter(c -> c.getType() == ChannelType.CATEGORY)
                .count();

        // THREADS
        List<ThreadChannel> cachedThreadChannels = guild.getThreadChannels();
        int cachedThreadCount = cachedThreadChannels.size();
        List<ThreadChannel> activeThreadChannels = guild.retrieveActiveThreads().complete().stream()
                .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
                .toList();
        int activeThreadCount = activeThreadChannels.size();
        List<ThreadChannel> threadChannelsArchived =
                cachedThreadChannels.stream().filter(ThreadChannel::isArchived).toList();
        int threadArchivedCount = threadChannelsArchived.size();
        long cardsInfoThreadCount = cachedThreadChannels.stream()
                .filter(t -> !isFoWGuild
                        ? t.getName().startsWith(Constants.CARDS_INFO_THREAD_PREFIX)
                        : t.getName().contains("-cards-info"))
                .count();
        long botThreadCount = cachedThreadChannels.stream()
                .filter(t -> t.getName().contains("-bot-map-updates"))
                .count();
        long roundThreadCount = cachedThreadChannels.stream()
                .filter(t -> t.getName().contains("-round-"))
                .count();
        long privateThreadCount =
                cachedThreadChannels.stream().filter(t -> !t.isPublic()).count();
        long publicThreadCount =
                cachedThreadChannels.stream().filter(ThreadChannel::isPublic).count();

        String message = """
            ## Server Limit Statistics:
            ### Server: %s
            - %,d / %,d%s - members
            - %d boosts
            - %d / %d%s - emojis
            - %d / 250%s - roles
            - space for **%d** more games
            ### Channels:
            - **%d / 500%s - channels**
              - %d   %s  categories
              - %d   %s  '%s' channels
            ### Threads:
            - %s
            - **%d / 1000%s - active threads**
              - %d cached threads
              - %d cached archived threads
            - %d   %s  private threads
              - %d   %s  'Cards Info' threads (/cards_info)
            - %d   %s  public threads
              - %d   %s  '-bot-map-updates' threads
              - %d   %s  '-round-' threads (/sc play and combat)
            """.formatted(
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
                        (!isFoWGuild ? "pbd" : "fow"),
                        getThreadAgeInHoursString(activeThreadChannels.getFirst()),
                        activeThreadCount,
                        getPercentage(activeThreadCount, 1000),
                        cachedThreadCount,
                        threadArchivedCount,
                        privateThreadCount,
                        getPercentage(privateThreadCount, cachedThreadCount),
                        cardsInfoThreadCount,
                        getPercentage(cardsInfoThreadCount, cachedThreadCount),
                        publicThreadCount,
                        getPercentage(publicThreadCount, cachedThreadCount),
                        botThreadCount,
                        getPercentage(botThreadCount, cachedThreadCount),
                        roundThreadCount,
                        getPercentage(roundThreadCount, cachedThreadCount));
        MessageHelper.sendMessageToEventChannel(event, message);
    }

    private String getPercentage(double numerator, double denominator) {
        NumberFormat formatPercent = NumberFormat.getPercentInstance();
        formatPercent.setMinimumFractionDigits(1);
        String formatted = formatPercent.format(denominator == 0 ? 0.0 : (numerator / denominator));
        formatted = " *(" + formatted + ")* ";
        return formatted;
    }

    private String getThreadAgeInHoursString(ThreadChannel threadChannel) {
        OffsetDateTime latestActivityTime = TimeUtil.getTimeCreated(threadChannel.getLatestMessageIdLong());
        Duration duration = Duration.between(latestActivityTime, OffsetDateTime.now());
        return "oldest thread is %,d hours old".formatted(duration.toHours());
    }
}
