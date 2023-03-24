package ti4.commands.explore;

import java.io.File;
import java.util.ArrayList;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.player.SendTG;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapManager;
import ti4.map.MapSaveLoadManager;
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
		Map activeMap = getActiveMap();
		User activeUser = getUser();
        Player sender = activeMap.getPlayers().get(activeUser.getId());
        sender = Helper.getGamePlayer(activeMap, sender, event, null);

		Player reciever = Helper.getPlayer(activeMap, null, event);
        if (reciever == null) {
        	sendMessage("Target player could not be found in game:" + activeMap.getName());
            return;
        }
        String trait = event.getOption(Constants.TRAIT).getAsString();
        OptionMapping countOption = event.getOption(Constants.COUNT);
        int count = 1;
        if (countOption != null) {
        	count = countOption.getAsInt();
        } 
        
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
        		reciever.addFragment(fragID);
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

		String message = Helper.getPlayerRepresentation(event, sender) + " sent " + trait + " " + Helper.getEmojiFromDiscord(emojiName) + " relic fragments to: " + Helper.getPlayerRepresentation(event, reciever);
		sendMessage(message);
		if (activeMap.isFoWMode()) {
			String fail = "User for faction not found. Report to ADMIN";
			String success = "The other player has been notified";
			MessageHelper.sendPrivateMessageToPlayer(reciever, activeMap, event, message, fail, success);
		}
	}
}
