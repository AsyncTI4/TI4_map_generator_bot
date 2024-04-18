package ti4.commands.bothelper;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.TimeUtil;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class ListOldChannels extends BothelperSubcommandData {
    public ListOldChannels() {
        super(Constants.LIST_OLD_CHANNELS, "List the oldest channels");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of channels to list (1 to 500)").setRequired(true));
    }

    public void execute(SlashCommandInteractionEvent event) {
        int channelCount = event.getOption(Constants.COUNT).getAsInt();
        if (channelCount < 1 || channelCount > 500) {
            MessageHelper.sendMessageToEventChannel(event, "Please choose a number between 1 and 500");
            return;
        }
        Guild guild = event.getGuild();
        MessageHelper.sendMessageToEventChannel(event, getOldChannelsMessage(guild, channelCount));
    }

    public static String getOldChannelsMessage(Guild guild, Integer channelCount) {
        List<TextChannel> channels = guild.getTextChannels();
        channels = channels.stream()
            .filter(c -> c.getLatestMessageIdLong() != 0)
            .sorted(Comparator.comparing(MessageChannel::getLatestMessageId))
            .limit(channelCount)
            .toList();

        StringBuilder sb = new StringBuilder("Least Active Channels:\n");
        for (TextChannel channel : channels) {
            OffsetDateTime latestActivityTime = TimeUtil.getTimeCreated(channel.getLatestMessageIdLong());
            Duration duration = Duration.between(latestActivityTime.toLocalDateTime(), OffsetDateTime.now().toLocalDateTime());
            sb.append("> `").append(latestActivityTime).append(" (").append(duration.toDays()).append(" days ago)`  ").append(channel.getAsMention()).append("\n");
        }
        return sb.toString();
    }
}
