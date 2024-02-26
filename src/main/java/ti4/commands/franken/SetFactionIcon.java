package ti4.commands.franken;

import net.dv8tion.jda.api.entities.emoji.CustomEmoji;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;

import java.lang.reflect.Field;

public class SetFactionIcon extends FrankenSubcommandData {

    public SetFactionIcon() {
        super(Constants.SET_FACTION_ICON, "Set franken faction icon to use");
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_EMOJI, "Custom emoji to use. Enter jibberish to reset.").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveGame();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }

        if (!activeGame.isFrankenGame()) {
            sendMessage("This can only be run in Franken games.");
            player.setFactionEmoji(null);
            return;
        }
        
        String factionEmojiString = event.getOption(Constants.FACTION_EMOJI, null, OptionMapping::getAsString);

        Emoji factionEmoji = Emoji.fromFormatted(factionEmojiString);
        if (!(factionEmoji instanceof CustomEmoji)) {
            sendMessage(factionEmojiString + " is not a custom emoji. Resetting to default.");
            player.setFactionEmoji(null);
            return;
        }
        if (AsyncTI4DiscordBot.jda.getEmojiById(((CustomEmoji) factionEmoji).getId()) == null) {
            sendMessage("The bot cannot load " + factionEmojiString + ". Please use a custom emoji from one of the bot servers. Resetting to default.");
            player.setFactionEmoji(null);
            return;
        }
        player.setFactionEmoji(factionEmojiString);
        
    }
    
}
