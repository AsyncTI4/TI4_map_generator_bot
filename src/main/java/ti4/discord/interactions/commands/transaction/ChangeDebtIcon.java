package ti4.discord.interactions.commands.transaction;

import java.util.List;
import javax.annotation.Nullable;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.RichCustomEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.fellbaum.jemoji.EmojiManager;
import org.apache.commons.lang3.StringUtils;
import ti4.discord.JdaService;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;
import ti4.service.emoji.ApplicationEmojiService;
import ti4.service.emoji.TI4Emoji;

class ChangeDebtIcon extends GameStateSubcommand {

    ChangeDebtIcon() {
        super(Constants.CHANGE_DEBT_ICON, "Change the background icon for a given debt pool", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.DEBT_POOL, "Which debt pool change")
                .setAutoComplete(true)
                .setRequired(true));
        addOptions(new OptionData(
                        OptionType.STRING,
                        "emoji",
                        "Icon to use (any AsyncBot emoji, or a custom emoji of this server)")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        String icon = event.getOption("emoji", null, OptionMapping::getAsString);
        String pool = event.getOption(Constants.DEBT_POOL, null, OptionMapping::getAsString);
        if (pool == null) {
            pool = Constants.DEBT_DEFAULT_POOL;
        }

        String resolvedIcon = resolveIcon(icon, event.getGuild());
        if (resolvedIcon != null) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    player.getRepresentationUnfogged() + " is setting the \"" + pool + "\" icon to " + resolvedIcon
                            + ".");
            game.setDebtPoolIcon(pool, resolvedIcon);
            return;
        }

        if (Constants.DEBT_DEFAULT_POOL.equals(pool)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "The bot cannot load \"" + icon
                            + "\". Please use a bot emoji, a standard emoji, or a custom emoji from this server."
                            + " Resetting to default.");
            game.clearDebtPoolIcon(pool);
        } else {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "The bot cannot load \"" + icon
                            + "\". Please use a bot emoji, a standard emoji, or a custom emoji from this server."
                            + " No change made.");
        }
    }

    @Nullable
    private static String resolveIcon(String icon, @Nullable Guild guild) {
        TI4Emoji ti4Emoji = TI4Emoji.findEmojiFromJustName(icon);
        if (ti4Emoji != null) {
            return ti4Emoji.emojiString();
        }
        if (EmojiManager.isEmoji(icon)) {
            return Emoji.fromFormatted(icon).getFormatted();
        }
        CustomEmoji customEmoji = findAccessibleCustomEmoji(icon, guild);
        return customEmoji == null ? null : customEmoji.getFormatted();
    }

    @Nullable
    private static CustomEmoji findAccessibleCustomEmoji(String icon, @Nullable Guild guild) {
        if (Message.MentionType.EMOJI.getPattern().matcher(icon).matches()) {
            CustomEmoji customEmoji = (CustomEmoji) Emoji.fromFormatted(icon);
            return isAccessible(customEmoji) ? customEmoji : null;
        }
        if (guild == null) {
            return null;
        }
        List<RichCustomEmoji> matches = guild.getEmojisByName(StringUtils.strip(icon.trim(), ":"), true);
        return matches.size() == 1 ? matches.getFirst() : null;
    }

    private static boolean isAccessible(CustomEmoji emoji) {
        return ApplicationEmojiService.isValidAppEmoji(emoji) || JdaService.jda.getEmojiById(emoji.getId()) != null;
    }
}
