package ti4.commands.explore;

import java.util.ArrayList;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.GenericInteractionCreateEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import ti4.generator.Mapper;
import ti4.helpers.ButtonHelper;
import ti4.helpers.ButtonHelperFactionSpecific;
import ti4.helpers.Constants;
import ti4.helpers.FoWHelper;
import ti4.helpers.Helper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendFragments extends ExploreSubcommandData {

	public SendFragments() {
		super(Constants.SEND_FRAGMENT, "Send a number of relic fragments (default 1) to another player");
		addOptions(
			typeOption.setRequired(true),
			new OptionData(OptionType.STRING, Constants.FACTION_COLOR, "Faction or Color").setRequired(true).setAutoComplete(true),
			new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of fragments (default 1)")
		);
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Game activeGame = getActiveMap();
		User activeUser = getUser();
        Player sender = activeGame.getPlayers().get(activeUser.getId());
        sender = Helper.getGamePlayer(activeGame, sender, event, null);

		Player receiver = Helper.getPlayer(activeGame, null, event);
        if (receiver == null) {
        	sendMessage("Target player could not be found in game:" + activeGame.getName());
            return;
        }
        String trait = event.getOption(Constants.TRAIT).getAsString();
        OptionMapping countOption = event.getOption(Constants.COUNT);
        int count = 1;
        if (countOption != null) {
        	count = countOption.getAsInt();
        }
		ButtonHelperFactionSpecific.pillageCheck(sender, activeGame);
		ButtonHelperFactionSpecific.pillageCheck(receiver, activeGame);
		sendFrags(event, sender, receiver, trait, count, activeGame);

	}
	public void sendFrags(GenericInteractionCreateEvent event, Player sender, Player receiver, String trait, int count, Game activeGame) {

        ArrayList<String> fragments = new ArrayList<>();
        for (String cardID : sender.getFragments()) {
        	String[] card = Mapper.getExplore(cardID).split(";");
        	if (card[1].equalsIgnoreCase(trait)) {
        		fragments.add(cardID);
        	}
        }

        if (fragments.size() >= count) {
        	for (int i=0; i<count; i++) {
        		String fragID = fragments.get(i);
        		sender.removeFragment(fragID);
        		receiver.addFragment(fragID);
        	}
        } else {
        	sendMessage("Not enough fragments of the specified trait");
        	return;
        }

		String emojiName = 	switch (trait){
			case "cultural" -> "CFrag";
			case "hazardous" -> "HFrag";
			case "industrial" -> "IFrag";
			case "frontier" -> "UFrag";
			default -> "";
		};

		String p1 = Helper.getPlayerRepresentation(sender, activeGame);
		String p2 = Helper.getPlayerRepresentation(receiver, activeGame);
		String fragString = count + " " + trait + " " + Helper.getEmojiFromDiscord(emojiName) + " relic fragments";
		String message =  p1 + " sent " + fragString + " to " + p2;
		MessageHelper.sendMessageToChannel(ButtonHelper.getCorrectChannel(receiver, activeGame), message);

		if (activeGame.isFoWMode()) {
			String fail = "User for faction not found. Report to ADMIN";
			String success = "The other player has been notified";
			MessageHelper.sendPrivateMessageToPlayer(receiver, activeGame, event, message, fail, success);

			// Add extra message for transaction visibility
			FoWHelper.pingPlayersTransaction(activeGame, event, sender, receiver, fragString, null);
		}
	}
}
