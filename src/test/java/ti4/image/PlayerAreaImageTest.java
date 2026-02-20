package ti4.image;

import java.util.HashSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import ti4.helpers.Constants;
import ti4.image.helpers.ImageTestHelper.TestMode;
import ti4.image.helpers.PlayerAreaImageTestHelper;
import ti4.map.Game;
import ti4.map.Player;
import ti4.model.FactionModel;
import ti4.testUtils.BaseTi4Test;

/**
 * <h1> HOW TO USE THIS TEST FILE: </h1>
 * <p> 1. Change TestMode to "SaveStatic"
 * <p> 2. Run all of the tests
 * <p> 3. Change TestMode back to "Compare"
 * <p><p> For advanced tips & complaints, ping Jazzxhands in discord
 */
@org.junit.jupiter.api.Disabled
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PlayerAreaImageTest extends BaseTi4Test {

    public static Game testGame;
    private static Player testPlayer1;
    private static Player testPlayer2;
    private static Player testPlayer3;

    public static final TestMode testMode = TestMode.SaveStatic;

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
        FactionModel cabal = Mapper.getFaction("firmament");
        testPlayer2.setFaction(testGame, "firmament");
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
    void generateGenericPlayerArea() {
        PlayerAreaImageTestHelper.runTest(testPlayer1, "player1.png");
    }

    @Test
    @Order(2)
    void generateFirmamentPlayerArea() {
        testPlayer2.getPlotCardsRaw().clear();
        testPlayer2.setPlotCard("enervate", 22);
        testPlayer2.setPlotCard("siphon", 23);
        testPlayer2.setPlotCard("seethe", 24);
        testPlayer2.setPlotCard("assail", 25);
        testPlayer2.setPlotCard("mutated_extract", 26);

        testPlayer2.getPlotCards().keySet().forEach(id -> testPlayer2.setPlotCardFaction(id, "arborec"));
        testPlayer2.setPlotCardFaction("mutated_extract", "bastion");
        PlayerAreaImageTestHelper.runTest(testPlayer2, "player2.png");
    }

    @Test
    @Order(3)
    void generateTripleMirageTestImage() {
        PlayerAreaImageTestHelper.runTest(testPlayer3, "player3.png");
    }
}
