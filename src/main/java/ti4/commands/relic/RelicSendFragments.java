package ti4.commands.relic;

import java.util.ArrayList;
import java.util.List;

import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.leaders.CommanderUnlockCheck;
import ti4.commands2.CommandHelper;
import ti4.generator.Mapper;
import ti4.helpers.ButtonHelperAbilities;
import ti4.helpers.Constants;
import ti4.helpers.Emojis;
import ti4.helpers.FoWHelper;
import ti4.helpers.TransactionHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;
import ti4.model.ExploreModel;

public class RelicSendFragments extends RelicSubcommandData {

	public RelicSendFragments() {
		super(Constants.SEND_FRAGMENT, "Send a number of relic fragments (default 1) to another player");
		addOptions(
			typeOption.setRequired(true),
			new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setAutoComplete(true),
			new OptionData(OptionType.STRING, Constants.TARGET_FACTION_OR_COLOR, "Faction or Color to send to").setRequired(true).setAutoComplete(true),
			new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of fragments (default 1)"));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Game game = getActiveGame();
		Player sender = CommandHelper.getPlayerFromEvent(game, event);

		Player receiver = CommandHelper.getOtherPlayerFromEvent(game, event);
		if (receiver == null) {
			MessageHelper.sendMessageToEventChannel(event, "Target player could not be found in game:" + game.getName());
			return;
		}
		String trait = event.getOption(Constants.TRAIT, null, OptionMapping::getAsString);
		int count = event.getOption(Constants.COUNT, 1, OptionMapping::getAsInt);
		ButtonHelperAbilities.pillageCheck(sender, game);
		ButtonHelperAbilities.pillageCheck(receiver, game);
		sendFrags(event, sender, receiver, trait, count, game);

	}

	public void sendFrags(GenericInteractionCreateEvent event, Player sender, Player receiver, String trait, int count, Game game) {
		List<String> fragments = new ArrayList<>();
		for (String cardID : sender.getFragments()) {
			ExploreModel card = Mapper.getExplore(cardID);
			if (card.getType().equalsIgnoreCase(trait)) {
				fragments.add(cardID);
			}
		}

		if (fragments.size() >= count) {
			for (int i = 0; i < count; i++) {
				String fragID = fragments.get(i);
				sender.removeFragment(fragID);
				receiver.addFragment(fragID);
			}
		} else {
			MessageHelper.sendMessageToEventChannel(event, "Not enough fragments of the specified trait");
			return;
		}

		String emojiName = switch (trait) {
			case "cultural" -> "CFrag";
			case "hazardous" -> "HFrag";
			case "industrial" -> "IFrag";
			case "frontier" -> "UFrag";
			default -> "";
		};

		String p1 = sender.getRepresentation();
		String p2 = receiver.getRepresentation();
		String fragString = count + " " + trait + " " + Emojis.getEmojiFromDiscord(emojiName) + " relic fragments";
		String message = p1 + " sent " + fragString + " to " + p2;
		if (!game.isFowMode()) {
			MessageHelper.sendMessageToChannel(receiver.getCorrectChannel(), message);
		}
		CommanderUnlockCheck.checkPlayer(receiver, "kollecc", "bentor");

		if (game.isFowMode()) {
			String fail = "User for faction not found. Report to ADMIN";
			String success = "The other player has been notified";
			MessageHelper.sendPrivateMessageToPlayer(receiver, game, event, message, fail, success);

			// Add extra message for transaction visibility
			FoWHelper.pingPlayersTransaction(game, event, sender, receiver, fragString, null);
		}
		TransactionHelper.checkTransactionLegality(game, sender, receiver);
		Player player = receiver;
		CommanderUnlockCheck.checkPlayer(player, "kollecc");
	}
}
