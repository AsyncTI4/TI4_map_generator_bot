package ti4.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashSet;
import org.junit.jupiter.api.Test;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.Mapper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.model.FactionModel;
import ti4.testUtils.BaseTi4Test;

class CheckDistanceHelperTest extends BaseTi4Test {

    @Test
    void testMentakCruiserBreakthroughAllowsMovingThroughShips() {
        Game game = new Game();
        game.newGameSetup();
        game.setActiveSystem("102");

        Player mentak = createPlayer(game, "mentak", "blue", true);
        Player opponent = createPlayer(game, "arborec", "red", false);

        Tile start = new Tile("101", "101");
        Tile blocked = new Tile("103", "103");
        Tile active = new Tile("102", "102");
        game.setTile(start);
        game.setTile(blocked);
        game.setTile(active);

        active.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Cruiser, mentak.getColorID()), 1);
        active.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, opponent.getColorID()), 1);
        blocked.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, opponent.getColorID()), 1);

        int distance = CheckDistanceHelper.getDistanceBetweenTwoTiles(game, mentak, "101", "102", true);

        assertEquals(2, distance);
    }

    private static Player createPlayer(Game game, String faction, String color, boolean enableMentakBreakthrough) {
        Player player = game.addPlayer(faction, faction);
        player.setFaction(game, faction);
        player.setColor(color);
        FactionModel model = Mapper.getFaction(faction);
        player.setUnitsOwned(new HashSet<>(model.getUnits()));
        if (enableMentakBreakthrough) {
            player.addBreakthrough("mentakbt");
            player.setBreakthroughUnlocked("mentakbt", true);
            player.addTech("cr2");
            player.addOwnedUnitByID("mentak_cruiser3");
        }
        return player;
    }
}
