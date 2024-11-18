package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.TransactionHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendTG extends GameStateSubcommand {

    public SendTG() {
        super(Constants.SEND_TG, "Sent TG(s) to player/faction", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade goods count").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to which you send TG(s)").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.CLEAR_DEBT, "True to automatically clear any debt with receiving player"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color (defaults to you)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
        int sendTG = event.getOption(Constants.TG, 0, OptionMapping::getAsInt);
        int tg = player.getTg();
        sendTG = Math.min(sendTG, tg);
        tg -= sendTG;
        player.setTg(tg);
        ButtonHelperAbilities.pillageCheck(player, game);

        Player targetPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        int targetTG = targetPlayer.getTg();
        targetTG += sendTG;
        targetPlayer.setTg(targetTG);
        ButtonHelperAbilities.pillageCheck(targetPlayer, game);

        String p1 = player.getRepresentation();
        String p2 = targetPlayer.getRepresentation();
        CommanderUnlockCheck.checkPlayer(player, "hacan");
        String tgString = sendTG + " " + Emojis.getTGorNomadCoinEmoji(game) + " trade goods";
        String message = p1 + " sent " + tgString + " to " + p2;
        MessageHelper.sendMessageToEventChannel(event, message);

        if (event.getOption(Constants.CLEAR_DEBT, false, OptionMapping::getAsBoolean)) {
            ClearDebt.clearDebt(targetPlayer, player, sendTG);
            MessageHelper.sendMessageToEventChannel(event, targetPlayer.getRepresentation() + " cleared " + sendTG + " debt tokens owned by " + player.getRepresentation());
        }

        if (game.isFowMode()) {
            String fail = "Could not notify receiving player.";
            String success = "The other player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(targetPlayer, game, event.getChannel(), message, fail, success);

            // Add extra message for transaction visibility
            FoWHelper.pingPlayersTransaction(game, event, player, targetPlayer, tgString, null);
        }
        TransactionHelper.checkTransactionLegality(game, player, targetPlayer);
    }
}
