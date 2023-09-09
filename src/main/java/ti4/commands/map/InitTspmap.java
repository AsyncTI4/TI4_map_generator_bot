package ti4.commands.map;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import ti4.helpers.AliasHandler;
import ti4.helpers.Constants;
import ti4.map.Game;
import ti4.map.Tile;
import ti4.message.MessageHelper;

public class InitTspmap extends MapSubcommandData {
    public InitTspmap() {
        super(Constants.INIT_TSPMAP, "Initialize the map to have the hyperlanes and edge adjacencies of Tispoon's endless map layout");
        addOption(OptionType.STRING, Constants.CONFIRM, "Type 'YES' to confirm. This command can erase tiles", true, false);
    }

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        Game activeGame = getActiveMap();
        OptionMapping option = event.getOption(Constants.CONFIRM);
        if (option == null || !"YES".equals(option.getAsString())) {
            MessageHelper.replyToMessage(event, "Must confirm with YES");
            return;
        }

        initializeMap(activeGame);
        addTspmapHyperlanes(activeGame);
        addTspmapEdgeAdjacencies(activeGame);
    }

    public void initializeMap(Game activeGame) {
        activeGame.clearAdjacentTileOverrides();
    }

    public void addTspmapHyperlanes(Game activeGame) {
        //ring 1
        activeGame.setTile(new Tile(AliasHandler.resolveTile("87a5"), "102"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("87a0"), "103"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("87a2"), "105"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("87a3"), "106"));

        //ring 2
        activeGame.setTile(new Tile(AliasHandler.resolveTile("hl_horizon_3"), "204"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("hl_horizon_0"), "210"));

        //ring 3
        activeGame.setTile(new Tile(AliasHandler.resolveTile("87b3"), "309"));

        //ring 4
        activeGame.setTile(new Tile(AliasHandler.resolveTile("84b0"), "407"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("83a2"), "411"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("83a2"), "415"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("84b0"), "419"));

        //ring 5, corners
        activeGame.setTile(new Tile(AliasHandler.resolveTile("89a0"), "505"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("89a0"), "512"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("89a0"), "520"));
        activeGame.setTile(new Tile(AliasHandler.resolveTile("89a0"), "527"));
    }

    public void addTspmapEdgeAdjacencies(Game activeGame) {
        activeGame.addAdjacentTileOverride("505", 0, "409");
        activeGame.addAdjacentTileOverride("505", 2, "422");

        activeGame.addAdjacentTileOverride("405", 1, "422");
        activeGame.addAdjacentTileOverride("405", 2, "316");
        activeGame.addAdjacentTileOverride("405", 3, "420");

        activeGame.addAdjacentTileOverride("304", 2, "420");

        activeGame.addAdjacentTileOverride("305", 1, "420");

        activeGame.addAdjacentTileOverride("306", 1, "419");
        activeGame.addAdjacentTileOverride("306", 2, "418");

        activeGame.addAdjacentTileOverride("307", 1, "418");

        activeGame.addAdjacentTileOverride("409", 0, "418");
        activeGame.addAdjacentTileOverride("409", 1, "313");
        activeGame.addAdjacentTileOverride("409", 2, "416");

        activeGame.addAdjacentTileOverride("410", 3, "404");

        activeGame.addAdjacentTileOverride("411", 3, "303");

        activeGame.addAdjacentTileOverride("309", 3, "302");

        activeGame.addAdjacentTileOverride("310", 2, "302");
        activeGame.addAdjacentTileOverride("310", 3, "201");
        activeGame.addAdjacentTileOverride("310", 4, "318");

        activeGame.addAdjacentTileOverride("311", 3, "318");

        activeGame.addAdjacentTileOverride("415", 3, "317");

        activeGame.addAdjacentTileOverride("416", 3, "422");
    }
}
