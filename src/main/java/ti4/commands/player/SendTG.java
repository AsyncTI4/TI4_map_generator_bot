package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.CommandHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.TransactionHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendTG extends PlayerSubcommandData {

    public SendTG() {
        super(Constants.SEND_TG, "Sent TG(s) to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade goods count").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to which you send TG(s)").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.CLEAR_DEBT, "True to automatically clear any debt with receiving player"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getActiveGame();
        Player player = CommandHelper.getPlayerFromEvent(game, event);
        if (player == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player could not be found");
            return;
        }
        Player otherPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (otherPlayer == null) {
            MessageHelper.sendMessageToEventChannel(event, "Player to send TGs/Commodities could not be found");
            return;
        }

        int sendTG = event.getOption(Constants.TG, 0, OptionMapping::getAsInt);
        int tg = player.getTg();
        sendTG = Math.min(sendTG, tg);
        tg -= sendTG;
        player.setTg(tg);
        ButtonHelperAbilities.pillageCheck(player, game);

        int targetTG = otherPlayer.getTg();
        targetTG += sendTG;
        otherPlayer.setTg(targetTG);
        ButtonHelperAbilities.pillageCheck(otherPlayer, game);

        String p1 = player.getRepresentation();
        String p2 = otherPlayer.getRepresentation();
        CommanderUnlockCheck.checkPlayer(player, "hacan");
        String tgString = sendTG + " " + Emojis.getTGorNomadCoinEmoji(game) + " trade goods";
        String message = p1 + " sent " + tgString + " to " + p2;
        MessageHelper.sendMessageToEventChannel(event, message);

        if (event.getOption(Constants.CLEAR_DEBT, false, OptionMapping::getAsBoolean)) {
            ClearDebt.clearDebt(otherPlayer, player, sendTG);
            MessageHelper.sendMessageToEventChannel(event, otherPlayer.getRepresentation() + " cleared " + sendTG + " debt tokens owned by " + player.getRepresentation());
        }

        if (game.isFowMode()) {
            String fail = "Could not notify receiving player.";
            String success = "The other player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(otherPlayer, game, event.getChannel(), message, fail, success);

            // Add extra message for transaction visibility
            FoWHelper.pingPlayersTransaction(game, event, player, otherPlayer, tgString, null);
        }
        TransactionHelper.checkTransactionLegality(game, player, otherPlayer);
    }
}
