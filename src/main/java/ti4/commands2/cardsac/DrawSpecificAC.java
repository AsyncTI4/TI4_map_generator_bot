package ti4.commands2.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.message.MessageHelper;

class DrawSpecificAC extends GameStateSubcommand {

    public DrawSpecificAC() {
        super(Constants.DRAW_SPECIFIC_AC, "Draw Specific Action Card", true, true);
        addOptions(new OptionData(OptionType.STRING, Constants.AC_ID, "ID of the card you wish to draw").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        var game = getGame();
        var player = getPlayer();
        String acId = event.getOption(Constants.AC_ID).getAsString();
        game.drawSpecificActionCard(acId, player.getUserID());

        int currentAcCount = player.getAc();
        if (currentAcCount == player.getAc()) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Card not drawn. It could be in someone's hand, or you could be using the wrong ID."
                + " Remember, you need the word ID (i.e `scramble` for _Scramble Frequency_) and not the number ID. You may find the word ID with the `/search action_cards` command.");
            return;
        }

        ActionCardHelper.sendActionCardInfo(game, getPlayer());
    }
}
