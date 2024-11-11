package ti4.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.PlayerGameStateSubcommand;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

public class DrawSpecificAC extends PlayerGameStateSubcommand {

    public DrawSpecificAC() {
        super(Constants.DRAW_SPECIFIC_AC, "Draw Specific Action Card", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.AC_ID, "ID of the card you want to draw").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var game = getGame();
        var player = getPlayer();
        int currentAcCount = player.getAc();
        String acId = event.getOption(Constants.AC_ID).getAsString();

        game.drawSpecificActionCard(acId, player.getUserID());

        if (currentAcCount == player.getAc()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Card not drawn. It could be in someone's hand, or you could be using the wrong ID. Remember, you need the word ID (i.e scramble for Scramble Frequency) and not the number ID. You may find the word ID by proper usage of the /search command.");
            return;
        }

        ACInfo.sendActionCardInfo(game, player);
    }
}
