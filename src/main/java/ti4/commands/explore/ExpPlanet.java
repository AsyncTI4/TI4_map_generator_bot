package ti4.commands.explore;

import java.util.ArrayList;
import java.util.StringTokenizer;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsSubcommandData;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Planet;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;
import ti4.commands.units.AddRemoveUnits;

public class ExpPlanet extends ExploreSubcommandData {

	public ExpPlanet() {
		super(Constants.EXP_PLANET, "Explore a specific planet.");
		addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Tile containing the planet").setRequired(true), 
				new OptionData(OptionType.STRING, Constants.PLANET_NAME, "Planet to explore").setRequired(true));
	}

	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String tileName = event.getOption(Constants.TILE_NAME).getAsString();
		String planetName = event.getOption(Constants.PLANET_NAME).getAsString();
		Map activeMap = getActiveMap();
		Tile tile = getTile(event, tileName, activeMap);
		planetName = AddRemoveUnits.getPlanet(event, tile, planetName);
		String planet = Mapper.getPlanet(planetName);
		StringTokenizer planetInfo = new StringTokenizer(planet, ",");
		//planetInfo.nextToken();
		String type = planetInfo.nextToken();
		String cardID = activeMap.drawExplore(type);
		
		StringBuilder sb = new StringBuilder();
		String cardInfo = Mapper.getExplore(cardID);
		sb.append("(").append(cardID).append(") ").append(cardInfo);
		MessageHelper.replyToMessage(event, sb.toString());
		MessageHelper.sendMessageToChannel(event.getChannel(), "Card has been discarded. Resolve effects and/or purge manually.");
	}
	
	private Tile getTile(SlashCommandInteractionEvent event, String tileID, Map activeMap) {
		if (activeMap.isTileDuplicated(tileID)) {
			MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
			return null;
		}
		Tile tile = activeMap.getTile(tileID);
		if (tile == null) {
			tile = activeMap.getTileByPostion(tileID);
		}
		if (tile == null) {
			MessageHelper.replyToMessage(event,  "Tile not found");
			return null;
		}
		return tile;
	}

}
