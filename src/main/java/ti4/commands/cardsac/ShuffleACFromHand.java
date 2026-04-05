package ti4.commands.cardsac;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ShuffleACFromHand extends GameStateSubcommand {

    ShuffleACFromHand() {
        super(Constants.SHUFFLE_AC_FROM_HAND, "Shuffle an action card from your hand back into the deck.", true, true);
        addOptions(new OptionData(
                        OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID, which is found between ()")
                .setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();
        Game game = getGame();
        Player player = getPlayer();
        String acID = null;
        for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
            if (ac.getValue().equals(acIndex)) {
                acID = ac.getKey();
            }
        }
        if (acID == null) {
            MessageHelper.sendMessageToChannel(
                    event.getChannel(), "No such action card ID found in your hand, please retry.");
            return;
        }
        game.shuffleActionCardFromHandIntoDeck(player.getUserID(), acIndex);
        String message = "Card shuffled back into deck from hand: "
                + Mapper.getActionCard(acID).getRepresentation(game);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ActionCardHelper.sendActionCardInfo(game, player);
    }
}
