package ti4.commands.bothelper;

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
import ti4.model.*;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class CreateTile extends BothelperSubcommandData {
    public CreateTile() {
        super(Constants.CREATE_TILE, "Permanently creates a new tile that can be used in future games.");
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_ID, "The reference ID for the tile, generally the name in all lowercase without spaces").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_NAME, "The tile's display name").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_ALIASES, "A comma-separated list of any aliases you want to set for the tile.").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_IMAGE, "The name of the tile's image file. Someone with access will need to add this to the bot").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_PLANET_IDS, "A comma-separated list of the IDs of the planets that are in the tile. Make sure to make the planets").setRequired(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_TYPE, "The tile's layout type. If you don't know what this is, ask.").setRequired(true).setAutoComplete(true));
        addOptions(new OptionData(OptionType.STRING, Constants.TILE_WORMHOLES, "Comma-separated list of what wormholes are in the tile. Supports all greek letters through omega.").setRequired(false).setAutoComplete(true));
        //addOptions(new OptionData(OptionType.STRING, Constants.TILE_TOKEN_LOCATIONS, "The location of space tokens in the tile. Use only to override").setRequired(false));
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        sendMessage("Creating tile " + event.getOption(Constants.TILE_NAME).getAsString());
        OptionMapping whOption = event.getOption(Constants.TILE_WORMHOLES);
        String whString = Optional.ofNullable(whOption).isPresent() ? whOption.getAsString() : "";
        TileModel tile = null;
        try {
            tile = createNewTile(
                    event.getOption(Constants.TILE_ID).getAsString(),
                    event.getOption(Constants.TILE_NAME).getAsString(),
                    event.getOption(Constants.TILE_ALIASES).getAsString(),
                    event.getOption(Constants.TILE_IMAGE).getAsString(),
                    event.getOption(Constants.TILE_PLANET_IDS).getAsString(),
                    event.getOption(Constants.TILE_TYPE).getAsString(),
                    whString
            );
        } catch (Exception e) {
            BotLogger.log("Something went wrong creating the tile! "
                    + e.getMessage() + "\n" + e.getStackTrace());
        }
        if(Optional.ofNullable(tile).isPresent()) {
            try {
                exportTileModelToJson(tile);
            } catch (Exception e) {
                BotLogger.log("Something went wrong exporting the tile to json! "
                        + e.getMessage() + "\n" +e.getStackTrace());
            }
            try {
                TileHelper.addNewTileToList(tile);
                AliasHandler.addNewTileAliases(tile);
            } catch (Exception e) {
                BotLogger.log("Something went wrong adding the tile to the active tiles list! " +
                        e.getMessage() + "\n" + e.getStackTrace());
            }
        }
        sendMessage("Created new tile! Please check and make sure everything generated properly. This is the model:\n" +
                "```json\n" + TileHelper.getAllTiles().get(event.getOption(Constants.TILE_ID).getAsString()) + "\n```");
    }

    private static TileModel createNewTile(String id,
                                           String name,
                                           String aliases,
                                           String image,
                                           String planetIds,
                                           String type,
                                           String wormholes) {
        ShipPositionModel shipPositionModel = new ShipPositionModel();

        TileModel tile = new TileModel();
        tile.setId(id.toLowerCase());
        tile.setName(name);
        tile.setAliases(getAliasListFromString(aliases));
        tile.setImagePath(image);
        tile.setPlanetIds(getPlanetListFromString(planetIds));
        tile.setShipPositionsType(shipPositionModel.getTypeFromString(type));
        tile.setSpaceTokenLocations(tile.getShipPositionsType().getSpaceTokenLayout());
        tile.setWormholes(getWormholesFromString(wormholes));

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
        return Stream.of(wormholeString
                .replace(" ", "").
                toLowerCase().split(","))
                .filter(Objects::nonNull)
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
