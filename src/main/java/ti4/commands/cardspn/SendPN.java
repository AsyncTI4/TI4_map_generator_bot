package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;

public class SendPN extends PNCardsSubcommandData {
	public SendPN() {
		super(Constants.SEND_PN, "Send Promissory Note to player");
		addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID,"Promissory Note ID that is sent between () or Name/Part of Name").setRequired(true));
		addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
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
		OptionMapping option = event.getOption(Constants.PROMISSORY_NOTE_ID);
		if (option == null) {
			sendMessage("Please select what Promissory Note to send");
			return;
		}

		String value = option.getAsString().toLowerCase();
		String id = null;
		int pnIndex;
		try {
			pnIndex = Integer.parseInt(value);
			for (java.util.Map.Entry<String, Integer> so : player.getPromissoryNotes().entrySet()) {
				if (so.getValue().equals(pnIndex)) {
					id = so.getKey();
				}
			}
		} catch (Exception e) {
			boolean foundSimilarName = false;
			String cardName = "";
			for (java.util.Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
				String pnName = Mapper.getPromissoryNote(pn.getKey(), false);
				if (pnName != null) {
					pnName = pnName.toLowerCase();
					if (pnName.contains(value) || pn.getKey().contains(value)) {
						if (foundSimilarName && !cardName.equals(pnName)) {
							sendMessage("Multiple cards with similar name founds, please use ID");
							return;
						}
						id = pn.getKey();
						foundSimilarName = true;
						cardName = pnName;
					}
				}
			}
		}

		if (id == null) {
			sendMessage("No such Promissory Note ID found, please retry");
			return;
		}
		PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(id);
		if (pnModel == null) {
			sendMessage("No such Promissory Note found, please retry");
			return;
		}

		Player targetPlayer = Helper.getPlayer(activeGame, null, event);
		if (targetPlayer == null) {
			sendMessage("No such Player in game");
			return;
		}

		Player pnOwner = activeGame.getPNOwner(id);
		if (player.getPromissoryNotesInPlayArea().contains(id) ) {
			if (!targetPlayer.equals(pnOwner)) {
				sendMessage("Promissory Notes in Play Area can only be sent to the owner of the PN");
				return;
			}
		}
		ButtonHelperFactionSpecific.pillageCheck(player, activeGame);
		player.removePromissoryNote(id);
		ButtonHelperFactionSpecific.pillageCheck(targetPlayer, activeGame);
		targetPlayer.setPromissoryNote(id);

		boolean placeDirectlyInPlayArea = pnModel.isPlayedDirectlyToPlayArea();
		if (placeDirectlyInPlayArea && !targetPlayer.equals(pnOwner)&& !targetPlayer.isPlayerMemberOfAlliance(pnOwner)) {
			targetPlayer.setPromissoryNotesInPlayArea(id);
		}

		PNInfo.sendPromissoryNoteInfo(activeGame, targetPlayer, false);
		PNInfo.sendPromissoryNoteInfo(activeGame, player, false);

		String extraText = placeDirectlyInPlayArea ? "**" + pnModel.getName() + "**" : "";
		String message = Helper.getPlayerRepresentation(player, activeGame) + " sent " + Emojis.PN + extraText + " to " + Helper.getPlayerRepresentation(targetPlayer, activeGame);
		if (activeGame.isFoWMode()) {
			String fail = "User for faction not found. Report to ADMIN";
			String success = message + "\nThe other player has been notified";
			MessageHelper.sendPrivateMessageToPlayer(targetPlayer, activeGame, event, message, fail, success);
			sendMessage("PN sent");
		} else {
			sendMessage(message);
		}

		// FoW specific pinging
		if (activeGame.isFoWMode()) {
			String extra = null;
			if (id.endsWith("_sftt")) extra = "Scores changed.";
			FoWHelper.pingPlayersTransaction(activeGame, event, player, targetPlayer, Emojis.PN + extraText + "PN", extra);
		}
	}
}
