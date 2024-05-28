package ti4.commands.cardsac;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class PickACFromDiscard extends ACCardsSubcommandData {
    public PickACFromDiscard() {
        super(Constants.PICK_AC_FROM_DISCARD, "Pick an Action Card from discard pile into your hand");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true).setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = game.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(game, player, event, null);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Action Card to draw from discard pile");
            return;
        }

        int acIndex = option.getAsInt();
        getActionCardFromDiscard(event, game, player, acIndex);
    }

    public static void getActionCardFromDiscard(GenericInteractionCreateEvent event, Game game, Player player, int acIndex) {
        String acId = null;
        for (Map.Entry<String, Integer> ac : game.getDiscardActionCards().entrySet()) {
            if (ac.getValue().equals(acIndex)) {
                acId = ac.getKey();
            }
        }

        if (acId == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry");
            return;
        }
        boolean picked = game.pickActionCard(player.getUserID(), acIndex);
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "No such Action Card ID found, please retry");
            return;
        }
        String sb = "Game: " + game.getName() + " " +
            "Player: " + player.getUserName() + "\n" +
            "Picked card from Discards: " +
            Mapper.getActionCard(acId).getRepresentation() + "\n";
        MessageHelper.sendMessageToChannel(event.getMessageChannel(), sb);

        ACInfo.sendActionCardInfo(game, player);
    }
}
