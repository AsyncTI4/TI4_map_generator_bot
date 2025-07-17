package ti4.commands.franken;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.entities.emoji.UnicodeEmoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;

class SetFactionIcon extends GameStateSubcommand {

    public SetFactionIcon() {
        super(Constants.SET_FACTION_ICON, "Set franken faction icon to use", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_EMOJI, "Custom emoji to use. Enter jibberish to reset.").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color for which you set stats").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.CONFIRM, "YES to override Franken-only setting").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        OptionMapping confirmOption = event.getOption(Constants.CONFIRM);

        if (!game.isFrankenGame() && !"YES".equals(confirmOption == null ? "" : confirmOption.getAsString())) {
            MessageHelper.sendMessageToEventChannel(event, "This can only be run in Franken games.");
            player.setFactionEmoji(null);
            return;
        }

        String factionEmojiString = event.getOption(Constants.FACTION_EMOJI, null, OptionMapping::getAsString);

        Emoji factionEmoji = Emoji.fromFormatted(factionEmojiString);
        if (!(factionEmoji instanceof CustomEmoji || factionEmoji instanceof UnicodeEmoji)) {
            MessageHelper.sendMessageToEventChannel(event, factionEmojiString + " is not a supported emoji. Resetting to default.");
            player.setFactionEmoji(null);
            return;
        }
        if ((factionEmoji instanceof UnicodeEmoji))
        {
            MessageHelper.sendMessageToEventChannel(event, player.getRepresentationUnfogged() + " is setting their faction icon to " + factionEmojiString + ".");
            player.setFactionEmoji(factionEmojiString);
            return;
        }
        if ((factionEmoji instanceof CustomEmoji)) {
            MessageHelper.sendMessageToEventChannel(event, player.getRepresentationUnfogged() + " is setting their faction icon to " + factionEmojiString + ".");
            player.setFactionEmoji(factionEmojiString);
            return;
        }
        MessageHelper.sendMessageToEventChannel(event, "The bot cannot load " + factionEmojiString + ". Please use a custom emoji from one of the bot servers. Resetting to default.");
        player.setFactionEmoji(null);
    }
}
