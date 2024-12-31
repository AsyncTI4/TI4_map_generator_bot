package ti4.commands.cardspn;

import java.util.Map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.CommandHelper;
import ti4.commands.GameStateSubcommand;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.PromissoryNoteHelper;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.PromissoryNoteModel;
import ti4.service.emoji.CardEmojis;

class SendPN extends GameStateSubcommand {

	public SendPN() {
		super(Constants.SEND_PN, "Send a promissory note to a player", true, true);
		addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID, "Promissory note ID, which is found between (), or name/part of name").setRequired(true));
		addOptions(new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true));
		addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Source faction or color (default is you)").setAutoComplete(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Player player = getPlayer();
		String value = event.getOption(Constants.PROMISSORY_NOTE_ID).getAsString().toLowerCase();
		String id = null;
		int pnIndex;
		try {
			pnIndex = Integer.parseInt(value);
			for (Map.Entry<String, Integer> so : player.getPromissoryNotes().entrySet()) {
				if (so.getValue().equals(pnIndex)) {
					id = so.getKey();
				}
			}
		} catch (Exception e) {
			boolean foundSimilarName = false;
			String cardName = "";
			for (Map.Entry<String, Integer> pn : player.getPromissoryNotes().entrySet()) {
				String pnName = Mapper.getPromissoryNote(pn.getKey()).getName();
				if (pnName != null) {
					pnName = pnName.toLowerCase();
					if (pnName.contains(value) || pn.getKey().contains(value)) {
						if (foundSimilarName && !cardName.equals(pnName)) {
							MessageHelper.sendMessageToEventChannel(event, "Multiple cards with similar name founds, please use ID.");
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
			MessageHelper.sendMessageToEventChannel(event, "No such promissory note ID found, please retry.");
			return;
		}
		PromissoryNoteModel pnModel = Mapper.getPromissoryNotes().get(id);
		if (pnModel == null) {
			MessageHelper.sendMessageToEventChannel(event, "No such promissory note found, please retry.");
			return;
		}

		Game game = getGame();
		Player targetPlayer = CommandHelper.getOtherPlayerFromEvent(game, event);
		if (targetPlayer == null) {
			MessageHelper.replyToMessage(event, "Unable to determine who the target player is.");
			return;
		}

		Player pnOwner = game.getPNOwner(id);
		if (player.getPromissoryNotesInPlayArea().contains(id)) {
			if (!targetPlayer.equals(pnOwner)) {
				MessageHelper.sendMessageToEventChannel(event, "Promissory notes in play area may only be sent to the owner of the promissory note.");
				return;
			}
		}
		ButtonHelperAbilities.pillageCheck(player, game);
		player.removePromissoryNote(id);
		ButtonHelperAbilities.pillageCheck(targetPlayer, game);
		targetPlayer.setPromissoryNote(id);

		if (id.contains("dspnveld") && !targetPlayer.ownsPromissoryNote(id)) {
			PromissoryNoteHelper.resolvePNPlay(id, targetPlayer, game, event);
		}

		boolean placeDirectlyInPlayArea = pnModel.isPlayedDirectlyToPlayArea();
		if (placeDirectlyInPlayArea && !targetPlayer.equals(pnOwner) && !targetPlayer.isPlayerMemberOfAlliance(pnOwner)) {
			targetPlayer.setPromissoryNotesInPlayArea(id);
		}

		PromissoryNoteHelper.sendPromissoryNoteInfo(game, targetPlayer, false);
		PromissoryNoteHelper.sendPromissoryNoteInfo(game, player, false);

		String conditionalPNName = placeDirectlyInPlayArea ? "_" + pnModel.getName() + "_" : "a promissory note";
        String preposition = placeDirectlyInPlayArea ? " directly to the play area of " : " to the hand of ";
		String message = player.getRepresentation() + " sent " + CardEmojis.PN + conditionalPNName + preposition + targetPlayer.getRepresentation();
		if (game.isFowMode()) {
			String fail = "User for faction not found. Report to ADMIN.";
			String success = message + "\nThe other player has been notified.";
			MessageHelper.sendPrivateMessageToPlayer(targetPlayer, game, event, message, fail, success);
			MessageHelper.sendMessageToEventChannel(event, "Promissory note sent.");
		} else {
			MessageHelper.sendMessageToEventChannel(event, message);
		}

		// FoW specific pinging
		if (game.isFowMode()) {
			String extra = null;
			if (id.endsWith("_sftt")) extra = "Scores changed.";
			FoWHelper.pingPlayersTransaction(game, event, player, targetPlayer, CardEmojis.PN + conditionalPNName + " promissory note", extra);
		}
	}
}
