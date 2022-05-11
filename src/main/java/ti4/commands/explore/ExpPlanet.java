package ti4.commands.explore;

import java.io.File;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.cards.CardsSubcommandData;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
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
	
	public ExpPlanet(String name, String description) {
		super(name, description);
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
		String type = planetInfo.nextToken();
		String cardID = activeMap.drawExplore(type);
		MessageHelper.replyToMessage(event, displayExplore(cardID));
		
		String message = "Card has been discarded. Resolve effects manually.";
		String card = Mapper.getExplore(cardID);
		StringTokenizer cardInfo = new StringTokenizer(card, ";");
		String name = cardInfo.nextToken();
		String color = cardInfo.nextToken();
		String count = cardInfo.nextToken();
		String cardType = cardInfo.nextToken();
		String description = cardInfo.nextToken();
		if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
			Player player = activeMap.getPlayer(getUser().getId());
			if (color.equalsIgnoreCase(Constants.CULTURAL)) {
				player.setCrf(player.getCrf()+1);
			} else if (color.equalsIgnoreCase(Constants.INDUSTRIAL)) {
				player.setIrf(player.getIrf()+1);
			} else if (color.equalsIgnoreCase(Constants.HAZARDOUS)) {
				player.setHrf(player.getHrf()+1);
			} else {
				message = "Invalid fragment type drawn";
			}
			activeMap.purgeExplore(cardID);
			message = "Gained relic fragment";
		} else if (cardType.equalsIgnoreCase(Constants.ATTACH)) {
			String tokenFilename = null;
			while (tokenFilename == null) {
				String token = cardInfo.nextToken();
				tokenFilename = Mapper.getAttachmentID(token);
			}
			tile.addToken(tokenFilename, planetName);
			activeMap.purgeExplore(cardID);
			message = "Token added to planet";
		} else if (cardType.equalsIgnoreCase(Constants.EXP_TOKEN)) {
			String token = cardInfo.nextToken();
			String tokenFilename = Mapper.getAttachmentID(token);
			tile.addToken(tokenFilename, Constants.SPACE);
			message = "Token added to map";
		}
		
		MapSaveLoadManager.saveMap(activeMap);
		File file = GenerateMap.getInstance().saveImage(activeMap);
		MessageHelper.replyToMessage(event, file);
		MessageHelper.sendMessageToChannel(event.getChannel(), message);
	}
	
	protected Tile getTile(SlashCommandInteractionEvent event, String tileID, Map activeMap) {
		if (activeMap.isTileDuplicated(tileID)) {
			MessageHelper.replyToMessage(event, "Duplicate tile name found, please use position coordinates");
			return null;
		}
		Tile tile = activeMap.getTile(tileID);
		if (tile == null) {
			tile = activeMap.getTileByPosition(tileID);
		}
		if (tile == null) {
			MessageHelper.replyToMessage(event,  "Tile not found");
			return null;
		}
		return tile;
	}

	protected String displayExplore(String cardID) {
		StringBuilder sb = new StringBuilder();
		String card = Mapper.getExplore(cardID);
		StringTokenizer tokenizer = new StringTokenizer(card, ";");
		
		String name = tokenizer.nextToken();
		String color = tokenizer.nextToken();
		String count = tokenizer.nextToken();
		String type = tokenizer.nextToken();
		String description = tokenizer.nextToken();
		sb.append("(").append(cardID).append(") ").append(name).append(" - ").append(description);
		return sb.toString();
	}
	
}
