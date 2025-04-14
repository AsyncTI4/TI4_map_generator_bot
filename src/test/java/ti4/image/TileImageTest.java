package ti4.image;

import java.util.HashSet;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;

import ti4.helpers.Constants;
import ti4.helpers.Helper;
import ti4.helpers.Units.UnitType;
import ti4.image.helpers.ImageTestHelper.TestMode;
import ti4.image.helpers.TileImageTestHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.model.FactionModel;
import ti4.testUtils.BaseTi4Test;

/**
 * <h1> HOW TO USE THIS TEST FILE: </h1> 
 * <p> 1. Change TestMode to "SaveStatic"
 * <p> 2. Run all of the tests
 * <p> 3. Change TestMode back to "Compare"
 * <p><p> For advanced tips & complaints, ping Jazzxhands in discord
 */

//@org.junit.jupiter.api.Disabled
public class TileImageTest extends BaseTi4Test {

    public static Game testGame = null;
    public static Player testPlayer1 = null;
    public static Player testPlayer2 = null;

    public static TestMode testMode = TestMode.Compare;

    @AfterAll
    public static void readyForProduction() {
        Assertions.assertEquals(testMode, TestMode.Compare);
    }

    @BeforeAll
    private static void setupTestGame() {
        if (testGame != null) return;
        testGame = new Game();
        testGame.setName(" Test Tile Image Generation    ");
        testGame.setCcNPlasticLimit(false);

        testPlayer1 = testGame.addPlayer(Constants.jazzId, "Jazzxhands");
        FactionModel arbo = Mapper.getFaction("arborec");
        testPlayer1.setFaction(testGame, "arborec");
        testPlayer1.setColor("splitpurple");
        testPlayer1.setUnitsOwned(new HashSet<>(arbo.getUnits()));

        testPlayer2 = testGame.addPlayer(Constants.tspId, "HolyTispoon");
        FactionModel cabal = Mapper.getFaction("cabal");
        testPlayer2.setFaction(testGame, "cabal");
        testPlayer2.setColor("black");
        testPlayer2.setUnitsOwned(new HashSet<>(cabal.getUnits()));
        testPlayer2.setDecalSet("cb_96");
    }

    @Test
    @Order(1)
    public void generateDevilsTestImage() {
        Tile devils = new Tile("75", "000");
        testGame.setTile(devils);
        TileImageTestHelper.addUnitsToUnitHolder(testPlayer1, devils, "space", UnitType.Dreadnought, UnitType.Flagship, UnitType.Destroyer);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, devils, "loki", UnitType.Infantry, UnitType.Mech, UnitType.Spacedock, UnitType.Pds, UnitType.Pds);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, devils, "abaddon", UnitType.Infantry, UnitType.Mech, UnitType.Spacedock, UnitType.Pds, UnitType.Pds);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, devils, "ashtroth", UnitType.Infantry, UnitType.Mech, UnitType.Spacedock, UnitType.Pds, UnitType.Pds);
        TileImageTestHelper.addTokensToHolder(devils, "loki",
            "attachment_tombofemphidia.png",
            "attachment_paradiseworld.png",
            "attachment_nanoforge.png",
            "attachment_dysonsphere.png");

        TileImageTestHelper.runTest(devils, "Devils.png");
    }

    @Test
    @Order(2)
    public void generateMirageTestImage() {
        Tile emptyAlpha = new Tile("40", "101");
        testGame.setTile(emptyAlpha);

        TileImageTestHelper.addTokensToHolder(emptyAlpha, "space", "token_mirage.png");
        Helper.addMirageToTile(emptyAlpha);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, emptyAlpha, "mirage", UnitType.Infantry, UnitType.Infantry, UnitType.Infantry);

        TileImageTestHelper.runTest(emptyAlpha, "Mirage.png");
    }

    @Test
    @Order(3)
    public void generateTripleMirageTestImage() {
        Tile rigels = new Tile("76", "102");
        testGame.setTile(rigels);

        TileImageTestHelper.addTokensToHolder(rigels, "space", "token_mirage.png");
        Helper.addMirageToTile(rigels);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, rigels, "rigeli", UnitType.Infantry);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, rigels, "rigelii", UnitType.Infantry);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, rigels, "mirage", UnitType.Infantry);

        TileImageTestHelper.runTest(rigels, "TripleMirage.png");
    }

    @Test
    @Order(4)
    public void generateCabalDockTestImage() {
        Tile acheron = new Tile("54", "301");
        testGame.setTile(acheron);

        TileImageTestHelper.addUnitsToUnitHolder(testPlayer2, acheron, "space", UnitType.Destroyer);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer2, acheron, "acheron", UnitType.Mech, UnitType.Infantry, UnitType.Spacedock);

        TileImageTestHelper.runTest(acheron, "Acheron.png");
    }
}