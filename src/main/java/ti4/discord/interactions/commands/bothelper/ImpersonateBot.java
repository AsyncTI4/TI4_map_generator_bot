package ti4.discord.interactions.commands.bothelper;

import java.util.Collections;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.unions.GuildChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import ti4.discord.interactions.commands.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class ImpersonateBot extends Subcommand {

    ImpersonateBot() {
        super("impersonate", "Speak as the bot");
        addOptions(new OptionData(OptionType.STRING, "text", "The thing to say").setRequired(true));
        addOptions(new OptionData(
                OptionType.CHANNEL, Constants.CHANNEL, "Channel to clean (defaults to current channel)"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String text = event.getOption("text").getAsString();

        MessageChannel channel = resolveTargetChannel(event);
        if (channel == null) return;

        var messageData = new MessageCreateBuilder()
                .addContent(text)
                .setAllowedMentions(Collections.emptyList())
                .build();
        channel.sendMessage(messageData).queue();
    }

    private static MessageChannel resolveTargetChannel(SlashCommandInteractionEvent event) {
        OptionMapping channelOption = event.getOption(Constants.CHANNEL);
        if (channelOption == null) return event.getChannel();
        GuildChannelUnion channel = channelOption.getAsChannel();
        if (channel.getType().isMessage()) return channel.asGuildMessageChannel();
        MessageHelper.sendMessageToEventChannel(event, "The selected channel must support messages.");
        return null;
    }
}
