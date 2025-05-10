package ti4.commands.cardsac;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.GameStateSubcommand;
import ti4.image.Mapper;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class PurgeAC extends GameStateSubcommand {

    public PurgeAC() {
        super(Constants.PURGE_AC, "Purge an Action Card", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action card ID, which is found between ()").setRequired(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Player player = getPlayer();
        int acIndex = event.getOption(Constants.ACTION_CARD_ID).getAsInt();

        String acID = null;
        for (Map.Entry<String, Integer> ac : player.getActionCards().entrySet()) {
            if (ac.getValue().equals(acIndex)) {
                acID = ac.getKey();
            }
        }

        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such action card ID found, please retry.");
            return;
        }

        Game game = getGame();
        boolean removed = game.purgedActionCard(player.getUserID(), acIndex);
        if (!removed) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such action card ID found, please retry.");
            return;
        }

        String sb = "Player: " + player.getUserName() + " - " +
            "Purged Action Card:" + "\n" +
            Mapper.getActionCard(acID).getRepresentation() + "\n";
        MessageHelper.sendMessageToChannel(event.getChannel(), sb);
        ActionCardHelper.sendActionCardInfo(game, player);
    }
}
