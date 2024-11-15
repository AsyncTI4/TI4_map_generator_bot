package ti4.commands.cardsac;

import java.util.Map;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.AsyncTI4DiscordBot;
import ti4.commands2.CommandHelper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SentAC extends ACCardsSubcommandData {
    public SentAC() {
        super(Constants.SEND_AC, "Send an Action Card to a player");
        addOptions(new OptionData(OptionType.INTEGER, Constants.ACTION_CARD_ID, "Action Card ID that is sent between ()").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Target faction or color").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player could not be found");
            return;
        }
        OptionMapping option = event.getOption(Constants.ACTION_CARD_ID);
        if (option == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Please select what Action Card to send");
            return;
        }

        int acIndex = option.getAsInt();
        String acID = null;
        for (Map.Entry<String, Integer> so : player.getActionCards().entrySet()) {
            if (so.getValue().equals(acIndex)) {
                acID = so.getKey();
            }
        }

        if (acID == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "No such Action Card ID found, please retry");
            return;
        }

        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "Player not found");
            return;
        }
        User user = AsyncTI4DiscordBot.jda.getUserById(otherPlayer.getUserID());
        if (user == null) {
            MessageHelper.sendMessageToChannel(event.getChannel(), "User for faction not found. Report to ADMIN");
            return;
        }

        // FoW specific pinging
        if (game.isFowMode()) {
            FoWHelper.pingPlayersTransaction(game, event, player, otherPlayer, Emojis.ActionCard + " Action Card", null);
        }

        sendActionCard(event, game, player, otherPlayer, acID);
    }

    public static void sendActionCard(GenericInteractionCreateEvent event, Game game, Player player, Player p2, String acID) {
        Integer handIndex = player.getActionCards().get(acID);
        ButtonHelper.checkACLimit(game, event, p2);
        if (acID == null || handIndex == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find AC in your hand.");
            return;
        }
        if (p2 == null) {
            MessageHelper.sendMessageToChannel(event.getMessageChannel(), "Could not find other player.");
            return;
        }

        player.removeActionCard(handIndex);
        p2.setActionCard(acID);
        ACInfo.sendActionCardInfo(game, player);
        ACInfo.sendActionCardInfo(game, p2);
    }
}
