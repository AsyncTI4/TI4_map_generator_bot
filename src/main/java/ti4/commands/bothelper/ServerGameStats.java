package ti4.commands.bothelper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.ThreadChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.map.persistence.GameManager;
import ti4.map.persistence.ManagedGame;
import ti4.message.MessageHelper;
import ti4.spring.jda.JdaService;

class ServerGameStats extends Subcommand {

    public ServerGameStats() {
        super(Constants.SERVER_GAME_STATS, "Game Statistics for Administration");
        addOptions(new OptionData(OptionType.BOOLEAN, INCLUDE_HUB, "Include the HUB server in these stats"));
        addOptions(new OptionData(
                OptionType.BOOLEAN,
                SHOW_OLDEST_THREAD_INFO,
                "Show how old the oldest active thread is in each server"));
    }

    private static final String INCLUDE_HUB = "include_hub";
    private static final String SHOW_OLDEST_THREAD_INFO = "show_oldest_thread_info";

    public void execute(SlashCommandInteractionEvent event) {
        List<Guild> guildsToShow = new ArrayList<>(JdaService.serversToCreateNewGamesOn);

        boolean includeHub = event.getOption(INCLUDE_HUB, false, OptionMapping::getAsBoolean);
        if (includeHub || JdaService.guilds.size() == 1) guildsToShow.add(JdaService.guildPrimary);

        boolean showOldestThreadInfo = event.getOption(SHOW_OLDEST_THREAD_INFO, false, OptionMapping::getAsBoolean);

        int hostedGames = 0;
        int roomForGames = 0;

        List<Guild> guilds = guildsToShow.stream()
                .sorted(Comparator.comparing(Guild::getIdLong)) // Sort by creation date
                .toList();

        Map<String, Integer> guildToGameCount = new HashMap<>();

        for (Guild guild : guilds) {
            guildToGameCount.putIfAbsent(guild.getId(), 0);
        }

        GameManager.getManagedGames().stream()
                .map(ManagedGame::getMainGameChannel)
                .filter(Objects::nonNull)
                .distinct()
                .filter(channel -> channel.getParentCategory() != null
                        && !"The in-limbo PBD Archive"
                                .equals(channel.getParentCategory().getName()))
                .forEach(channel -> guildToGameCount.merge(channel.getGuild().getId(), 1, Integer::sum));

        StringBuilder sb = new StringBuilder();
        sb.append("## __Server Game Statistics__\n");
        for (Guild guild : guilds) {
            sb.append("**").append(guild.getName()).append("**\n");
            int roleCount = guild.getRoles().size(); // 250
            int guildRoomForGames = 250 - roleCount;
            int channelCount = guild.getChannels().size(); // 500
            guildRoomForGames = Math.min(guildRoomForGames, (500 - channelCount) / 2);
            int gameCount = guildToGameCount.get(guild.getId());
            sb.append("> hosting **").append(gameCount).append("** games  -  ");
            sb.append("space for **").append(guildRoomForGames).append("** more games\n");
            if (showOldestThreadInfo) {
                sb.append("> ").append(getOldestThreadString(guild)).append("\n");
            }
            hostedGames += gameCount;
            roomForGames += guildRoomForGames;
        }
        sb.append("\n**Total**\n");
        sb.append("> hosting **").append(hostedGames).append("** games  -  ");
        sb.append("space for **").append(roomForGames).append("** more games\n");
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb.toString());
    }

    private static String getOldestThreadString(Guild guild) {
        List<ThreadChannel> activeThreadChannels = guild.retrieveActiveThreads().complete().stream()
                .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
                .toList();
        if (activeThreadChannels.isEmpty()) {
            return "no active threads";
        }
        ThreadChannel oldestThread = activeThreadChannels.get(0);
        OffsetDateTime latestActivityTime = TimeUtil.getTimeCreated(oldestThread.getLatestMessageIdLong());
        Duration duration = Duration.between(latestActivityTime, OffsetDateTime.now());
        return "oldest active thread is %,d hours old".formatted(duration.toHours());
    }
}
