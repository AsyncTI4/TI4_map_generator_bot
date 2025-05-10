package ti4.commands.cardsac;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ActionCardHelper;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.CardEmojis;

class SendAC extends GameStateSubcommand {

    public SendAC() {
        super(Constants.SEND_AC, "Send an action card to a player", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action card ID to send").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to send to").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
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
        Player playerToSendTo = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (playerToSendTo == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }

        // FoW specific pinging
        if (game.isFowMode()) {
            FoWHelper.pingPlayersTransaction(game, event, player, playerToSendTo, CardEmojis.ActionCard + " Action Card", null);
        }

        ActionCardHelper.sendActionCard(event, game, player, playerToSendTo, acID);
    }
}
