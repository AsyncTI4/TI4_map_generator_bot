package ti4.commands2.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands2.CommandHelper;
import ti4.commands2.GameStateSubcommand;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.TransactionHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.service.emoji.MiscEmojis;

class SendCommodities extends GameStateSubcommand {

    public SendCommodities() {
        super(Constants.SEND_COMMODITIES, "Sent Commodities to player/faction", true, true);
        addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES, "Commodities count").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to which you send Commodities").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.CLEAR_DEBT, "True to automatically clear any debt with receiving player"));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color (defaults to you)").setAutoComplete(true));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        Player player = getPlayer();
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

        Player targetPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
        if (targetPlayer == null) {
            MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
            return;
        }
        if (!player.isPlayerMemberOfAlliance(targetPlayer)) {
            int targetTG = targetPlayer.getTg();
            targetTG += sendCommodities;
            targetPlayer.setTg(targetTG);
        } else {
            int targetTG = targetPlayer.getCommodities();
            targetTG += sendCommodities;
            if (targetTG > targetPlayer.getCommoditiesTotal()) {
                targetTG = targetPlayer.getCommoditiesTotal();
            }
            targetPlayer.setCommodities(targetTG);
        }

        String p1 = player.getRepresentation();
        String p2 = targetPlayer.getRepresentation();
        String commString = sendCommodities + " " + MiscEmojis.comm + " commodities";
        String message = p1 + " sent " + commString + " to " + p2;
        MessageHelper.sendMessageToEventChannel(event, message);
        ButtonHelperFactionSpecific.resolveDarkPactCheck(game, player, targetPlayer, sendCommodities);
        ButtonHelperAbilities.pillageCheck(targetPlayer, game);
        ButtonHelperAbilities.pillageCheck(player, game);

        if (event.getOption(Constants.CLEAR_DEBT, false, OptionMapping::getAsBoolean)) {
            targetPlayer.clearDebt(player, sendCommodities);
            MessageHelper.sendMessageToEventChannel(event, targetPlayer.getRepresentation() + " cleared " + sendCommodities + " debt tokens owned by " + player.getRepresentation());
        }

        if (game.isFowMode()) {
            String fail = "Could not notify receiving player.";
            String success = "The other player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(targetPlayer, game, event.getChannel(), message, fail, success);

            // Add extra message for transaction visibility
            FoWHelper.pingPlayersTransaction(game, event, player, targetPlayer, commString, null);
        }
        TransactionHelper.checkTransactionLegality(game, player, targetPlayer);
    }
}
