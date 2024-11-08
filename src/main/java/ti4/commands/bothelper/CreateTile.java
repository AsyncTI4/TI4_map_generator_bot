package ti4.commands.bothelper;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import ti4.generator.TileHelper;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.helpers.Storage;
import ti4.message.BotLogger;
import ti4.message.MessageHelper;
import ti4.model.ShipPositionModel;
import ti4.model.Source;
import ti4.model.TileModel;
import ti4.model.WormholeModel;

public class CreateTile extends BothelperSubcommandData {
    public CreateTile() {
        super(Constants.CREATE_TILE, "Permanently creates a new tile that can be used in future games.");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_ID, "The reference ID for the tile, generally the name in all lowercase without spaces").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "The tile's display name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_ALIASES, "A comma-separated list of any aliases you want to set for the tile.").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_IMAGE, "The name of the tile's image file. Someone with access will need to add this to the bot").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_PLANET_IDS, "A comma-separated list of the IDs of the planets that are in the tile. Make sure to make the planets").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_TYPE, "The tile's layout type. If you don't know what this is, ask.").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_WORMHOLES, "Comma-separated list of what wormholes are in the tile. Supports all greek letters through omega.").setAutoComplete(true));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_ASTEROID_FIELD, "Has an Asteroid Field?"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_SUPERNOVA, "Has a Supernova?"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_NEBULA, "Has a Nebula?"));
        addOptions(new OptionData(OptionType.BOOLEAN, Constants.IS_GRAVITY_RIFT, "Has a Gravity Rift?"));
        addOptions(new OptionData(OptionType.STRING, Constants.SOURCE, "The source of the planet - generally the ID of the expansion or module it comes from").setAutoComplete(true));
        //addOptions(new OptionData(OptionType.STRING, Constants.TILE_TOKEN_LOCATIONS, "The location of space tokens in the tile. Use only to override").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        MessageHelper.sendMessageToEventChannel(event, "Creating tile " + event.getOption(Constants.TILE_NAME).getAsString());
        String tileID = event.getOption(Constants.TILE_ID).getAsString().toLowerCase();
        TileModel tile = null;
        try {
            tile = createNewTile(
                    tileID,
                    event.getOption(Constants.TILE_NAME).getAsString(),
                    event.getOption(Constants.TILE_ALIASES).getAsString(),
                    event.getOption(Constants.TILE_IMAGE).getAsString(),
                    event.getOption(Constants.TILE_PLANET_IDS).getAsString().toLowerCase(),
                    event.getOption(Constants.TILE_TYPE).getAsString(),
                    event.getOption(Constants.TILE_WORMHOLES, "", OptionMapping::getAsString),
                    event.getOption(Constants.IS_ASTEROID_FIELD, false, OptionMapping::getAsBoolean),
                    event.getOption(Constants.IS_SUPERNOVA, false, OptionMapping::getAsBoolean),
                    event.getOption(Constants.IS_NEBULA, false, OptionMapping::getAsBoolean),
                    event.getOption(Constants.IS_GRAVITY_RIFT, false, OptionMapping::getAsBoolean),
                    event.getOption(Constants.SOURCE, null, OptionMapping::getAsString)
            );
        } catch (Exception e) {
            MessageHelper.sendMessageToEventChannel(event, "Something went wrong creating the tile: " + tileID);
            BotLogger.log("Something went wrong creating the tile: " + tileID, e);                    
        }
        if (Optional.ofNullable(tile).isPresent()) {
            try {
                exportTileModelToJson(tile);
            } catch (Exception e) {
                MessageHelper.sendMessageToEventChannel(event, "Something went wrong creating the tile: " + tileID);
                BotLogger.log("Something went wrong exporting the tile to json: " + tileID, e);                        
            }
            try {
                TileHelper.addNewTileToList(tile);
                AliasHandler.addNewTileAliases(tile);
            } catch (Exception e) {
                MessageHelper.sendMessageToEventChannel(event, "Something went wrong creating the tile: " + tileID);
                BotLogger.log("Something went wrong adding the tile to the active tiles list: " + tileID, e);
            }
        }
        String message = "Created new tile! Please check and make sure everything generated properly. This is the model:\n" +
                "```json\n" + TileHelper.getTileById(event.getOption(Constants.TILE_ID).getAsString()) + "\n```";
        MessageHelper.sendMessageToEventChannel(event, message);
    }

    private static TileModel createNewTile(String id,
                                           String name,
                                           String aliases,
                                           String image,
                                           String planetIds,
                                           String type,
                                           String wormholes,
                                           boolean isAsteroidField,
                                           boolean isSupernova,
                                           boolean isNebula,
                                           boolean isGravityRift,
                                           String source) {
        ShipPositionModel shipPositionModel = new ShipPositionModel();

        TileModel tile = new TileModel();
        tile.setId(id.toLowerCase());
        tile.setName(name);
        tile.setAliases(getAliasListFromString(aliases));
        tile.setImagePath(image);
        tile.setPlanets(getPlanetListFromString(planetIds));
        tile.setShipPositionsType(shipPositionModel.getTypeFromString(type));
        if(!"".equals(wormholes))
            tile.setWormholes(getWormholesFromString(wormholes));
        tile.setIsAsteroidField(isAsteroidField);
        tile.setIsSupernova(isSupernova);
        tile.setIsNebula(isNebula);
        tile.setIsGravityRift(isGravityRift);
        if (source != null) tile.setSource(Source.ComponentSource.valueOf(source));
        return tile;
    }

    private static List<String> getAliasListFromString(String aliases) {
        return Stream.of(aliases.replace(" ", "").toLowerCase().split(",")).toList();
    }

    private static List<String> getPlanetListFromString(String planets) {
        return Stream.of(planets.replace(" ", "").toLowerCase().split(",")).toList();
    }

    private static Set<WormholeModel.Wormhole> getWormholesFromString(String wormholeString) {
        WormholeModel wormholeModel = new WormholeModel();
        return Stream.of(wormholeString.replace(" ", "").toLowerCase().split(","))
                .filter(Objects::nonNull)
                .filter(s -> !s.isBlank())
                .map(wormholeModel::getWormholeFromString)
                .collect(Collectors.toSet());
    }

    private static void exportTileModelToJson(TileModel tileModel) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            mapper.writeValue(new File(Storage.getResourcePath() + File.separator + "systems" + File.separator+tileModel.getId()+".json"),tileModel);
            mapper.writeValue(new File(Storage.getStoragePath() + File.separator + "systems" + File.separator + tileModel.getId()+".json"),tileModel);
        } catch (IOException e) {
            BotLogger.log("Something went wrong creating new tile JSON!", e);
        }
    }
}
