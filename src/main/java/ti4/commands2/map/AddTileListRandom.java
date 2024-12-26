package ti4.commands2.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.commands2.GameStateSubcommand;
import ti4.commands2.map.AddTileRandom.RandomOption;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.message.MessageHelper;
import ti4.model.Source.ComponentSource;
import ti4.model.TileModel;
import ti4.service.map.AddTileListService;

public class AddTileListRandom extends GameStateSubcommand {

    public AddTileListRandom() {
        super(Constants.ADD_TILE_LIST_RANDOM, "Add tile list to generate map", true, false);
        addOption(OptionType.STRING, Constants.TILE_LIST, "Tile list (supports random options from /map add_tile_random)", true);
        addOption(OptionType.BOOLEAN, Constants.INCLUDE_ERONOUS_TILES, "Include Eronous tiles");
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game game = getGame();
        String tileList = event.getOption(Constants.TILE_LIST).getAsString().toUpperCase();
        tileList = tileList.replace(",", " ");

        Set<ComponentSource> sources = AddTileRandom.getSources(event, game);

        StringTokenizer tileListTokenizer = new StringTokenizer(tileList, " ");
        List<String> tilesToAdd = new ArrayList<>();

        //Replace each instance of RandomOption with a randomized tile
        while(tileListTokenizer.hasMoreTokens()) {
            String tileToken = tileListTokenizer.nextToken().trim();
            boolean isCenter = false;
            if (tileToken.contains("{")) {
                isCenter = true;
                tileToken = tileToken.replace("{", "").replace("}", "").trim();
            }
            
            if (RandomOption.isValid(tileToken)) {
                //Ignoring existing tiles from the map as those will be cleared by addTileListToMap
                List<TileModel> availableTiles = AddTileRandom.availableTiles(sources, RandomOption.valueOf(tileToken), new HashSet<>(), tilesToAdd);
                if (availableTiles.isEmpty()) {
                    MessageHelper.replyToMessage(event, "Not enough " + tileToken + " tiles to draw from.");
                    return;
                }

                Collections.shuffle(availableTiles);
                tileToken = availableTiles.getFirst().getId();
            } 
            tilesToAdd.add((isCenter ? "{" : "") + tileToken + (isCenter ? "}" : ""));
        }

        AddTileListService.addTileListToMap(getGame(), String.join(", ", tilesToAdd), event);
    }
}
