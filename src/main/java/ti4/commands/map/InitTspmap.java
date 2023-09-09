package ti4.commands.map;

import java.util.HashMap;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Map;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class InitTspmap extends MapSubcommandData {
    public InitTspmap() {
        super(Constants.INIT_TSPMAP, "Initialize the map to have the hyperlanes and edge adjacencies of Tispoon's endless map layout");
        addOption(OptionType.STRING, Constants.CONFIRM, "Type 'YES' to confirm. This command can erase tiles", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Map activeMap = getActiveMap();
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        initializeMap(activeMap);
        addTspmapHyperlanes(activeMap);
        addTspmapEdgeAdjacencies(activeMap);
    }

    public void initializeMap(Map activeMap) {
        activeMap.clearAdjacentTileOverrides();
    }

    public void addTspmapHyperlanes(Map activeMap) {
        //ring 1
        activeMap.setTile(new Tile(AliasHandler.resolveTile("87a5"), "102"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("87a0"), "103"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("87a2"), "105"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("87a3"), "106"));

        //ring 2
        activeMap.setTile(new Tile(AliasHandler.resolveTile("HL_HORIZON_3"), "204"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("HL_HORIZON_0"), "210"));

        //ring 3
        activeMap.setTile(new Tile(AliasHandler.resolveTile("87B3"), "309"));

        //ring 4
        activeMap.setTile(new Tile(AliasHandler.resolveTile("84B0"), "407"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("83A2"), "411"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("83A2"), "415"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("84B0"), "419"));

        //ring 5, corners
        activeMap.setTile(new Tile(AliasHandler.resolveTile("89A0"), "505"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("89A0"), "512"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("89A0"), "520"));
        activeMap.setTile(new Tile(AliasHandler.resolveTile("89A0"), "527"));
    }

    public void addTspmapEdgeAdjacencies(Map activeMap) {
        activeMap.addAdjacentTileOverride("505", 0, "409");
        activeMap.addAdjacentTileOverride("505", 2, "422");

        activeMap.addAdjacentTileOverride("405", 1, "422");
        activeMap.addAdjacentTileOverride("405", 2, "316");
        activeMap.addAdjacentTileOverride("405", 3, "420");

        activeMap.addAdjacentTileOverride("304", 2, "420");

        activeMap.addAdjacentTileOverride("305", 1, "420");

        activeMap.addAdjacentTileOverride("306", 1, "419");
        activeMap.addAdjacentTileOverride("306", 2, "418");

        activeMap.addAdjacentTileOverride("307", 1, "418");

        activeMap.addAdjacentTileOverride("409", 0, "418");
        activeMap.addAdjacentTileOverride("409", 1, "313");
        activeMap.addAdjacentTileOverride("409", 2, "416");

        activeMap.addAdjacentTileOverride("410", 3, "404");

        activeMap.addAdjacentTileOverride("411", 3, "303");

        activeMap.addAdjacentTileOverride("309", 3, "302");

        activeMap.addAdjacentTileOverride("310", 2, "302");
        activeMap.addAdjacentTileOverride("310", 3, "201");
        activeMap.addAdjacentTileOverride("310", 4, "318");

        activeMap.addAdjacentTileOverride("311", 3, "318");

        activeMap.addAdjacentTileOverride("415", 3, "317");

        activeMap.addAdjacentTileOverride("416", 3, "422");
    }
}
