package ti4.commands.explore;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.commands.units.AddRemoveUnits;
import ti4.generator.GenerateMap;
import ti4.generator.Mapper;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.MapSaveLoadManager;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.message.MessageHelper;

import java.io.File;
import java.util.StringTokenizer;

public class ExpPlanet extends ExploreSubcommandData {

    public ExpPlanet() {
        super(Constants.PLANET, "Explore a specific planet.");
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
        String type = planetInfo.nextToken();
        String cardID = activeMap.drawExplore(type);
        MessageHelper.replyToMessage(event, displayExplore(cardID));

        String message = "Card has been discarded. Resolve effects manually.";
        String card = Mapper.getExplore(cardID);
        StringTokenizer cardInfo = new StringTokenizer(card, ";");
        String color = cardInfo.nextToken();
        String cardType = cardInfo.nextToken();
        if (cardType.equalsIgnoreCase(Constants.FRAGMENT)) {
            Player player = activeMap.getPlayer(getUser().getId());
            if (color.equalsIgnoreCase(Constants.CULTURAL)) {
                player.setCrf(player.getCrf() + 1);
            } else if (color.equalsIgnoreCase(Constants.INDUSTRIAL)) {
                player.setIrf(player.getIrf() + 1);
            } else if (color.equalsIgnoreCase(Constants.HAZARDOUS)) {
                player.setHrf(player.getHrf() + 1);
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
        } else if (cardType.equalsIgnoreCase(Constants.TOKEN)) {
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
}
