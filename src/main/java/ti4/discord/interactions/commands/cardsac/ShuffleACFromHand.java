package ti4.discord.interactions.commands.cardsac;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.discord.interactions.commands.CommandHelper;
import ti4.discord.interactions.commands.GameStateSubcommand;
import ti4.game.Game;
import ti4.game.Player;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
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
        String acID = CommandHelper.requireOrReport(
                event,
                CommandHelper.cardIdForNumber(player.getActionCards(), acIndex),
                "No such action card ID found in your hand, please retry.");
        if (acID == null) return;
        game.shuffleActionCardFromHandIntoDeck(player.getUserID(), acIndex);
        String message = "Card shuffled back into deck from hand: "
                + Mapper.getActionCard(acID).getRepresentation(game);
        MessageHelper.sendMessageToChannel(event.getChannel(), message);
        ActionCardHelper.sendActionCardInfo(game, player);
    }
}
