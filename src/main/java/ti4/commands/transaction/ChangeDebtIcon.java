package ti4.commands.transaction;

import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.fellbaum.jemoji.EmojiManager;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.TI4Emoji;

class ChangeDebtIcon extends GameStateSubcommand {

    ChangeDebtIcon() {
        super(Constants.CHANGE_DEBT_ICON, "Change the background icon for a given debt pool", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.DEBT_POOL, "Which debt pool change")
                .setAutoComplete(true)
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, "emoji", "Icon to use (can be any AsyncBot emoji)")
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

        TI4Emoji ti4Emoji = TI4Emoji.findEmojiFromJustName(icon);
        if ((ti4Emoji != null)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    player.getRepresentationUnfogged() + " is setting the \"" + pool + "\" icon to "
                            + ti4Emoji.emojiString() + ".");
            game.setDebtPoolIcon(pool, ti4Emoji.emojiString());
            return;
        }

        if (EmojiManager.isEmoji(icon)) {
            Emoji unicodeEmoji = Emoji.fromFormatted(icon);
            MessageHelper.sendMessageToEventChannel(
                    event,
                    player.getRepresentationUnfogged() + " is setting the \"" + pool + "\" icon to "
                            + unicodeEmoji.getFormatted() + ".");
            game.setDebtPoolIcon(pool, unicodeEmoji.getFormatted());
            return;
        }

        if (Constants.DEBT_DEFAULT_POOL.equals(pool)) {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "The bot cannot load \"" + icon
                            + "\". Please use a custom emoji from one of the bot servers. Resetting to default.");
            game.clearDebtPoolIcon(pool);
        } else {
            MessageHelper.sendMessageToEventChannel(
                    event,
                    "The bot cannot load \"" + icon
                            + "\". Please use a custom emoji from one of the bot servers. No change made.");
        }
    }

    @Override
    public boolean isSuspicious(SlashCommandInteractionEvent event) {
        return true;
    }
}
