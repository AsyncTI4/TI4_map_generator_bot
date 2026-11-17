package ti4.service.tech;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.game.Game;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Constants;
import ti4.helpers.Units;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

/**
 * Sol's faction technologies (Spec Ops II and Advanced Carrier II) are both unit upgrades, so they move progress
 * on Develop Weaponry ("own 2 unit upgrade technologies") and Revolutionize Warfare ("own 3").
 */
class EntropicScarServiceTest extends BaseTi4Test {

    private static final String SCAR_TOKEN = "token_entropicscar_async.png";

    private Game game;
    private Player sol;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.setName("entropic-scar-test");
        game.newGameSetup();
        sol = game.addPlayer("sol-id", "sol");
        sol.setFaction(game, "sol");
        sol.setColor("blue");
        sol.addOwnedUnitByID("carrier");
        sol.addFactionTech("so2");
        sol.addFactionTech("ac2");
        sol.setStrategicCC(2);
    }

    @Test
    void offersUnownedFactionTechnologies() {
        assertThat(EntropicScarService.getAvailableTechnologies(game, sol)).containsExactlyInAnyOrder("so2", "ac2");

        sol.addTech("so2");

        assertThat(EntropicScarService.getAvailableTechnologies(game, sol)).containsExactly("ac2");
    }

    @Test
    void scarTechnologyThatCompletesTechnologyObjectiveKeepsPlayerInScoring() {
        // Destroyer II plus one Scar unit upgrade reaches the 2 unit upgrades Develop Weaponry needs.
        revealStageOne("develop");
        sol.addTech("dd2");
        addScarWithCarrier("101", "37");

        assertThat(EntropicScarService.couldScorePublicObjectiveAfterScar(game, sol))
                .isTrue();
        assertThat(sol.getTechs())
                .as("the hypothetical technologies are removed again")
                .containsExactly("dd2");
    }

    @Test
    void objectiveOutOfReachOfScarTechnologiesDoesNotKeepPlayerInScoring() {
        // One Scar gives one technology, and Sol owns no unit upgrades, so Develop Weaponry stays one short.
        revealStageOne("develop");
        addScarWithCarrier("101", "37");

        assertThat(EntropicScarService.couldScorePublicObjectiveAfterScar(game, sol))
                .isFalse();
    }

    @Test
    void shipsInTwoScarsCanGainBothTechnologies() {
        revealStageOne("develop");
        addScarWithCarrier("101", "37");
        addScarWithCarrier("102", "38");

        assertThat(EntropicScarService.couldScorePublicObjectiveAfterScar(game, sol))
                .isTrue();
    }

    @Test
    void secondScarTechnologyNeedsASecondToken() {
        revealStageOne("develop");
        addScarWithCarrier("101", "37");
        addScarWithCarrier("102", "38");
        sol.setStrategicCC(1);

        assertThat(EntropicScarService.couldScorePublicObjectiveAfterScar(game, sol))
                .isFalse();
    }

    @Test
    void objectiveThatIgnoresTechnologiesDoesNotKeepPlayerInScoring() {
        // Before the narrowing, any Scar holder with a payable technology escaped the automatic no-score.
        revealStageOne("corner");
        sol.addTech("dd2");
        addScarWithCarrier("101", "37");

        assertThat(EntropicScarService.couldScorePublicObjectiveAfterScar(game, sol))
                .isFalse();
    }

    @Test
    void noPaymentSourceMeansNoScarTechnology() {
        revealStageOne("develop");
        sol.addTech("dd2");
        addScarWithCarrier("101", "37");
        sol.setStrategicCC(0);

        assertThat(EntropicScarService.couldScorePublicObjectiveAfterScar(game, sol))
                .isFalse();
    }

    @Test
    void shipsOutsideScarsGainNothing() {
        revealStageOne("develop");
        sol.addTech("dd2");
        Tile tile = new Tile("37", "101");
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, sol.getColor()), 1);
        game.setTile(tile);

        assertThat(EntropicScarService.couldScorePublicObjectiveAfterScar(game, sol))
                .isFalse();
    }

    private void revealStageOne(String objective) {
        assertThat(game.revealSpecificStage1(objective)).isTrue();
    }

    private void addScarWithCarrier(String position, String tileId) {
        Tile tile = new Tile(tileId, position);
        tile.addToken(SCAR_TOKEN, Constants.SPACE);
        tile.addUnit(Constants.SPACE, Units.getUnitKey(UnitType.Carrier, sol.getColor()), 1);
        game.setTile(tile);
    }
}
