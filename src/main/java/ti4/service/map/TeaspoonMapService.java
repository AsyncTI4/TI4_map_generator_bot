package ti4.service.map;

import lombok.experimental.UtilityClass;
import ti4.helpers.AliasHandler;
import ti4.map.Game;
import ti4.map.Tile;

@UtilityClass
public class TeaspoonMapService {

    public static void addTspmapHyperlanes(Game game) {
        //ring 1
        game.setTile(new Tile(AliasHandler.resolveTile("87a5"), "102"));
        game.setTile(new Tile(AliasHandler.resolveTile("87a0"), "103"));
        game.setTile(new Tile(AliasHandler.resolveTile("87a2"), "105"));
        game.setTile(new Tile(AliasHandler.resolveTile("87a3"), "106"));

        //ring 2
        game.setTile(new Tile(AliasHandler.resolveTile("hl_horizon_3"), "204"));
        game.setTile(new Tile(AliasHandler.resolveTile("hl_horizon_0"), "210"));

        //ring 3
        game.setTile(new Tile(AliasHandler.resolveTile("87b3"), "309"));

        //ring 4
        game.setTile(new Tile(AliasHandler.resolveTile("84b0"), "407"));
        game.setTile(new Tile(AliasHandler.resolveTile("83a2"), "411"));
        game.setTile(new Tile(AliasHandler.resolveTile("83a2"), "415"));
        game.setTile(new Tile(AliasHandler.resolveTile("84b0"), "419"));

        //ring 5, corners
        game.setTile(new Tile(AliasHandler.resolveTile("89a0"), "505"));
        game.setTile(new Tile(AliasHandler.resolveTile("89a0"), "512"));
        game.setTile(new Tile(AliasHandler.resolveTile("89a0"), "520"));
        game.setTile(new Tile(AliasHandler.resolveTile("89a0"), "527"));
    }

    public static void addTspmapEdgeAdjacencies(Game game) {
        game.addAdjacentTileOverride("505", 0, "409");
        game.addAdjacentTileOverride("505", 2, "422");

        game.addAdjacentTileOverride("405", 1, "422");
        game.addAdjacentTileOverride("405", 2, "316");
        game.addAdjacentTileOverride("405", 3, "420");

        game.addAdjacentTileOverride("304", 2, "420");

        game.addAdjacentTileOverride("305", 1, "420");

        game.addAdjacentTileOverride("306", 1, "419");
        game.addAdjacentTileOverride("306", 2, "418");

        game.addAdjacentTileOverride("307", 1, "418");

        game.addAdjacentTileOverride("409", 0, "418");
        game.addAdjacentTileOverride("409", 1, "313");
        game.addAdjacentTileOverride("409", 2, "416");

        game.addAdjacentTileOverride("410", 3, "404");

        game.addAdjacentTileOverride("411", 3, "303");

        game.addAdjacentTileOverride("309", 3, "302");

        game.addAdjacentTileOverride("310", 2, "302");
        game.addAdjacentTileOverride("310", 3, "201");
        game.addAdjacentTileOverride("310", 4, "318");

        game.addAdjacentTileOverride("311", 3, "318");

        game.addAdjacentTileOverride("415", 3, "317");

        game.addAdjacentTileOverride("416", 3, "422");
    }
}
