package ti4.image;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitKey;
import ti4.helpers.Units.UnitType;
import ti4.image.helpers.ImageTestHelper.TestMode;
import ti4.image.helpers.TileImageTestHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.map.Tile;
import ti4.model.FactionModel;
import ti4.service.map.TokenPlanetService;
import ti4.testUtils.BaseTi4Test;

/**
 * <h1> HOW TO USE THIS TEST FILE: </h1>
 * <p> 1. Change TestMode to "SaveStatic"
 * <p> 2. Run all of the tests
 * <p> 3. Change TestMode back to "Compare"
 * <p><p> For advanced tips & complaints, ping Jazzxhands in discord
 */
@org.junit.jupiter.api.Disabled
public class TileImageTest extends BaseTi4Test {

    public static Game testGame;
    private static Player testPlayer1;
    private static Player testPlayer2;
    private static Player testPlayer3;

    public static final TestMode testMode = TestMode.SaveTemp;

    @AfterAll
    static void readyForProduction() {
        Assertions.assertEquals(TestMode.Compare, testMode);
    }

    @BeforeAll
    static void setupTestGame() {
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

        testPlayer3 = testGame.addPlayer(Constants.chassitId, "Chassit");
        FactionModel bastion = Mapper.getFaction("bastion");
        testPlayer3.setFaction(testGame, "bastion");
        testPlayer3.setColor("copper");
        testPlayer3.setUnitsOwned(new HashSet<>(bastion.getUnits()));
    }

    @Test
    @Order(1)
    void generateDevilsTestImage() {
        Tile devils = new Tile("75", "000");
        testGame.setTile(devils);
        TileImageTestHelper.addUnitsToUnitHolder(
                testPlayer1, devils, "space", UnitType.Dreadnought, UnitType.Flagship, UnitType.Destroyer);
        TileImageTestHelper.addUnitsAndControlToPlanet(
                testPlayer1,
                devils,
                "loki",
                UnitType.Infantry,
                UnitType.Mech,
                UnitType.Spacedock,
                UnitType.Pds,
                UnitType.Pds);
        TileImageTestHelper.addUnitsAndControlToPlanet(
                testPlayer1,
                devils,
                "abaddon",
                UnitType.Infantry,
                UnitType.Mech,
                UnitType.Spacedock,
                UnitType.Pds,
                UnitType.Pds);
        TileImageTestHelper.addUnitsAndControlToPlanet(
                testPlayer1,
                devils,
                "ashtroth",
                UnitType.Infantry,
                UnitType.Mech,
                UnitType.Spacedock,
                UnitType.Pds,
                UnitType.Pds);
        TileImageTestHelper.addTokensToHolder(
                devils,
                "loki",
                "attachment_tombofemphidia.png",
                "attachment_paradiseworld.png",
                "attachment_nanoforge.png",
                "attachment_dysonsphere.png");

        TileImageTestHelper.runTest(devils, "Devils.png");
    }

    @Test
    @Order(2)
    void generateMirageTestImage() {
        Tile emptyAlpha = new Tile("40", "101");
        testGame.setTile(emptyAlpha);

        TileImageTestHelper.addTokensToHolder(emptyAlpha, "space", "token_mirage.png");
        TokenPlanetService.addTokenPlanetToTile(testGame, emptyAlpha, "mirage");
        TileImageTestHelper.addUnitsAndControlToPlanet(
                testPlayer1, emptyAlpha, "mirage", UnitType.Infantry, UnitType.Infantry, UnitType.Infantry);

        TileImageTestHelper.runTest(emptyAlpha, "Mirage.png");
    }

    @Test
    @Order(3)
    void generateTripleMirageTestImage() {
        Tile rigels = new Tile("76", "102");
        testGame.setTile(rigels);

        TileImageTestHelper.addTokensToHolder(rigels, "space", "token_cradle.png");
        TokenPlanetService.addTokenPlanetToTile(testGame, rigels, "cradle");
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, rigels, "rigeli", UnitType.Infantry);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, rigels, "rigelii", UnitType.Infantry);
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer1, rigels, "cradle", UnitType.Infantry);

        TileImageTestHelper.runTest(rigels, "TripleMirage.png");
    }

    @Test
    @Order(4)
    void generateCabalDockTestImage() {
        Tile acheron = new Tile("54", "301");
        testGame.setTile(acheron);

        TileImageTestHelper.addUnitsToUnitHolder(testPlayer2, acheron, "space", UnitType.Destroyer);
        TileImageTestHelper.addUnitsAndControlToPlanet(
                testPlayer2, acheron, "acheron", UnitType.Mech, UnitType.Infantry, UnitType.Spacedock);

        TileImageTestHelper.runTest(acheron, "Acheron.png");
    }

    @Test
    @Order(5)
    void generateGalvanizeTestImage() {
        Tile thibah = new Tile("21", "207");
        testGame.setTile(thibah);

        UnitKey dn = Units.getUnitKey(UnitType.Dreadnought, testPlayer3.getColor());
        UnitKey inf = Units.getUnitKey(UnitType.Infantry, testPlayer3.getColor());
        UnitKey mf = Units.getUnitKey(UnitType.Mech, testPlayer3.getColor());
        UnitKey ff = Units.getUnitKey(UnitType.Fighter, testPlayer3.getColor());
        // none, dmg, galv, both
        thibah.getSpaceUnitHolder().addUnitsWithStates(dn, List.of(1, 1, 1, 1));
        thibah.getSpaceUnitHolder().addUnitsWithStates(ff, List.of(5, 0, 1, 0));
        TileImageTestHelper.addUnitsAndControlToPlanet(testPlayer3, thibah, "thibah", UnitType.Spacedock);
        thibah.getUnitHolderFromPlanet("thibah").addUnitsWithStates(inf, List.of(1, 0, 3, 0));
        thibah.getUnitHolderFromPlanet("thibah").addUnitsWithStates(mf, List.of(1, 0, 1, 1));

        TileImageTestHelper.runTest(thibah, "Thibah.png");
    }
}
