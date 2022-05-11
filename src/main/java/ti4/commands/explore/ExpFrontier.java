package ti4.commands.explore;

import java.io.File;
import java.util.StringTokenizer;

import org.jetbrains.annotations.NotNull;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.map.UnitHolder;
import ti4.message.MessageHelper;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.helpers.Helper;

public class ExpFrontier extends ExpPlanet {
	public ExpFrontier() {
		super(Constants.FRONTIER, "Explore a frontier tile");
		addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "Location of the frontier tile").setRequired(true));
	}
	
	@Override
	public void execute(SlashCommandInteractionEvent event) {
		String tileName = event.getOption(Constants.TILE_NAME).getAsString();
		Map activeMap = getActiveMap();
		Tile tile = getTile(event, tileName, activeMap);
		
		UnitHolder space = tile.getUnitHolders().get(Constants.SPACE);
		String frontierFilename = Mapper.getTokenID(Constants.FRONTIER);
		if (space.getTokenList().contains(frontierFilename)) {
			space.removeToken(frontierFilename);
			String cardID = activeMap.drawExplore(Constants.FRONTIER);
			MessageHelper.replyToMessage(event, displayExplore(cardID));
			
			String message = "Card has been discarded. Resolve effects manually.";
			String card = Mapper.getExplore(cardID);
			StringTokenizer cardInfo = new StringTokenizer(card, ";");
			String name = cardInfo.nextToken();
			String color = cardInfo.nextToken();
			String count = cardInfo.nextToken();
			String cardType = cardInfo.nextToken();
			String description = cardInfo.nextToken();
			if (cardType.equalsIgnoreCase(Constants.FRAGMENT) && color.equalsIgnoreCase(Constants.FRONTIER)) {
				Player player = activeMap.getPlayer(getUser().getId());
				player.setVrf(player.getVrf()+1);
				message = "Gained relic fragment";
				activeMap.purgeExplore(cardID);
			} else if (cardType.equalsIgnoreCase(Constants.EXP_TOKEN)) {
				String token = cardInfo.nextToken();
				String tokenFilename = Mapper.getTokenID(token);
				tile.addToken(tokenFilename, Constants.SPACE);
				message = "Token added to map";
				if (token.equalsIgnoreCase(Constants.MIRAGE)) {
					Helper.addMirageToTile(tile);
					message = "Mirage added to map!";
				}
				activeMap.purgeExplore(cardID);
			}
			
			MapSaveLoadManager.saveMap(activeMap);
			File file = GenerateMap.getInstance().saveImage(activeMap);
			MessageHelper.replyToMessage(event, file);
			MessageHelper.sendMessageToChannel(event.getChannel(), message);
		} else {
			MessageHelper.replyToMessage(event, "No frontier token in given system.");
		}
		
	}

}