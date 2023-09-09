package ti4.commands.player;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendTG extends PlayerSubcommandData {
	public SendTG() {
		super(Constants.SEND_TG, "Sent TG to player/faction");
		addOptions(new OptionData(OptionType.INTEGER, Constants.TG, "Trade goods count").setRequired(true));
		addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color to which you send TG").setAutoComplete(true).setRequired(true));
		addOptions(new OptionData(OptionType.BOOLEAN, Constants.CLEAR_DEBT, "True to automatically clear any debt with receiving player"));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {

		Game activeGame = getActiveGame();
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

		int sendTG = event.getOption(Constants.TG, 0, OptionMapping::getAsInt);
		int tg = player.getTg();
		sendTG = Math.min(sendTG, tg);
		tg -= sendTG;
		player.setTg(tg);
		ButtonHelperFactionSpecific.pillageCheck(player, activeGame);

		int targetTG = player_.getTg();
		targetTG += sendTG;
		player_.setTg(targetTG);
		ButtonHelperFactionSpecific.pillageCheck(player_, activeGame);

		String p1 = Helper.getPlayerRepresentation(player, activeGame);
		String p2 = Helper.getPlayerRepresentation(player_, activeGame);
		if(player_.getLeaderIDs().contains("hacancommander") && !player_.hasLeaderUnlocked("hacancommander")){
			ButtonHelper.commanderUnlockCheck(player_, activeGame, "hacan", event);
		}
		String tgString = sendTG + " " + Emojis.tg + " trade goods";
		String message =  p1 + " sent " + tgString + " to " + p2;
		sendMessage(message);

		if (event.getOption(Constants.CLEAR_DEBT, false, OptionMapping::getAsBoolean)) {
			ClearDebt.clearDebt(player_, player, sendTG);
			sendMessage(Helper.getPlayerRepresentation(player_, activeGame) + " cleared " + sendTG + " debt tokens owned by " + Helper.getPlayerRepresentation(player, activeGame));
		}

		if (activeGame.isFoWMode()) {
			String fail = "Could not notify receiving player.";
			String success = "The other player has been notified";
			MessageHelper.sendPrivateMessageToPlayer(player_, activeGame, event.getChannel(), message, fail, success);

			// Add extra message for transaction visibility
			FoWHelper.pingPlayersTransaction(activeGame, event, player, player_, tgString, null);
		}
		
	}
}
