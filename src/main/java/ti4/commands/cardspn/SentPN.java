package ti4.commands.cardspn;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsInfo;
import ti4.commands.player.SendTG;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.Player;
import ti4.message.MessageHelper;

import java.io.File;

public class SentPN extends PNCardsSubcommandData {
	public SentPN() {
		super(Constants.SEND_PN, "Send Promissory Note to player");
		addOptions(new OptionData(OptionType.STRING, Constants.PROMISSORY_NOTE_ID,
				"Promissory Note ID that is sent between () or Name/Part of Name").setRequired(true));
		addOptions(new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true)
				.setAutoComplete(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		Player player = activeMap.getPlayer(getUser().getId());
		player = Helper.getGamePlayer(activeMap, player, event, null);
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
		boolean areaPN = false;
		Player targetPlayer = Helper.getPlayer(activeMap, null, event);

		if (targetPlayer == null) {
			sendMessage("No such Player in game");
			return;
		}

		String promissoryNoteOwner = Mapper.getPromissoryNoteOwner(id);
		if (player.getPromissoryNotesInPlayArea().contains(id)) {
			String playerColor = targetPlayer.getColor();
			String playerFaction = targetPlayer.getFaction();
			if (!(playerColor != null && playerColor.equals(promissoryNoteOwner))
					&& !(playerFaction != null && playerFaction.equals(promissoryNoteOwner))) {
				sendMessage("Can send Promissory Notes from Play Area just to Owner of the Note");
				return;
			}
		}

		player.removePromissoryNote(id);
		targetPlayer.setPromissoryNote(id);
		boolean sendSftT = false;
		boolean sendAlliance = false;
		if ((id.endsWith("_sftt") || id.endsWith("_an")) && !promissoryNoteOwner.equals(targetPlayer.getFaction())
				&& !promissoryNoteOwner.equals(targetPlayer.getColor())) {
			targetPlayer.setPromissoryNotesInPlayArea(id);
			if (id.endsWith("_sftt")) {
				sendSftT = true;
			} else {
				sendAlliance = true;
			}
			areaPN = true;
		}
		CardsInfo.sentUserCardInfo(event, activeMap, targetPlayer);
		CardsInfo.sentUserCardInfo(event, activeMap, player);
		String text = sendSftT ? "**Support for the Throne** " : (sendAlliance ? "**Alliance** " : "");
		String message = Helper.getPlayerRepresentation(event, player) + " sent " + Emojis.PN + text + "PN to "
				+ Helper.getPlayerRepresentation(event, targetPlayer);
		if (activeMap.isFoWMode()) {
			String fail = "User for faction not found. Report to ADMIN";
			String success = message + "\nThe other player has been notified";
			MessageHelper.sendPrivateMessageToPlayer(targetPlayer, activeMap, event, message, fail, success);
			sendMessage("PN sent");
		} else {
			sendMessage(message);
		}

		// FoW specific pinging
		if (activeMap.isFoWMode()) {
			int successCount = 0;
			// iterate through the player list. this may result in some extra pings, we'll
			// sort that out later
			for (Player player_ : activeMap.getPlayers().values()) {
				// let's figure out what they can see!
				boolean senderVisible = FoWHelper.canSeeStatsOfPlayer(activeMap, player_, player);
				boolean recieverVisible = FoWHelper.canSeeStatsOfPlayer(activeMap, player_, targetPlayer);

				// first off let's give full info for someone that can see both sides
				if (senderVisible && recieverVisible) {
					boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, message);
					successCount += success ? 1 : 0;
				} else if (senderVisible) {
					String tempMessage = Helper.getPlayerRepresentation(event, player) + " sent " + Emojis.PN + text
							+ "PN to " + "???";
					boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, tempMessage);
					successCount += success ? 1 : 0;
				} else if (recieverVisible) {
					String tempMessage = "???" + " sent " + Emojis.PN + text + "PN to "
							+ Helper.getPlayerRepresentation(event, targetPlayer);
					boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, tempMessage);
					successCount += success ? 1 : 0;
				} else if (sendSftT) {
					// notify score change! This is needed because someone without visiblity to
					// either player should find out that the score bar changed
					String tempMessage = "Scores changed";
					boolean success = MessageHelper.sendPrivateMessageToPlayer(player_, activeMap, tempMessage);
					successCount += success ? 1 : 0;
				}
			}
			if (successCount < activeMap.getPlayers().size()) {
				if (successCount < activeMap.getPlayers().values().size()) {
					MessageHelper.replyToMessage(event,
							"One more more pings failed to send.  Please follow up with game's GM.");

				} else {
					MessageHelper.replyToMessage(event, "Succesfully sent all pings.");
				}
			}

		}

		// Turned off, as we might change back
		if (areaPN && false) {
			File file = GenerateMap.getInstance().saveImage(activeMap, event);
			MessageHelper.sendFileToChannel(event.getChannel(), file);
		}
	}
}
