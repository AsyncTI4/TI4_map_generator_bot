package ti4.commands.cardsac;

import java.util.Map;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

class ShowAC extends GameStateSubcommand {

    public ShowAC() {
        super(Constants.SHOW_AC, "Show an action card to one player", true, true);
        addOptions(new OptionData(
                        OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID, which is found between ()")
                .setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color")
                .setRequired(true)
                .setAutoComplete(true));
        addOptions(
                new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
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
        String sb = "---------\n" + "Game: "
                + game.getName() + "\n" + "Player: "
                + player.getUserName() + "\n" + "Shown Action Cards:"
                + "\n" + Mapper.getActionCard(acID).getRepresentation()
                + "\n" + "---------\n";
        player.setActionCard(acID);

        Player playerToShowTo = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (playerToShowTo == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }

        ActionCardHelper.sendActionCardInfo(game, player);
        MessageHelper.sendMessageToPlayerCardsInfoThread(playerToShowTo, sb);
    }
}
