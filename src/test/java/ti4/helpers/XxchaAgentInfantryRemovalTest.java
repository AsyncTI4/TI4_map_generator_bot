package ti4.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ti4.ai.AiTestGame;
import ti4.game.Player;
import ti4.game.Tile;
import ti4.helpers.Units.UnitType;
import ti4.testUtils.BaseTi4Test;

// Ggrocuto Rinn: if the readied planet is in a system adjacent to a planet the agent's owner controls, the owner may
// remove 1 infantry from it. Having ships next door is not the condition.
class XxchaAgentInfantryRemovalTest extends BaseTi4Test {

    private AiTestGame test;
    private Player xxcha;
    private Tile next;

    // Xxcha holds Archon Ren and Archon Tau at 301; Lodor, held by Sol, sits in the system beside it.
    @BeforeEach
    void setUp() {
        test = new AiTestGame();
        xxcha = test.addSeat(AiTestGame.SECOND_AI_ID, "xxcha", "green");
        test.place("14", AiTestGame.HOME);
        xxcha.addPlanet("archonren");
        xxcha.addPlanet("archontau");
        next = test.place("26", AiTestGame.neighbourOf(AiTestGame.HOME));
        test.sol.addPlanet("lodor");
    }

    @Test
    void aPlanetBesideAControlledPlanetQualifies() {
        assertThat(ButtonHelperHeroes.isNextToPlanetControlledBy(test.game, xxcha, next))
                .isTrue();
    }

    @Test
    void shipsNextDoorAreNotEnough() {
        xxcha.removePlanet("archonren");
        xxcha.removePlanet("archontau");
        test.units(test.game.getTileByPosition(AiTestGame.HOME), "space", xxcha, UnitType.Destroyer, 1);

        assertThat(ButtonHelperHeroes.isNextToPlanetControlledBy(test.game, xxcha, next))
                .isFalse();
    }

    // The planets of the system itself do not count: it has to be an adjacent system.
    @Test
    void aControlledPlanetInTheSameSystemDoesNotCount() {
        xxcha.removePlanet("archonren");
        xxcha.removePlanet("archontau");
        xxcha.addPlanet("lodor");
        test.sol.removePlanet("lodor");

        assertThat(ButtonHelperHeroes.isNextToPlanetControlledBy(test.game, xxcha, next))
                .isFalse();
    }

    @Test
    void aDistantPlanetDoesNotQualify() {
        Tile far = test.place("34", "310");

        assertThat(ButtonHelperHeroes.isNextToPlanetControlledBy(test.game, xxcha, far))
                .isFalse();
    }
}
