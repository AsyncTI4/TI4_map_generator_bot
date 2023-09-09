package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendCommodities extends PlayerSubcommandData {
    public SendCommodities() {
        super(Constants.SEND_COMMODITIES, "Sent Commodities to player/faction");
        addOptions(new OptionData(OptionType.INTEGER, Constants.COMMODITIES, "Commodities count").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send Commodities").setAutoComplete(true).setRequired(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.CLEAR_DEBT, "True to automatically clear any debt with receiving player"));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {

        Game activeGame = getActiveMap();
        Player player = activeGame.getPlayer(getUser().getId());
        player = Helper.getGamePlayer(activeGame, player, event, null);
        if (player == null) {
            sendMessage("Player could not be found");
            return;
        }
        Player player_ = Helper.getPlayer(activeGame, player, event);
        if (player_ == null) {
            sendMessage("Player to send TG/Commodities could not be found");
            return;
        }

        int sendCommodities = event.getOption(Constants.COMMODITIES, 0, OptionMapping::getAsInt);
        int commodities = player.getCommodities();
        sendCommodities = Math.min(sendCommodities, commodities);
        commodities -= sendCommodities;
        player.setCommodities(commodities);

        if(!player.isPlayerMemberOfAlliance(player_)){
            int targetTG = player_.getTg();
            targetTG += sendCommodities;
            player_.setTg(targetTG);
        }else{
            int targetTG = player_.getCommodities();
            targetTG += sendCommodities;
            if(targetTG > player_.getCommoditiesTotal()){
                targetTG = player_.getCommoditiesTotal();
            }
            player_.setCommodities(targetTG);
        }
        
        String p1 = Helper.getPlayerRepresentation(player, activeGame);
        String p2 = Helper.getPlayerRepresentation(player_, activeGame);
        String commString = sendCommodities + " " + Emojis.comm + " commodities";
        String message =  p1 + " sent " + commString + " to " + p2;
        sendMessage(message);
        ButtonHelperFactionSpecific.pillageCheck(player_, activeGame);
        ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
        ButtonHelperFactionSpecific.resolveDarkPactCheck(activeGame, player, player_, sendCommodities, event);

        if (event.getOption(Constants.CLEAR_DEBT, false, OptionMapping::getAsBoolean)) {
			ClearDebt.clearDebt(player_, player, sendCommodities);
			sendMessage(Helper.getPlayerRepresentation(player_, activeGame) + " cleared " + sendCommodities + " debt tokens owned by " + Helper.getPlayerRepresentation(player, activeGame));
		}

        if (activeGame.isFoWMode()) {
            String fail = "Could not notify receiving player.";
            String success = "The other player has been notified";
            MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, event.getChannel(), message, fail, success);

            // Add extra message for transaction visibility
            FoWHelper.pingPlayersTransaction(activeGame, event, player, player_, commString, null);
        }
        
    }
}
