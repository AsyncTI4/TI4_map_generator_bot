package ti4.commands2.search;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.Subcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class SearchEmojisSubcommand extends Subcommand {

    public SearchEmojisSubcommand() {
        super(Constants.SEARCH_EMOJIS, "List all emojis the bot can use");
        addOptions(new OptionData(OptionType.STRING, Constants.SEARCH, "Searches the text and limits results to those containing this string."));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.INCLUDE_RAW_STRING, "Includes the raw emoji string for copy/paste"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        String searchString = event.getOption(Constants.SEARCH, "", OptionMapping::getAsString);
        boolean includeRAW = event.getOption(Constants.INCLUDE_RAW_STRING, false, OptionMapping::getAsBoolean);

        List<RichCustomEmoji> emojis = AsyncTI4DiscordBot.jda.getEmojis().stream()
                .filter(RichCustomEmoji::isAvailable)
                .filter(e -> e.getFormatted().toLowerCase().contains(searchString.toLowerCase()))
                .sorted(Comparator.comparing(Emoji::getName))
                .sorted(Comparator.comparing(e -> e.getGuild().getName()))
                .toList();

        String message = emojis.stream().map(e -> getEmojiMessage(e, includeRAW)).collect(Collectors.joining("\n"));

        if (emojis.size() > 3) {
            String threadName = event.getFullCommandName() + " search: " + searchString;
            MessageHelper.sendMessageToThread(event.getChannel(), threadName, message);
        } else {
            MessageHelper.sendMessageToChannel(event.getChannel(), message);
        }
    }

    private static String getEmojiMessage(RichCustomEmoji emoji, boolean includeRAW) {
        if (!includeRAW) return "# " + emoji.getFormatted();
        return emoji.getFormatted() + " `" + emoji.getFormatted() + "`";
    }
}
