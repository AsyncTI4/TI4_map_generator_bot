package ti4.commands.admin;

import java.util.Map;

import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.helpers.RepositoryDispatchEvent;
import ti4.message.MessageHelper;

class GetChannelHtml extends Subcommand {

    GetChannelHtml() {
        super(Constants.GET_CHANNEL_HTML, "Dispatch the archive_game_channel GitHub action for a channel.");
        addOptions(new OptionData(
                        OptionType.CHANNEL, Constants.CHANNEL, "Channel to archive (defaults to current channel)")
                .setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageChannel channel = event.getOption(Constants.CHANNEL, null, OptionMapping::getAsChannel);
        String channelId =
                channel != null ? channel.getId() : event.getChannel().getId();
        new RepositoryDispatchEvent("archive_game_channel", Map.of("channel", channelId)).sendEvent();
        MessageHelper.sendMessageToEventChannel(
                event, "Dispatched `archive_game_channel` for channel <#" + channelId + ">.");
    }
}
