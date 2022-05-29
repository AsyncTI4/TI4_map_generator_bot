package ti4.commands.explore;

import java.io.File;
import java.util.ArrayList;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.message.MessageHelper;

public class SendFragments extends ExploreSubcommandData {

	public SendFragments() {
		super(Constants.SEND_FRAGMENT, "Send a number of relic fragments (default 1) to another player");
		addOptions(
			typeOption.setRequired(true),
			new OptionData(OptionType.USER, Constants.PLAYER, "Player to send fragments to").setRequired(true),
			new OptionData(OptionType.INTEGER, Constants.COUNT, "Number of fragments (default 1)")
		);
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		User user = getUser();
		OptionMapping recieverOption = event.getOption(Constants.PLAYER);
		String playerID = recieverOption.getAsUser().getId();
        if (activeMap.getPlayer(playerID) == null) {
        	MessageHelper.sendMessageToChannel(event.getChannel(), "Player:" + recieverOption.getAsUser().getName() + " could not be found in map:" + activeMap.getName());
            return;
        }
        Player reciever = activeMap.getPlayers().get(playerID);
        Player sender = activeMap.getPlayers().get(user.getId());
        String color = event.getOption(Constants.EXPLORE_TYPE).getAsString();
        OptionMapping countOption = event.getOption(Constants.COUNT);
        int count = 1;
        if (countOption != null) {
        	count = event.getOption(Constants.COUNT).getAsInt();
        } 
        
        ArrayList<String> fragments = new ArrayList<>();
        for (String cardID : sender.getFragments()) {
        	String[] card = Mapper.getExplore(cardID).split(";");
        	if (card[1].equalsIgnoreCase(color)) {
        		fragments.add(cardID);
        	}
        }
        
        if (fragments.size() >= count) {
        	while (count > 0) {
        		count--;
        		String fragID = fragments.get(count);
        		sender.removeFragment(fragID);
        		reciever.addFragment(fragID);
        	}
        } else {
        	MessageHelper.replyToMessage(event, "Not enough fragments of the specified type");
        	return;
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(count).append(" ").append(color).append(" relic fragments sent to ").append(recieverOption.getAsUser().getName());
        MessageHelper.replyToMessage(event, sb.toString());
        
        MapSaveLoadManager.saveMap(activeMap);
        File file = GenerateMap.getInstance().saveImage(activeMap);
        MessageHelper.replyToMessage(event, file);
	}

}
