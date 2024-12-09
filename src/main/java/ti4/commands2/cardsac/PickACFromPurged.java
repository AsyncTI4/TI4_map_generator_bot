package ti4.commands2.cardsac;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class PickACFromPurged extends GameStateSubcommand {

    public PickACFromPurged() {
        super(Constants.PICK_AC_FROM_PURGED, "Pick an Action Card from the purged pile into your hand", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();

        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();
        String acID = null;
        for (Map.Entry<String, Integer> so : game.getPurgedActionCards().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                acID = so.getKey();
            }
        }
        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }

        boolean picked = game.pickActionCardFromPurged(player.getUserID(), acIndex);
        if (!picked) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }

        String sb = "Game: " + game.getName() + " " +
            "Player: " + player.getUserName() + "\n" +
            "Picked card from Purged: " +
            Mapper.getActionCard(acID).getRepresentation() + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);

        ActionCardHelper.sendActionCardInfo(game, player);
    }
}
