package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.TransactionHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendCommodities extends PlayerSubcommandData {
    public SendCommodities() {
        super(Constants.SEND_COMMODITIES, "Sent Commodities to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES, "Commodities count").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to which you send Commodities").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true));
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
            MessageHelper.sendMessageToEventChannel(event, "Player to send TG/Commodities could not be found");
            return;
        }
        if (player.hasAbility("military_industrial_complex")) {
            MessageHelper.sendMessageToChannel(player.getCorrectChannel(), player.getRepresentationUnfogged()
                + " since you cannot send players commodities due to your faction ability, sending comms here seems likely an error. Nothing has been processed as a result. Try a different route if this correction is wrong");
            return;
        }

        int sendCommodities = event.getOption(Constants.COMMODITIES, 0, OptionMapping::getAsInt);
        int commodities = player.getCommodities();
        sendCommodities = Math.min(sendCommodities, commodities);
        commodities -= sendCommodities;
        player.setCommodities(commodities);

        if (!player.isPlayerMemberOfAlliance(otherPlayer)) {
            int targetTG = otherPlayer.getTg();
            targetTG += sendCommodities;
            otherPlayer.setTg(targetTG);
        } else {
            int targetTG = otherPlayer.getCommodities();
            targetTG += sendCommodities;
            if (targetTG > otherPlayer.getCommoditiesTotal()) {
                targetTG = otherPlayer.getCommoditiesTotal();
            }
            otherPlayer.setCommodities(targetTG);
        }

        String p1 = player.getRepresentation();
        String p2 = otherPlayer.getRepresentation();
        String commString = sendCommodities + " " + Emojis.comm + " commodities";
        String message = p1 + " sent " + commString + " to " + p2;
        MessageHelper.sendMessageToEventChannel(event, message);
        ButtonHelperFactionSpecific.resolveDarkPactCheck(game, player, otherPlayer, sendCommodities);
        ButtonHelperAbilities.pillageCheck(otherPlayer, game);
        ButtonHelperAbilities.pillageCheck(player, game);

        if (event.getOption(Constants.CLEAR_DEBT, false, OptionMapping::getAsBoolean)) {
            ClearDebt.clearDebt(otherPlayer, player, sendCommodities);
            MessageHelper.sendMessageToEventChannel(event, otherPlayer.getRepresentation() + " cleared " + sendCommodities + " debt tokens owned by " + player.getRepresentation());
        }

        if (game.isFowMode()) {
            String fail = "Could not notify receiving player.";
            String success = "The other player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(otherPlayer, game, event.getChannel(), message, fail, success);

            // Add extra message for transaction visibility
            FoWHelper.pingPlayersTransaction(game, event, player, otherPlayer, commString, null);
        }
        TransactionHelper.checkTransactionLegality(game, player, otherPlayer);
    }
}
