package ti4.commands.admin;

import net.dv8tion.jda.api.entities.emoji.*;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import ti4.helpers.Constants;
import ti4.listeners.ButtonListener;
import ti4.message.BotLogger;

import java.util.List;

public class ResetEmojiCache extends AdminSubcommandData {

    public ResetEmojiCache() {
        super(Constants.RESET_EMOJI_CACHE, "Reset Emoji Cache for Button reactions");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        List<RichCustomEmoji> emojis = event.getJDA().getEmojis();
        for (Emoji emoji : emojis) {
            BotLogger.log(emoji.getName() + " " + emoji.getFormatted());
        }
        ButtonListener.emoteMap.clear();
    }
}
