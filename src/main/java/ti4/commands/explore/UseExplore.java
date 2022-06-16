package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class UseExplore extends ExploreSubcommandData {

	public UseExplore() {
		super(Constants.USE, "Draw and activate an explore card from the deck or discard");
		addOptions(
				idOption.setRequired(true),
				new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile to use explore card on, if any"),
				new OptionData(OptionType.STRING, Constants.PLANET_NAME, "Planet to use explore card on, if any"));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		Map activeMap = getActiveMap();
		String id = event.getOption(Constants.EXPLORE_CARD_ID).getAsString();
		if (activeMap.pickExplore(id) != null) {
			OptionMapping planetOption = event.getOption(Constants.PLANET_NAME);
			String planetName = null;
			if (planetOption != null) {
				planetName = planetOption.getAsString();
			}
			OptionMapping tileOption = event.getOption(Constants.TILE_NAME);
			Tile tile = null;
			if (tileOption != null) {
				tile = getTile(event, tileOption.getAsString().toLowerCase(), activeMap);
				if (tile == null) return;
			}
			String messageText = "Used card: " + id + " by player: " + activeMap.getPlayer(event.getUser().getId()).getUserName();
			resolveExplore(event, id, tile, planetName, messageText);
		} else {
			MessageHelper.replyToMessage(event, "Invalid card ID");
		}
	}
	
}
